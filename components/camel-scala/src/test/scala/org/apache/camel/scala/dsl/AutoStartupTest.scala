/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.scala.dsl

import org.apache.camel.RoutesBuilder
import org.apache.camel.ServiceStatus
import org.apache.camel.ServiceStatus.{Started, Stopped}
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.scala.dsl
import org.apache.camel.scala.dsl.AutoStartupTest._
import org.apache.camel.test.junit4.CamelTestSupport
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike}

class AutoStartupTest extends CamelTestSupport with FunSuiteLike with BeforeAndAfterAll {

  override protected def createRouteBuilders(): Array[RoutesBuilder] = {
    Array(
      createRoute(_ from s"direct:start1" id withoutDslRouteId to "mock:output"),
      createRoute(_ from s"direct:start2" autoStartup false id booleanDslRouteId to "mock:output"),
      createRoute(_ from s"direct:start3" autoStartup "false" id stringDslRouteId  to "mock:output"),
      createRoute(_ from s"direct:start4" noAutoStartup() id noAutoStartupDslRouteId to "mock:output")
    )
  }

  override def beforeAll: Unit = {
    setUp()
  }

  test("route without auto startup dsl, is started by default") {
    assertRouteStatus(withoutDslRouteId, Started)
  }

  test("route with auto startup dsl using 'false' boolean, is stopped") {
    assertRouteStatus(booleanDslRouteId, Stopped)
  }

  test("route with auto startup dsl using 'false' string, is stopped") {
    assertRouteStatus(stringDslRouteId, Stopped)
  }

  test("route with no auto startup dsl, is stopped") {
    assertRouteStatus(noAutoStartupDslRouteId, Stopped)
  }

  private def assertRouteStatus(routeId: String, status: ServiceStatus) {
    assert(context().getRouteStatus(routeId) === status)
  }

  override protected def afterAll(): Unit = {
    tearDown()
  }
}

object AutoStartupTest {

  private val withoutDslRouteId: String = "without-dsl-route"
  private val booleanDslRouteId: String = "boolean-dsl-route"
  private val stringDslRouteId: String = "string-dsl-route"
  private val noAutoStartupDslRouteId: String = "no-auto-startup-dsl-route"

  private def createRoute(routeBuilderFunction: (dsl.builder.RouteBuilder) => Unit): RouteBuilder =
    new dsl.builder.RouteBuilder {
      routeBuilderFunction(this)
    }.builder

}

