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

import org.apache.camel.test.junit4.CamelTestSupport
import org.apache.camel.scala.dsl.builder.{ScalaRouteBuilder, RouteBuilder, RouteBuilderSupport}
import org.junit.Test

class CamelCBRTest extends CamelTestSupport with RouteBuilderSupport {

  override protected def createRouteBuilder = {
    builder
  }

  @Test
  def testFoo() {
    getMockEndpoint("mock:foo").expectedMessageCount(1)
    getMockEndpoint("mock:other").expectedMessageCount(0)
    getMockEndpoint("mock:end").expectedMessageCount(1)
    template.sendBody("direct:start", "foo")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testOther() {
    getMockEndpoint("mock:foo").expectedMessageCount(0)
    getMockEndpoint("mock:other").expectedMessageCount(1)
    getMockEndpoint("mock:end").expectedMessageCount(1)

    template.sendBody("direct:start", "bar")

    assertMockEndpointsSatisfied()
  }

  lazy val builder = new ScalaRouteBuilder(context()) {
    "direct:start" ==> {
      choice {
        when(simple("${body} == 'foo'")) to "mock:foo"
        otherwise to "mock:other"
      }
      to("mock:end")
    }

  }

}