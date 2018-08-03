import fs2._
import fs2.async.mutable.Topic
import fs2.StreamApp.ExitCode
import cats.implicits._
import cats.effect._

import scala.collection.mutable
import scala.concurrent.ExecutionContext


object TestTopicApp  extends StreamApp[IO] {

  def stream(args:List[String], requestShutdown:IO[Unit]):Stream[IO, ExitCode] ={
  import ExecutionContext.Implicits.global

    Stream.eval(async.topic[IO, Int](0)).flatMap{
    
     t => Stream(Stream.range(1,10).covary[IO].to(t.publish),
      t.subscribe(10).filter(_%2 == 0).to(Sink.showLinesStdOut[IO,Int]),
       t.subscribe(10).filter(_%3 == 0).to(Sink.showLinesStdOut[IO,Int]))

    }.join(3).drain ++ Stream.emit(ExitCode.Success)
  }
}
