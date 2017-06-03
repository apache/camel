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

import builder.{RouteBuilderSupport, RouteBuilder}
import org.apache.camel.Exchange

import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.apache.camel.processor.onexception._

/**
 * Scala DSL equivalent for org.apache.camel.processor.onexception.OnExceptionHandledTest 
 */
class SOnExceptionHandledTest extends OnExceptionHandledTest with RouteBuilderSupport {

    override def createRouteBuilder = new RouteBuilder {
      handle[IllegalArgumentException] {
        to("log:foo?showAll=true")
        to("mock:handled")
      }.handled

      "direct:start" throwException new IllegalArgumentException("Forced")
    }
}

/**
 * Scala DSL equivalent for org.apache.camel.processor.onexception.OnExceptionComplexRouteTest
 */
class SOnExceptionComplexRouteTest extends OnExceptionComplexRouteTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    errorHandler(deadLetterChannel("mock:error"))

    handle[MyTechnicalException] {
      to("mock:tech.error")
    }.maximumRedeliveries(2).handled

    "direct:start" ==> {
      routeId("start")

      handle[MyFunctionalException]{
      }.maximumRedeliveries(0)

      to("bean:myServiceBean")
      to("mock:result")
    }

    "direct:start2" ==> {
      routeId("start2")

      handle[MyFunctionalException] {
        to("mock:handled")
      }.maximumRedeliveries(0).handled

      to("bean:myServiceBean")
      to("mock:result")
    }
  }
}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.OnExceptionRetryUntilWithDefaultErrorHandlerTest
 */
class SOnExceptionRetryUntilWithDefaultErrorHandlerTest extends ScalaTestSupport {

  var invoked = 0

  @Test
  def testRetryUntil() {
    val out = template.requestBody("direct:start", "Hello World")
    assertEquals("Sorry", out)
    assertEquals(3, invoked)
  }

  def threeTimes(exchange: Exchange) = {
    invoked += 1

    assertEquals("Hello World", exchange.in)
    assertTrue(exchange.getException.isInstanceOf[MyFunctionalException])

    exchange.getIn.getHeader(Exchange.REDELIVERY_COUNTER, classOf[Int]) < 3
  }

  val builder = new RouteBuilder {
    errorHandler(defaultErrorHandler.maximumRedeliveries(1).logStackTrace(false))

    handle[MyFunctionalException] {
      transform("Sorry")
    }.retryWhile(threeTimes).handled

    "direct:start" throwException new MyFunctionalException("Sorry, you cannot do this")
  }
}

