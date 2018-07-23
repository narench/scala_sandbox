import fs2._
import fs2.io._
import cats._
import cats.effect._
import cats.implicits._


object MyUtil {

  def groupAdjacentBy[F[_], O, O2](f: O => O2, outStream: Stream[F, O])(implicit eq: Eq[O2]): Stream[F, (O2, Segment[O, Unit])] = {

    def go(current: Option[(O2, Segment[O, Unit])],
      s: Stream[F, O]): Pull[F, (O2, Segment[O, Unit]), Unit] = {
      println(s"inside go line 1>>> current = $current, stream = $s")
        s.pull.unconsChunk.flatMap {
        case Some((hd, tl)) =>
          val (k1, out) = current.getOrElse((f(hd(0)), Segment.empty[O]))
           println(s"inside go some >>> k1 = $k1, out = $out")
          doChunk(hd, tl, k1, out, None)
        case None =>
          val l = current
            .map { case (k1, out) => Pull.output1((k1, out)) }
            .getOrElse(Pull
              .pure(()))
          println(s"inside go None >>> l = $l")
          l >> Pull.done
        }
     
    }

    @annotation.tailrec
    def doChunk(chunk: Chunk[O],
                s: Stream[F, O],
                k1: O2,
                out: Segment[O, Unit],
                acc: Option[Segment[(O2, Segment[O, Unit]), Unit]])
        : Pull[F, (O2, Segment[O, Unit]), Unit] = {
      println(s"inside doChunk >> chunk = $chunk, k1 = $k1, out = $out,  acc = $acc")
      val differsAt = chunk.indexWhere(v => eq.neqv(f(v), k1)).getOrElse(-1)
      if (differsAt == -1) {
        // whole chunk matches the current key, add this chunk to the accumulated output
        val newOut: Segment[O, Unit] = out ++ Segment.chunk(chunk)
        acc match {
          case None      => go(Some((k1, newOut)), s)
          case Some(acc) =>
            // potentially outputs one additional chunk (by splitting the last one in two)
            Pull.output(acc) >> go(Some((k1, newOut)), s)
        }
      } else {
        // at least part of this chunk does not match the current key, need to group and retain chunkiness
        // split the chunk into the bit where the keys match and the bit where they don't
        val matching = Segment.chunk(chunk).take(differsAt)
        val newOut: Segment[O, Unit] = out ++ matching.voidResult
        println(s"dochunk else >> newout = $newOut")
        val nonMatching = chunk.drop(differsAt)
        // nonMatching is guaranteed to be non-empty here, because we know the last element of the chunk doesn't have
        // the same key as the first
        val k2 = f(nonMatching(0))
        doChunk(nonMatching,
          s,
                k2,
                Segment.empty[O],
                Some(acc.getOrElse(Segment.empty) ++ Segment((k1, newOut))))
      }
    }

    go(None, outStream).stream
  }


/*
  def collapseMultiLineText[F[_], O, R](in:Stream[F,O], n:Int = 2): Stream[F,O] = {



    def go(strm: Stream[F, O]): Pull[F, O, Unit] = {
      println("Entering go...")

       strm.pull.unconsChunk.flatMap { opt : Option[(Chunk[O], Stream[F, O])] => opt match {
      case None => Pull.done
         case Some((hd, tail)) =>{
           println(s"chunk >> $hd, stream >> ${tail}")
           Pull.outputChunk(hd.take(hd.size -1)).flatMap { _ => go(tail) }
     }
       }
       }





     }

    def collapseChunkOutput(chk:Chunk[O], f: O => Boolean)(implicit sg : Semigroup[O]): Chunk[O] = {

      // I could write a recursive function but it would be more O(n*logn)??
      // you can avoid some of the construction/deconstruction with
      //Catenable(..ts(n-1), ts(n)) 
      // push current to partial record

      chk.foldLeft[Catenable[O]](Catenable.empty) {(out, current) =>
        if (f(current)) out ++ Catenable(sg.combine(current, current))
        else Catenable(current) }
      chk
      
    }


    go(in).stream
  }

  /// Chunk(1,1,1,1,0,0,0,0,1,1,1)

  // def doChunk[F[_], O](chunk: Chunk[O], s: Stream[F,O], f: O => Boolean, accum:Option[O]) (implicit sg: Semigroup[O]):Pull[F,O,Unit] = {



  //   val prBegin = chunk.indexWhere(f).getOrElse(0) - 1
  //   val (chunk1, chunk2) = chunk.splitAt(prBegin)

  //   (chunk1, chunk2) match {

  //     case (Chunk.empty, Chunk.empty) => Pull.done
  //   }





  //   Pull.done

  // }


  //  def doChunkSeg[F[_], O](chunk: Chunk[O], out: Segment[O, Unit], f: O => Boolean) (implicit sg: Semigroup[O]):Segment[O,Unit] = {



  //   val splitIndex = chunk.indexWhere(f).getOrElse(-1)
  //   val (chunk1, chunk2) = chunk.splitAt(splitIndex)

  //    Segment.chunk(chunk1.take(splitIndex-1)) ++ Segment(sg.combine(chunk1.last.get, chunk2.head.get)) ++ doChunkSeg(
  //  }


 */

  


 /* def scratch() {

  val names = Chunk.vector(Vector("naren", "noreen", "nancy", "alpha", "richard", "bailey", "hannah", "darryl", "montana", "missouri", "paris", "lyviv", "nouveau"))
    type A = (Catenable[String], Option[String])
    type B = (Segment[String, Unit], Option[String])


  names.foldLeft[A]((Catenable.empty,None)){(out, e) => out match {
   // close partial record and emit if current element is n
    case (a , Some(pr)) if e.startsWith("n") => (a.snoc(pr), Some(e))
   // open partial record
    case (a, None) if e.startsWith("n") => (a, Some(e))
    case (a, Some(pr) ) => (a, Some(pr + e))
     case (a, None) => (a, Some(e))
  }}



    /*
    this works but one chunk is emitted per element
    (catenated(Chunk(naren), Chunk(noreen), Chunk(nancyalpharichardbaileyhannahdarrylmontanamissouriparislyviv)),Some(nouveau))
    */
   
    names.foldLeft[B]((Segment.empty,None)){(out, e) => out match {
   // close partial record and emit if current element is n
    case (a , Some(pr)) if e.startsWith("n") => (a ++ Segment(pr), Some(e))
   // open partial record
    case (a, None) if e.startsWith("n") => (a, Some(e))
    case (a, Some(pr) ) => (a, Some(pr + e))
     case (a,  None) => (a, Some(e))
    }}

  }*/

////////////////////////////////////////////////

     def combineAdjacentBy[F[_],O](s:Stream[F,O], f: O => Boolean)(implicit m: Monoid[O]): Stream[F,O] = {

       def doChunk(chunk: Chunk[O], s:Stream[F,O], current:Option[O],  accum:Option[Segment[O, Unit]]): Pull[F, O, Unit] = {

         val idxPartialRecordBegin = chunk.indexWhere(f).getOrElse(-1)

         println(s"inside doChunk idx = $idxPartialRecordBegin, chunk = ${chunk} accum = $accum current = $current")

         //homogenous chunk of 1s
         if (idxPartialRecordBegin == -1){

           println("outputting chunk...")

           accum match {
             // TODO Fix this
             case None =>
               Pull.output1(current.getOrElse(m.empty)) >> Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last, None)
             case Some(a) => Pull.output(a) >>  Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last, None)

          
           }

         }

         else {

           val (chunk1, chunk2) = chunk.splitAt(idxPartialRecordBegin)
           val primaries = Segment.chunk(chunk1.take(idxPartialRecordBegin - 1))

           // slurp all zeros
           val idxPartialRecordEnd = chunk2.indexWhere(f andThen {y:Boolean => !y}).getOrElse(chunk2.size)
           
           // if current is not empty and ends with a zero - currenct record is accum.last + tail
           

           val currentRecord = m.combine(chunk1.last.getOrElse(m.empty), chunk2.take(idxPartialRecordEnd).foldLeft(m.empty)(m.combine))

           println(s" idxBegin = $idxPartialRecordBegin idxEnd = $idxPartialRecordEnd, chunk1= $chunk1 chunk2 = $chunk2 current = $current, currentRecord = $currentRecord accum = $accum")

         
          doChunk(chunk2.drop(idxPartialRecordEnd), s, None,  Some(accum.getOrElse(Segment.empty) ++ primaries ++ Segment.singleton(currentRecord)))

         }
       }



       def go(s:Stream[F,O], current:Option[O], accum:Option[Segment[O, Unit]]): Pull[F, O, Unit] = {

         s.pull.unconsChunk.flatMap {
           case Some((head, tail)) => doChunk(head, tail, current, accum)
           case None =>  current match {
             case Some(e) => Pull.output1(e) >> Pull.done
             case None => Pull.done
           }

           }


       }

       go(s, None, None).stream

     }




}