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
package org.apache.camel.scala.dsl;

class FilterRouteBuilderTest extends ScalaTestSupport {

  def testSimpleFilter() = {
    "mock:a" expect {_.expectedMessageCount(1)}
    "direct:a" ! ("<hello/>", "<hellos/>")
    "mock:a" assert
  }

  def testFilterWithAlternatives() = {
    "mock:b" expect {_.expectedMessageCount(1)}
    "mock:b" expect {_.expectedMessageCount(1)}
    "mock:d" expect {_.expectedMessageCount(2)}
    "mock:e" expect {_.expectedMessageCount(0)}
    "mock:f" expect {_.expectedMessageCount(2)}
    "direct:b" ! ("<hello/>", "<hellos/>")
    "mock:b" assert()
    "mock:c" assert()
    "mock:d" assert()
    "mock:e" assert()
    "mock:f" assert()
  }

  override protected def createRouteBuilder() =
    new RouteBuilder {
       "direct:a" when(_.in == "<hello/>") to "mock:a"

       "direct:b" ==> {
         when(_.in == "<hello/>") then {
           to ("mock:b")
           --> ("mock:c")
         }
         when(_.in == "<hallo/>") {
           to ("mock:e")
         } otherwise {
           to ("mock:f")
         }
         to ("mock:d")
       }
    }.print

}
