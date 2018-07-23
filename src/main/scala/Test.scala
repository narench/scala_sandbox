
import cats._
//import shapeless._
import cats.data.Writer
import cats.instances.vector._
import fs2._

object MyApp2 extends App {

//  override  def main(args: Array[String] ) ={

    def testDebugging(x: Int) = {
      println(x)
    }

    testDebugging(1000)

    // def sumAdjacentN[F[_], O](s: Stream[F, O], n: Int)(implicit m: Monoid[O]): Pull[F, O, _] = {
 
  //   s.pull.unconsLimit(n).flatMap{ opt  => opt match {
  //     case None => Pull.done
  //     case Some((seg: Segment[O,Unit], strm: Stream[F,O])) => seg.take(100L).flatMap(eit  => eit match {
  //       case Left(_) => Pull.done
  //       case Right
  //     })


  //   }

    
  //   }






  // }

 /* def tk[F[_],O](n: Long): Pipe[F,O,O] = {
    def go(s: Stream[F,O], n: Long): Pull[F,O,Unit] = {
      s.pull.uncons.flatMap {
        case Some((hd,tl)) =>
          Pull.segment(hd.take(n)).flatMap {
            case Left((_,rem)) => go(tl,rem)
            case Right(_) => Pull.done
          }
        case None => Pull.done
      }
    }
    in => go(in,n).stream
  }

  // TODO Write takeWhile using Segment

  def takeWhile[F[_],O](pred: O => Boolean): Pipe[F,O,O] = {

    def go(s: Stream[F,O], pred: O => Boolean): Pull[F, O, Unit] = {

     //uncons gives you a pull with
      // Is Pull a recursive type

    s.pull.uncons.flatMap { r => r match {

      case Some((hd, tail)) => Pull.segment(hd.takeWhile(pred))

    }

    }


    }

  }*/

   




 // }

}
