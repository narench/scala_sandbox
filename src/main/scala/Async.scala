
import fs2._
import fs2.io._
import cats._
import cats.effect._
import cats.implicits._

object AsyncApp extends App {

trait Connection {
  def readBytes(onSuccess: Array[Byte] => Unit, onFailure: Throwable => Unit): Unit

  // or perhaps
  def readBytesE(onComplete: Either[Throwable,Array[Byte]] => Unit): Unit =
    readBytes(bs => onComplete(Right(bs)), e => onComplete(Left(e)))

  override def toString = "<connection>"
}


val c = new Connection {
  def readBytes(onSuccess: Array[Byte] => Unit, onFailure: Throwable => Unit): Unit = {
    Thread.sleep(200)
    onSuccess(Array(0,1,2))
  }
}
// c: Connection = <connection>

// Effect extends both Sync and Async
val T = cats.effect.Effect[IO]
// T: cats.effect.Effect[cats.effect.IO] = cats.effect.IOInstances$$anon$1@3ebc9073

val bytes = T.async[Array[Byte]] { (cb: Either[Throwable,Array[Byte]] => Unit) =>
  c.readBytesE(cb)
}
// bytes: cats.effect.IO[Array[Byte]] = IO$546302631

  Stream.eval(bytes).map(_.toList).compile.toVector.unsafeRunSync()

}
