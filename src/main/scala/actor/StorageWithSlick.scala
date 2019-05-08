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

import akka.actor.{Actor, ActorLogging}
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._

import scala.util.Success

/**
  * Get data from DB.
  */
class StorageWithSlick extends Actor with ActorLogging {

  implicit val executionContext = context.dispatcher

  private var database:Database = null

  override def preStart(): Unit = {
    database = Database.forConfig("postgres")
  }

  override def postStop(): Unit = {
    database.close()
  }

  override def receive: Receive = {
    case RequestClientById(id) => {
      implicit val getClientResult = GetResult(r => Client(r.skip.nextString(), r.nextString))

      val action = sql"SELECT pg_sleep(0.04), firstname, lastname FROM card_clients WHERE guid = $id".as[Client]
        .map(c => c.head)

      val endpoint = sender()

      database.run(action).onComplete {
        case Success(client) => endpoint ! client
        case _ => endpoint ! RequestFailed
      }
    }
    case _ => log.error("Unknown message!")
  }

}
