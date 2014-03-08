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

class FilterRouteBuilderTest extends ScalaTestSupport {

  @Test
  def testSimpleFilter() {
    "mock:a" expect {_.expectedMessageCount(1)}
    "direct:a" ! ("<hello/>", "<hellos/>")
    "mock:a" assert()
  }

  @Test
  def testFilterWithAlternatives() {
    "mock:b" expect {_.expectedMessageCount(1)}
    "mock:c" expect {_.expectedMessageCount(1)}
    "mock:d" expect {_.expectedMessageCount(3)}
    "mock:e" expect {_.expectedMessageCount(2)}
    "direct:b" ! ("<hello/>", "<hellos/>", "<hallo/>")
    "mock:b" assert()
    "mock:c" assert()
    "mock:d" assert()
    "mock:e" assert()
  }

  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" when(_.in == "<hello/>") to("mock:a")
     //END SNIPPET: simple

     //START SNIPPET: alternatives
     "direct:b" ==> {
       when(_.in == "<hallo/>") {
         --> ("mock:b")
         to ("mock:c")
       } otherwise {
         to ("mock:e")
       }
       to ("mock:d")
     }
     //END SNIPPET: alternatives
   }

}
