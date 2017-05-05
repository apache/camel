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
import org.apache.camel.scala.Preamble
import org.apache.camel.processor.intercept.{InterceptFromWhenWithChoiceTest, InterceptFromUriSimpleLogTest, InterceptFromSimpleRouteTest}
import org.apache.camel.processor.interceptor.Tracer

/**
 * Scala DSL equivalent for InterceptFromSimpleRouteTest
 */
class SInterceptFromSimpleRouteTest extends InterceptFromSimpleRouteTest with RouteBuilderSupport with Preamble {

  def livingIn(city: String)(exchange: Exchange) = exchange.in("city") == city

  override def createRouteBuilder = new RouteBuilder {
    interceptFrom.when(livingIn("London")) {
      to ("mock:intercepted")
    }.stop

    from("seda:a").to("mock:result")
  }

}

/**
 * Scala DSL equivalent for InterceptFromUriSimpleLogTest
 */
class SInterceptFromUriSimpleLogTest extends InterceptFromUriSimpleLogTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    interceptFrom("seda:bar") to("mock:bar")

    "direct:start" to "mock:first" to "seda:bar"
    "seda:bar" to "mock:result"
    "seda:foo" to "mock:result"
  }

}

/**
 * Scala DSL equivalent for InterceptFromWhenWithChoiceTest
 */
class SInterceptFromWhenWithChoiceTest extends InterceptFromWhenWithChoiceTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    context.addInterceptStrategy(new Tracer())

    interceptFrom.when(simple("${body} contains 'Goofy'")) {
      choice {
        when (_.in[String].contains("Hello")) {
          to ("mock:hello")
        } otherwise {
          to ("log:foo")
          to ("mock:goofy")
        }
      }
      stop
    }

    "direct:start" to "mock:foo" to "mock:end"
  }
}
