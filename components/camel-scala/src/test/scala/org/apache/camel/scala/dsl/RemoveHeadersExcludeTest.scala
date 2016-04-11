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
package org.apache.camel.scala
package dsl

import builder.RouteBuilder
import org.apache.camel.scala.test.Cat
import org.junit.Test
import org.junit.Assert.assertEquals
import scala.collection.JavaConverters._

/**
 * Test for setting the message header from the Scala DSL
 */
class RemoveHeadersExcludeTest extends ScalaTestSupport {

  @Test
  def testRemoveHeadersWildcard() {
    test {
      val headers = Map[String, AnyRef](
        "dudeCool" -> "cool",
        "dudeWicket" -> "wicket",
        "duck" -> "Donald",
        "foo" -> "bar").asJava

      template.sendBodyAndHeaders("direct:start", "Hello World", headers)
    }

    assertMockEndpointsSatisfied()

    "mock:end" expect { endpoint =>
      endpoint.headerReceived("duck", "Donald")
      endpoint.received("Hello World")

      // there is also a breadcrumb header
      assertEquals(2, endpoint.getReceivedExchanges.get(0).getIn.getHeaders.size)
    }
  }

  @Test
  def testRemoveHeadersRegex() {
    test {
      val headers = Map[String, AnyRef](
        "dudeCool" -> "cool",
        "dudeWicket" -> "wicket",
        "duck" -> "Donald",
        "BeerCarlsberg" -> "Great",
        "BeerTuborg" -> "Also Great",
        "BeerHeineken" -> "Good").asJava

      template.sendBodyAndHeaders("direct:start", "Hello World", headers)
    }

    assertMockEndpointsSatisfied()

    "mock:end" expect { endpoint =>
      endpoint.headerReceived("duck", "Donald")
      endpoint.headerReceived("BeerHeineken", "Good")
      endpoint.headerReceived("BeerTuborg", "Also Great")
      endpoint.received("Hello World")

      // there is also a breadcrumb header
      assertEquals(4, endpoint.getReceivedExchanges.get(0).getIn.getHeaders.size)
    }
  }

  val builder = new RouteBuilder {
    "direct:start" ==> {
      removeHeaders("dude*")
      removeHeaders("Beer*", ".*Heineken.*", ".*Tuborg.*")
      removeHeaders("foo")
      to ("mock:end")
    }
  }
}
