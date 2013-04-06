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

import org.junit.Test
import builder.RouteBuilder
import ExchangePattern.{InOnly, InOut}

class ExchangePatternTest extends ScalaTestSupport {

  @Test
  def testInOnly() {
    getMockEndpoint("mock:a").expectedMessageCount(1)
    getMockEndpoint("mock:a").expectedExchangePattern(InOnly)
    getMockEndpoint("mock:result").expectedMessageCount(1)
    getMockEndpoint("mock:result").expectedExchangePattern(InOnly)

    template.sendBody("direct:a", "Hello World")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testRequestInOnly() {
    getMockEndpoint("mock:a").expectedMessageCount(1)
    getMockEndpoint("mock:a").expectedExchangePattern(InOnly)
    getMockEndpoint("mock:result").expectedMessageCount(1)
    getMockEndpoint("mock:result").expectedExchangePattern(InOut)

    template.requestBody("direct:a", "Hello World")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testInOut() {
    getMockEndpoint("mock:b").expectedMessageCount(1)
    getMockEndpoint("mock:b").expectedExchangePattern(InOut)
    getMockEndpoint("mock:result").expectedMessageCount(1)
    getMockEndpoint("mock:result").expectedExchangePattern(InOnly)

    template.sendBody("direct:b", "Hello World")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testRequestInOut() {
    getMockEndpoint("mock:b").expectedMessageCount(1)
    getMockEndpoint("mock:b").expectedExchangePattern(InOut)
    getMockEndpoint("mock:result").expectedMessageCount(1)
    getMockEndpoint("mock:result").expectedExchangePattern(InOut)

    template.requestBody("direct:b", "Hello World")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testMixed() {
    getMockEndpoint("mock:c").expectedMessageCount(1)
    getMockEndpoint("mock:c").expectedExchangePattern(InOut)
    getMockEndpoint("mock:result").expectedMessageCount(1)
    getMockEndpoint("mock:result").expectedExchangePattern(InOnly)

    template.sendBody("direct:c", "Hello World")

    assertMockEndpointsSatisfied()
  }

  val builder = new MyRouteBuilder
  
  class MyRouteBuilder extends RouteBuilder {
    "direct:a" --> (InOnly, "mock:a") --> "mock:result"
    "direct:b" --> (InOut, "mock:b") --> "mock:result"
    "direct:c" to (InOut, "mock:c") to (InOnly, "mock:result")
  }

}
