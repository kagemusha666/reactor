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
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object EndpointWithSlick {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("reactor")
    implicit val materializer = ActorMaterializer()

    val db = Database.forConfig("postgres")
    system.registerOnTermination(() => db.close())

    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    implicit val getClientResult = GetResult(r => Client(r.nextString(), r.nextString))

    val storage = system.actorOf(Props[Storage], "storage")

    val route =
      get {
        pathPrefix("client2" / LongNumber) { id =>
          implicit val timeout: Timeout = 5.seconds

          // query the actor for the current auction state
          val client: Future[Client] = (storage ? RequestClientById(id)).mapTo[Client]
          complete(client)
        }
      } ~
        get {
          pathPrefix("client" / LongNumber) { id =>
            val action = sql"SELECT pg_sleep(0.04), firstname, lastname FROM card_clients WHERE guid = $id".as[Client]
              .map(c => c.head)
            val client: Future[Client] = db.run(action)
            complete(client)
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
