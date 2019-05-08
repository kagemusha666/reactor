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

import java.sql.{Connection, ResultSet}
import java.util.concurrent.{ForkJoinPool, RejectedExecutionException}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

/**
  * Request client
  *
  * @param id client ID
  */
case class RequestClientById(id:Long, timestamp:Long = System.currentTimeMillis())
case class RequestFailed()

object Storage {

  def props(connectionPool: ConnectionPool): Props = Props(new Storage(connectionPool))

  private case object TickKey
  private case object Tick

}

/**
  * Get data from DB.
  */
class Storage(connectionPool: ConnectionPool) extends Actor with Timers with ActorLogging {
  import Storage._

  val minTimeout = 500

  val threadPool = new ForkJoinPool(connectionPool.maxConnectionCount)

  implicit val executionContext = context.dispatcher

  timers.startPeriodicTimer(TickKey, Tick, 1.minute)

  override def receive: Receive = {
    case RequestClientById(id, timestamp) =>
      submitRequest(id, timestamp, sender())
    case Tick =>
      val number = connectionPool.closeSleepConnections()
      log.info(s"Closed $number connections.")
    case _ => log.error("Unexpected message!")
  }

  override def postStop():Unit = {
    threadPool.shutdown()
    connectionPool.closeAll()
  }

  private def submitRequest(id:Long, timestamp:Long, recepient:ActorRef): Unit = {
    try {
      val recipient = sender()
      val deadline = calculateDeadline(timestamp)

      threadPool.submit(new Runnable {
        override def run(): Unit = requestId(id, deadline, recipient)
      })
    } catch {
      case e:RejectedExecutionException => sender() ! RequestFailed
      case e:TimeoutException => log.info("Skip request before submit")
      case _:Throwable => log.error("Unexpected exception!")
    }
  }

  private def requestId(id:Long, deadline:Long, recepient:ActorRef):Unit = {
    try {
      val connection: Option[Connection] = connectionPool.obtainConnection(toTimeout(deadline))
      if (connection.isDefined) {
        try {
          recepient ! doRequest(connection.get, id, deadline)
        } finally {
          connectionPool.releaseConnection(connection.get)
        }
      } else {
        log.warning("Failed to obtain connection")
      }
    } catch {
      case e:TimeoutException => log.info("Request timeout")
      case e:Throwable => log.error("Request failed: {}", e)
    }
  }

  private def doRequest(connection: Connection, id:Long, deadline:Long): Client = {
    val statement = connection.prepareStatement(s"SELECT pg_sleep(0.04), firstname, lastname FROM card_clients WHERE guid = $id")
    try {
      statement.setQueryTimeout(toTimeoutInSec(deadline))
      val resultSet = statement.executeQuery()
      try {
        toClient(resultSet)
      } finally {
        resultSet.close()
      }
    } finally {
      statement.close()
    }
  }

  private def toClient(resultSet: ResultSet): Client = {
    resultSet.next()
    Client(resultSet.getString(2), resultSet.getString(3))
  }

  private def calculateDeadline(timestamp:Long):Long = {
    val deadline = timestamp + 3000
    val current = System.currentTimeMillis()
    if (deadline - current > minTimeout) deadline
    else throw new TimeoutException
  }

  private def toTimeout(deadline:Long):Long =  {
    val timeout = deadline - System.currentTimeMillis()
    if (timeout > minTimeout) timeout
    else throw new TimeoutException
  }

  private def toTimeoutInSec(deadline:Long):Int = {
    val sec = ((deadline - System.currentTimeMillis()) / 60).toInt
    if (sec > 0) sec
    else throw new TimeoutException
  }

}
