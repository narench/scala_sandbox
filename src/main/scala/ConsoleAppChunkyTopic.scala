import fs2._
import fs2.io.file._
import fs2.async.mutable.Topic
import fs2.StreamApp.ExitCode

import cats._
import cats.implicits._
import cats.effect._
import java.nio.file.Paths
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.spi.AsynchronousChannelProvider

import fs2.internal.ThreadFactories
import java.net.{InetSocketAddress, InetAddress}

import  com.amazonaws.services.s3.AmazonS3ClientBuilder
import blobstore.s3.S3Store
import blobstore.Store


object ConsoleAppChunkyTopic extends StreamApp[IO] {

  val local = new InetSocketAddress(InetAddress.getByName(null), 5555)
  val gps  = new InetSocketAddress(InetAddress.getByName(null), 5556)
  val adobe =  new InetSocketAddress(InetAddress.getByName(null), 7798)
  val metrics =  new InetSocketAddress(InetAddress.getByName(null), 7797)
  val chkSize =  new InetSocketAddress(InetAddress.getByName(null), 7799)

   implicit val tcpACG: AsynchronousChannelGroup = AsynchronousChannelProvider
    .provider()
    .openAsynchronousChannelGroup(3, ThreadFactories.named("fs2-sbx-tcp", true))


  def stream(args:List[String], requestShutdown:IO[Unit]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val consoleRegex =  raw"(\d{4})-(\d{2})-(\d{2})".r

    val path = blobstore.Path("gogo-udp-architecture/naren", "uber_console.log", None, false, None)
    val individualpath = blobstore.Path("gogo-udp-architecture", "naren/individual", None, false, None)
    val s3Store = S3Store[IO](AmazonS3ClientBuilder.standard().build())

    Stream.eval(async.topic[IO, Chunk[String]](Chunk(">> Console topic"))).flatMap { topic =>

     //  

      Stream(
    // s3Store.list(individualpath).filter(p => p.key.endsWith("log")).flatMap { p =>

        s3Store.get(path, 4000000)
     // .through(compress.inflate[IO](nowrap=true,bufferSize=40000))
      .through(text.utf8Decode)
        .through(text.lines)
        .through(MyUtil.combineAdjacentBy({s:String => !consoleRegex.findFirstIn(s).isDefined}))
          .chunkLimit(4000000)
// }
      .to(topic.publish),

    

     fs2.io.tcp.client[IO](local).flatMap(socket => topic.subscribe(2000000).flatMap(chk => Stream.emits(chk.filter_({s:String => s.contains("GPS")}))).intersperse("\n").through(text.utf8Encode).to(socket.writes())),

     fs2.io.tcp.client[IO](gps).flatMap(socket => topic.subscribe(2000000).flatMap(chk => Stream.emits(chk.filter_({s:String => s.contains("Horizontal Velocity")}))).intersperse("\n").through(text.utf8Encode).to(socket.writes())),

        fs2.io.tcp.client[IO](adobe).flatMap(socket => topic.subscribe(2000000).flatMap(chk => Stream.emits(chk.filter_({s:String => s.contains("ADOBE")}))).intersperse("\n").through(text.utf8Encode).to(socket.writes())),

     // increment counter for each element in topic, get value every 5 seconds

    fs2.io.tcp.client[IO](metrics).flatMap(socket =>  Stream.eval(async.refOf[IO, Int](0)).flatMap(counter =>
       topic.subscribe(1000000).flatMap(chk => Stream.eval(counter.modify(_ + chk.size))) concurrently
         Scheduler[IO](1).flatMap(sched => sched.awakeEvery[IO](5.seconds)).flatMap(_ => Stream.eval(counter.get)).map(i => s"processed $i records \n").through(text.utf8Encode).to(socket.writes())
    )),

     fs2.io.tcp.client[IO](chkSize).flatMap(socket => topic.subscribe(500000).flatMap(chk  => Stream.emit(chk.size.toString)).intersperse("\n").through(text.utf8Encode).to(socket.writes()))
     
   ).join(8).drain ++  Stream.emit(ExitCode.Success)



    }
  }


}
