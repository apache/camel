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

import java.util.List
import org.apache.camel.scala.dsl.builder.RouteBuilder
import org.apache.camel.spi.RouteStartupOrder;
import org.junit.Assert.assertEquals
import org.junit.Test

class StartOrderTest extends ScalaTestSupport {

  @Test
  def testRouteA() {
    "mock:result" expect { _.expectedMessageCount(1)}

    test {
      "direct:start" ! "Hello World"
    }

    assertMockEndpointsSatisfied()

    assertEquals(4, context().getRouteStartupOrder().size())
    assertEquals("seda://foo", context().getRouteStartupOrder().get(0).getRoute().getEndpoint().getEndpointUri())
    assertEquals("direct://start", context().getRouteStartupOrder().get(1).getRoute().getEndpoint().getEndpointUri())
    assertEquals("seda://bar", context().getRouteStartupOrder().get(2).getRoute().getEndpoint().getEndpointUri())
    assertEquals("direct://bar", context().getRouteStartupOrder().get(3).getRoute().getEndpoint().getEndpointUri())
  }


  val builder = new RouteBuilder {

    from("direct:start").startupOrder(2).to("seda:foo")
    "seda:foo" startupOrder 1 to "mock:result"
    "direct:bar" startupOrder 9 to "seda:bar"
    "seda:bar" startupOrder 5 to "mock:other"

  }

}