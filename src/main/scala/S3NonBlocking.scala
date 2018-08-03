import fs2._
import fs2.io._
import cats._
import cats.effect._
import cats.implicits._
import software.amazon.awssdk.services.s3.{S3AsyncClient}
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}
import java.util.concurrent.{CompletableFuture, CompletionStage}
import java.nio.file.Paths
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import scala.compat.java8.FutureConverters

object S3NonBlockingApp extends App {



  val  BUCKET = "sample-bucket"
  val KEY = "testfile.out"

 override def main(args: Array[String]): Unit =
  {
    val client = S3AsyncClient.create()

   
      val  futureGet:CompletableFuture[GetObjectResponse]  = client.getObject(
                GetObjectRequest.builder()
                                .bucket(BUCKET)
                                .key(KEY)
                  .build(),
        Paths.get("myfile.out"))

        //AsyncResponseTransformer.toFile(Paths.get("myfile.out")))




      futureGet.whenComplete((resp, err) => {
            try {
                if (resp != null) {
                    System.out.println(resp)
                } else {
                    // Handle error
                    err.printStackTrace()
                }
            } finally {
                // Lets the application shut down. Only close the client when you are completely done with it
                client.close()
            }
        })
     // this if just future.get 
    futureGet.join()

    val io = IO.fromFuture(IO(FutureConverters.toScala(futureGet)))
    

    }





}
