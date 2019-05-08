/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org>
 */

package actor

import actor.ClientJsonProtocol._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.TimeoutException
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
  * Setup router.
  */
object Endpoint {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("reactor")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val storage = system.actorOf(Storage.props(new ConnectionPool), "storage")

    val route =
      get {
        pathPrefix("client" / LongNumber) { id =>
          implicit val timeout: Timeout = 3.seconds

          onComplete(storage ? RequestClientById(id)) {
            case Success(value) =>
              if (value.isInstanceOf[Client])
                complete(value.asInstanceOf[Client])
              else
                complete(StatusCodes.RequestTimeout)
            case Failure(ex) =>
              if (ex.isInstanceOf[TimeoutException])
                complete(StatusCodes.RequestTimeout)
              else
                complete(StatusCodes.InternalServerError)
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}
