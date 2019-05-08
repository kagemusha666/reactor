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

package reactor.simulations

import com.typesafe.config.ConfigFactory

import io.gatling.core.Predef._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.jdbc.Predef._

import scala.concurrent.duration._
import scala.language.postfixOps


object Scenario {

  val config = ConfigFactory.load().getConfig("postgres").getConfig("properties")
  val host = config.getString("serverName")
  val user = config.getString("user")
  val password = config.getString("password")

  val clientId = jdbcFeeder(s"jdbc:postgresql://$host/set", user, password,
    s"SELECT guid FROM card_clients WHERE firstname IS NOT NULL AND lastname IS NOT NULL").circular

  val doGetClient: ChainBuilder =
    feed(clientId)
      .group("Client") {
        exec(
          http("ClientRequest")
            .get("/client/" + "${guid}")
            .check(status.is(200))
        )
      }

  val httpConf: HttpProtocolBuilder = http
    .baseUrl("http://localhost:8080")
    .header(HttpHeaderNames.ContentType, HttpHeaderValues.ApplicationJson)

  def getClientScn(loopCount: Int = 1): ScenarioBuilder = scenario("DoRequestClient")
    .repeat(loopCount)(doGetClient)
}

class GetClientTest extends Simulation {

  private val injectionSteps = Seq(
    constantUsersPerSec(60) during (60 seconds)
  )

  private val assertions = Seq(
    details("Client").responseTime.max.lt(3000),
  )

  setUp(Scenario.getClientScn().inject(injectionSteps).protocols(Scenario.httpConf)).assertions(assertions)
}
