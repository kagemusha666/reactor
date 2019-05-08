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

import java.sql.{Connection, DriverManager}
import java.util.concurrent.{ConcurrentLinkedQueue, Semaphore, TimeUnit}

import com.typesafe.config.ConfigFactory

/**
  * Create new connections and keep them.
  */
class ConnectionPool {

  val config = ConfigFactory.load().getConfig("postgres")
  val maxConnectionCount = config.getInt("numThreads")
  val connections = new ConcurrentLinkedQueue[Connection]()
  val semaphore = new Semaphore(maxConnectionCount)

  {
    val connection = newConnection()
    if (connection == null)
      throw new IllegalStateException()
    connections.add(connection)
  }

  def closeSleepConnections():Int = {
    var closed = 0
    val connectionCountToClose = maxConnectionCount - 1
    if (semaphore.availablePermits() == maxConnectionCount && semaphore.tryAcquire(connectionCountToClose)) {
      try {
        var connectionCountRest = connectionCountToClose
        while (connectionCountRest > 0) {
          val connectionToClose = connections.poll()
          if (connectionToClose != null && connections.peek() != null) {
            connectionToClose.close()
            connectionCountRest -= 1
            closed +=1
          } else {
            connections.add(connectionToClose)
            connectionCountRest = 0
          }
        }
      } finally {
        semaphore.release(connectionCountToClose)
      }
    }
    closed
  }

  def closeAll(): Unit = {
    semaphore.acquire(maxConnectionCount)
    while (connections.peek() != null)
      connections.poll().close()
    semaphore.release(maxConnectionCount)
  }

  def obtainConnection(timeout:Long): Option[Connection] = {
    if (semaphore.tryAcquire(timeout, TimeUnit.MILLISECONDS)) {
      connections.poll() match {
        case connection:Connection if connection != null => Some(connection)
        case null => Some(newConnection())
        case _ => None
      }
    } else None
  }

  def releaseConnection(connection:Connection):Unit = {
    connections.add(connection)
    semaphore.release()
  }

  private def newConnection(): Connection = {
    val properties = config.getConfig("properties")
    val host = properties.getString("serverName")
    val user = properties.getString("user")
    val password = properties.getString("password")
    DriverManager.getConnection(s"jdbc:postgresql://$host/set?user=$user&password=$password")
  }

}
