import fs2._
import fs2.io.file._
import fs2.async.mutable.Topic
import fs2.StreamApp.ExitCode

import cats._
import cats.implicits._
import cats.effect._
import java.nio.file.Paths
import scala.collection.mutable
import scala.concurrent.ExecutionContext


object ConsoleApp extends StreamApp[IO] {




  def stream(args:List[String], requestShutdown:IO[Unit]) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val consoleRegex =  raw"(\d{4})-(\d{2})-(\d{2})".r

    val path = Paths.get("/Users/nchennamsetty/Source/Gogo/Data/awsdump/consoles/N251AK.2017-03-16-13-40-01.console.log")

    Stream.eval(async.topic[IO, String](">> Console topic")).flatMap { topic =>  

   Stream( io.file.readAll[IO](path, 4000)
      .through(text.utf8Decode)
      .through(text.lines)
      .through(MyUtil.combineAdjacentBy({s:String => !consoleRegex.findFirstIn(s).isDefined}))
     .to(topic.publish),

     topic.subscribe(20).take(50).map(s => s">> $s").to(Sink.showLinesStdOut[IO,String]),

     topic.subscribe(10).filter({s:String => s.contains("Event")}).take(50).to(Sink.showLinesStdOut[IO,String])
   ).join(3).drain ++  Stream.emit(ExitCode.Success)



    }
  }


}
