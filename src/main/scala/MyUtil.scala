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




////////////////////////////////////////////////

     def combineAdjacentBy[F[_],O](f: O => Boolean)(implicit m: Monoid[O]): Pipe[F,O, O] = { s: Stream[F,O] =>

       def doChunk(chunk: Chunk[O], s:Stream[F,O], current:Option[O],  accum:Option[Segment[O, Unit]]): Pull[F, O, Unit] = {

         val prBegin = chunk.indexWhere(f).getOrElse(-1)

        //  println(s"inside doChunk prBegin = $prBegin, chunk = ${chunk} accum = $accum current = $current")

         //Chunk(2,2,2) or Chunk.empty
         if (prBegin == -1){

          // println(s"outputting chunk...current = $current accum = $accum")

           (accum, current) match {
  
             case (None, None) =>
                 Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last, None)
             case (Some(a), None) => Pull.output(a) >>  Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last, None)

             case (Some(a), Some(c)) =>
               if(chunk.isEmpty)
                  Pull.output(a) >> Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, current, None)
                 else
               Pull.output(a) >> Pull.output1(c) >> Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last , None)

             case (None, Some(c)) =>
               if(chunk.isEmpty)
                  Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, current, accum)
               else
                 Pull.output1(c) >> Pull.outputChunk(chunk.take(chunk.size -1)) >> go(s, chunk.last, accum)
          
           }

         }

         else {

      
           val (chunk1, chunk2) = chunk.splitAt(prBegin)
          // val primaries = chunk1.take(prBegin - 1)
           val prEnd = chunk2.indexWhere(f andThen {y:Boolean => !y}).getOrElse(chunk2.size)

          
           // Chunk (1,1,1,....)
           if(chunk1.isEmpty){

             //Chunk(1,1,1,1)
             if (prEnd == chunk2.size) {
               val newCurrent = Some(m.combine(current.getOrElse(m.empty), chunk2.foldLeft(m.empty)(m.combine)))
               // println(s" >> if if chunk1 is empty- all 1s current = $newCurrent accum = $accum")
               doChunk(Chunk.empty, s, newCurrent, accum)
             }
             else {
              
              //Chunk(1,1,1,1,2....)
               val reducedO =   m.combine(current.getOrElse(m.empty), chunk2.take(prEnd).foldLeft(m.empty)(m.combine))
               val newAccum =  Some(accum.getOrElse(Segment.empty)  ++ Segment.singleton(reducedO))
               // println(s" >> if else  chunk1 is empty, mixed current = None accum = $newAccum")
               doChunk(chunk2.drop(prEnd), s, None, newAccum)
               }
           }
           else {
             // Chunk(2,1,1,1) or Chunk(2,2,2,2,1,1,1,1)
             if (prEnd == chunk2.size) {
               // println(" >> else if chunk1 is not empty, chunk2 is all 1s")
               val newCurrent = Some(m.combine(chunk1.last.getOrElse(m.empty), chunk2.foldLeft(m.empty)(m.combine)))
              // println(s"primaries is not empty- all 1s current = $newCurrent accum = $accum")

               doChunk(Chunk.empty, s,newCurrent, Some(accum.getOrElse(Segment.empty) ++ Segment.chunk(chunk1.take(chunk1.size-1))))


             }
             // Chunk(2,2,2,1,1,1,2,...)
             else {
               // println(" >> else else chunk1 is not empty, chunk2 is not all 1s")
               val reducedO =  m.combine(chunk1.last.getOrElse(m.empty), chunk2.take(prEnd).foldLeft(m.empty)(m.combine))
               val currSeg  = current match {
                 case Some(c) => Segment.singleton(c)
                 case None => Segment.empty
               }

               doChunk(chunk2.drop(prEnd), s, None, Some(accum.getOrElse(Segment.empty) ++ currSeg ++  Segment.chunk(chunk1.take(chunk1.size-1)) ++ Segment.singleton(reducedO)))
             

             }


           }
        

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


  import cats.effect.{Effect, IO}
import fs2.StreamApp.ExitCode
import fs2.async.Promise
import fs2.{Scheduler, Stream, StreamApp, async}

import scala.concurrent.ExecutionContext.Implicits.global

class ConcurrentCompletion[F[_]](p: Promise[F, Int])(implicit F: Effect[F]) {

  private def attemptPromiseCompletion(n: Int): Stream[F, Unit] =
    Stream.eval(p.complete(n)).attempt.drain

  def start: Stream[F, ExitCode] =
    Stream(
      attemptPromiseCompletion(1),
      attemptPromiseCompletion(2),
      Stream.eval(p.get).evalMap(n => F.delay(println(s"Result: $n")))
    ).join(3).drain ++ Stream.emit(ExitCode.Success)

}

class Once[F[_]: Effect] extends StreamApp[F] {

  override def stream(args: List[String], requestShutdown: F[Unit]): fs2.Stream[F, ExitCode] =
    Scheduler(corePoolSize = 4).flatMap { implicit scheduler =>
      for {
        p <- Stream.eval(async.promise[F, Int])
        e <- new ConcurrentCompletion[F](p).start
      } yield e
    }

}




}
