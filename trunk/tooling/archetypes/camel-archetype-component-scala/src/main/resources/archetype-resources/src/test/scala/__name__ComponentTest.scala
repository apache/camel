## ------------------------------------------------------------------------
## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements.  See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License.  You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.
## ------------------------------------------------------------------------
package ${package}

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.junit4.CamelTestSupport
import org.junit.Test
import org.apache.camel.scala.dsl.builder.{RouteBuilder, RouteBuilderSupport}

// The trait RouteBuilderSupport helps bridge the Scala RouteBuilder with the Java RouteBuilder,
// that the CamelTestSupport expects and uses
class ${name}ComponentTest extends CamelTestSupport with RouteBuilderSupport {

  // Create a simple route that uses both the consumer and producer endpoint of the new component.
  override def createRouteBuilder() = {
    new RouteBuilder() {

      "${scheme}://foo" to "${scheme}://bar" to "mock:result"
    }

  }

  @Test
  def test${name}() = {
    val mock: MockEndpoint = getMockEndpoint("mock:result")
    mock.expectedMinimumMessageCount(1)

    assertMockEndpointsSatisfied()
  }

}
