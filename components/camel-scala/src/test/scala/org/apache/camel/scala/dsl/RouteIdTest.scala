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
package org.apache.camel
package scala.dsl

import builder.RouteBuilder
import org.junit.Test
import org.junit.Assert.assertEquals

class RouteIdTest extends ScalaTestSupport {

  @Test
  def testRouteA() {
    "mock:a" expect { _.expectedMessageCount(1)}

    test {
      "direct:a" ! "Hello World"
    }

    assertMockEndpointsSatisfied()

    assertEquals("route-a", context.getRouteDefinitions.get(0).getId)
    assertEquals("This is the a route", context.getRouteDefinitions.get(0).getDescriptionText)
  }

  @Test
  def testRouteB() {
    "mock:b" expect { _.expectedMessageCount(1)}

    test {
      "direct:b" ! "Hello World"
    }

    assertMockEndpointsSatisfied()

    assertEquals("route-b", context.getRouteDefinitions.get(1).getId)
  }

  val builder = new RouteBuilder {

    // java DSL
    from("direct:a") routeId "route-a" routeDescription "This is the a route" to "mock:a"

    // scala DSL
    "direct:b" routeId "route-b" to "mock:b"

  }

}