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
package org.apache.camel.scala.example

import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test

// START SNIPPET: e1
// we want to use the Camel test kit to test the FilterRouteExample which we can do by extending
// the CamelTestSupport class from camel-test
class FilterRouteTest extends CamelTestSupport {

  // then override this method to provide the RouteBuilder instance from camel-core
  override def createRouteBuilder() = new FilterRoute().createMyFilterRoute.builder

  // and here we just have regular JUnit test method which uses the API from camel-test

  @Test
  def testFilterRouteGold() {
    getMockEndpoint("mock:gold").expectedMessageCount(1)

    template.sendBodyAndHeader("direct:start", "Hello World", "gold", "true")

    assertMockEndpointsSatisfied()
  }

  @Test
  def testFilterRouteNotGold() {
    getMockEndpoint("mock:gold").expectedMessageCount(0)

    template.sendBodyAndHeader("direct:start", "Hello World", "gold", "false")

    assertMockEndpointsSatisfied()
  }

}
// END SNIPPET: e1
