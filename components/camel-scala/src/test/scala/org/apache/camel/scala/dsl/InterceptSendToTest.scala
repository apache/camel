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

import org.junit.Test
import builder.{RouteBuilderSupport, RouteBuilder}
import org.apache.camel.processor.intercept.InterceptSendToFaultTest

/**
 * Scala DSL equivalent for the InterceptSendToEndpointTest#testInterceptEndpoint test
 */
class InterceptSendToTest extends ScalaTestSupport {

  @Test
  def testSimpleSendTo() {
    "mock:bar" expect {_.received("Hello World")}
    "mock:detour" expect {_.received("Hello World")}
    "mock:foo" expect {_.received("Bye World")}
    "mock:result" expect {_.received("Bye World")}

    test {
      "direct:first" ! "Hello World"
    }
  }

  val builder = new RouteBuilder {
    interceptSendTo("mock:foo") {
      to("mock:detour")
      transform("Bye World")
    }

    "direct:first" ==> {
      to("mock:bar")
      to("mock:foo")
      to("mock:result")
    }
  }
}

/**
 * Scala DSL equivalent for the InterceptSendToEndpointTest#testInterceptEndpointWithPredicate test
 */
class InterceptSendToWithPredicateTest extends ScalaTestSupport {

  @Test
  def testSendToWithWhen() {
    "mock:bar" expect {_.received("Hello World", "Hi")}
    "mock:detour" expect {_.received("Hello World")}
    "mock:foo" expect {_.received("Bye World", "Hi")}
    "mock:result" expect {_.received("Bye World", "Hi")}

    test {
      "direct:second" ! ("Hello World", "Hi")
    }
  }

  val builder = new RouteBuilder {
    interceptSendTo("mock:foo").when(_.in[String] == "Hello World") {
      to("mock:detour")
      transform("Bye World")
    }

    "direct:second" ==> {
      to("mock:bar")
      to("mock:foo")
      to("mock:result")
    }
  }
}

/**
 * Scala DSL equivalent for the InterceptSendToEndpointTest#testInterceptEndpointStop test
 */
class InterceptSendToSkipOriginalTest extends ScalaTestSupport {

  @Test
  def testSendToAndSkipOriginal() {
    "mock:bar" expect {_.received("Hello World")}
    "mock:detour" expect {_.received("Bye World")}
    "mock:foo" expect {_.count = 0}
    "mock:result" expect {_.count = 1}

    test {
      "direct:third" ! ("Hello World")
    }
  }

  val builder = new RouteBuilder {
    interceptSendTo("mock:foo").skipSendToOriginalEndpoint {
      transform("Bye World")
      to("mock:detour")
    }

    "direct:third" ==> {
      to("mock:bar")
      to("mock:foo")
      to("mock:result")
    }
  }
}

/**
 * Scala DSL equivalent for the InterceptSendToFaultTest
 */
class SInterceptSendToFaultTest extends InterceptSendToFaultTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    interceptSendTo("mock:foo").setFaultBody("Damn")

    "direct:start" to "mock:foo" transform "Bye World" to "mock:result"
  }
}
