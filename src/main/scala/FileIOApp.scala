import fs2._
import fs2.io.file._
import fs2.async.mutable.Topic
import fs2.StreamApp.ExitCode

import cats._
import cats.implicits._
import cats.effect._
import java.nio.file.{Path, Paths, StandardOpenOption, WatchEvent}
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider

import fs2.internal.ThreadFactories
import java.net.{InetSocketAddress, InetAddress}


object FileIOApp extends StreamApp[IO] {

      import scala.concurrent.ExecutionContext.Implicits.global

  val out =  new InetSocketAddress(InetAddress.getByName(null), 7798)
  val metrics =  new InetSocketAddress(InetAddress.getByName(null), 7797)

   implicit val tcpACG: AsynchronousChannelGroup = AsynchronousChannelProvider
    .provider()
     .openAsynchronousChannelGroup(8, ThreadFactories.named("fs2-cp-tcp", true))


  def metricsSink[F[_]:Effect:Sync, O](periodicity:FiniteDuration): Sink[F,O] = { in:Stream[F,O] =>

      fs2.io.tcp.client[F](metrics).flatMap(socket =>  Stream.eval(async.refOf[F, Long](0)).flatMap(counter =>
        in.flatMap(r => Stream.eval(counter.modify(_ + 1)).flatMap(_  => Stream.emit())) concurrently
         Scheduler[F](1).flatMap(sched => sched.awakeEvery[F](periodicity)).flatMap(_ => Stream.eval(counter.get)).map(i => s"Processed ${i/(1024.0*1024.0)} MB \n").through(text.utf8Encode).to(socket.writes())
      ))


    







  }


  def stream(args:List[String], requestShutdown:IO[Unit]) = {

    val consoleRegex =  raw"(\d{4})-(\d{2})-(\d{2})".r

    val path = Paths.get("/Users/nchennamsetty/Source/Gogo/Data/awsdump/consoles/uber_console.log")
    val outPath = Paths.get("/Users/nchennamsetty/Source/Gogo/Data/awsdump/consoles/console_out.log")


    io.file.readAll[IO](path, 400000000)
      .observe(metricsSink(5.second))
      .through(text.utf8Decode)
      .through(text.lines)
      .through(MyUtil.combineAdjacentBy({s:String => !consoleRegex.findFirstIn(s).isDefined}))
      .through(text.utf8Encode)
      .to(io.file.writeAll[IO](outPath,  List(StandardOpenOption.CREATE_NEW)))
      .drain ++  Stream.emit(ExitCode.Success)


    }

}

