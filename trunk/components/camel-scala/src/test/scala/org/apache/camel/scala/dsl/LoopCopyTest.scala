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
 * Test for looping from the Scala DSL
 */
class LoopCopyTest extends ScalaTestSupport {

  @Test
  def testLoopCopy() {
      getMockEndpoint("mock:loop").expectedBodiesReceived("AB", "AB", "AB")
      getMockEndpoint("mock:result").expectedBodiesReceived("AB")

      template.sendBody("direct:start", "A")

      assertMockEndpointsSatisfied()
  }

  val builder = new RouteBuilder {
     "direct:start" ==> {
       loop(3).copy() {
         transform(simple("${body}B"))
         to("mock:loop")
       }
       to("mock:result")
     }
   }

}


