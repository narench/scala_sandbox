import fs2._
import fs2.async.mutable.Topic
import fs2.StreamApp.ExitCode
import cats.implicits._
import cats.effect._

import scala.collection.mutable
import scala.concurrent.ExecutionContext



object TopicApp  extends StreamApp[IO] {
  
  def sharedTopicStream[F[_]: Effect](topicId: String)(implicit ec: ExecutionContext): Stream[F, Topic[F, String]] =
    Stream.eval(async.topic[F, String](s"Topic $topicId start"))

  def addPublisher[F[_]](topic: Topic[F, String], value: String): Stream[F, Unit] =
    Stream.emit(value).covary[F].repeat.to(topic.publish)

  def addSubscriber[F[_]](topic: Topic[F, String]): Stream[F, String] =
    topic
      .subscribe(10)
      .take(4)

  // a request that adds a publisher to the topic
  def requestAddPublisher[F[_]](value: String, topic: Topic[F, String]): Stream[F, Unit] =
    addPublisher(topic, value)

  // a request that adds a subscriber to the topic
  def requestAddSubscriber[F[_]](topic: Topic[F, String])(implicit F: Effect[F]): Stream[F, Unit] =
    addSubscriber(topic).to(Sink.showLinesStdOut)

  def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {
    import ExecutionContext.Implicits.global
    // we simulate  requests that work on a common topic.
    val sharedTopic = sharedTopicStream[IO]("sharedTopic")

    // sharedTopic is passed to your Services, which use it as necessary
    sharedTopic.flatMap { topic ⇒
      requestAddPublisher("publisher1", topic) concurrently
      requestAddPublisher("publisher2", topic) concurrently
      requestAddSubscriber(topic) concurrently
      requestAddSubscriber(topic)

    }.drain ++ Stream.emit(ExitCode.Success)
  }

}
