import cats.effect.{Effect, IO}
import fs2.StreamApp.ExitCode
import fs2.async.mutable.{Signal, Topic}
import fs2.{Scheduler, Sink, Stream, StreamApp, async}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

case class Event(value: String)

class EventService[F[_]](eventsTopic: Topic[F, Event],
                         interrupter: Signal[F, Boolean])(implicit F: Effect[F], S: Scheduler) {

  // Publishing events every one second until signaling interruption
  def startPublisher: Stream[F, Unit] =
    S.awakeEvery(1.second).flatMap { _ =>
      val event = Event(System.currentTimeMillis().toString)
      Stream.eval(eventsTopic.publish1(event))
    }.interruptWhen(interrupter)

  // Creating 3 subscribers in a different period of time and join them to run concurrently
  def startSubscribers: Stream[F, Unit] = {
    val s1: Stream[F, Event] = eventsTopic.subscribe(10)
    val s2: Stream[F, Event] = S.delay(eventsTopic.subscribe(10), 5.seconds)
    val s3: Stream[F, Event] = S.delay(eventsTopic.subscribe(10), 10.seconds)

    def sink(subscriberNumber: Int): Sink[F, Event] =
      _.evalMap(e => F.delay(println(s"Subscriber #$subscriberNumber processing event: $e")))

    Stream(s1.to(sink(1)), s2.to(sink(2)), s3.to(sink(3))).join(3)
  }

}

class PubSub[F[_]: Effect] extends StreamApp[F] {

  override def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, ExitCode] =
    Scheduler(corePoolSize = 4).flatMap { implicit S =>
      for {
        topic     <- Stream.eval(async.topic[F, Event](Event("")))
        signal    <- Stream.eval(async.signalOf[F, Boolean](false))
        service   = new EventService[F](topic, signal)
        exitCode  <- Stream(
                      S.delay(Stream.eval(signal.set(true)), 15.seconds),
                      service.startPublisher.concurrently(service.startSubscribers)
                    ).join(2).drain ++ Stream.emit(ExitCode.Success)
      } yield exitCode
    }

}



