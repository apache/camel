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
import builder.RouteBuilder

/**
 * Content Based Router test with XPath expressions
 */
class HeaderContentBasedRouterTest extends ScalaTestSupport {

  @Test
  def testXPathContentBasedRouter() {
    "mock:foo" expect {_.expectedBodiesReceived("Hello Foo")}
    "mock:bar" expect {_.expectedBodiesReceived("Hello Bar")}
    "mock:other" expect {_.expectedBodiesReceived("Hello World")}

    test {
      template.sendBodyAndHeader("direct:a", "Hello Foo", "foo", 123)
      template.sendBodyAndHeader("direct:a", "Hello Bar", "bar", 456)
      template.sendBody("direct:a", "Hello World")
    }

    assertMockEndpointsSatisfied()
  }

  val builder = new RouteBuilder {
     //START SNIPPET: cbr
     "direct:a" ==> {
     choice {
        when (header("foo")) to ("mock:foo")
        when (header("bar")) to ("mock:bar")
        otherwise to ("mock:other")
      }
    }
    //END SNIPPET: cbr
  }

}