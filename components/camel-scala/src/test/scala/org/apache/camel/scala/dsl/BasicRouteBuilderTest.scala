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

import org.apache.camel.scala.ScalaTestSupport
import scala.builder.RouteBuilder

class BasicRouteBuilderTest extends ScalaTestSupport {

  def testBasicRouteArrowSyntax() = assertBasicRoute("direct:a", "mock:a")
  def testBasicRouteTextSyntax() = assertBasicRoute("direct:b", "mock:b")

  def assertBasicRoute(from: String, to: String) = {
    to expect {
      _.expectedMessageCount(1)
    }
    from ! "<hello/>"
    to assert
  }

  val builder = new MyRouteBuilder
  
  //START SNIPPET: basic
  class MyRouteBuilder extends RouteBuilder {
    "direct:a" --> "mock:a"
    "direct:b" to "mock:b"      
  }
  //END SNIPPET: basic
  
}
