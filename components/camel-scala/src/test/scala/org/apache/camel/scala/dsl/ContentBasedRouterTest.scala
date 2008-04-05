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

class ContentBasedRouterTest extends ScalaTestSupport {

  def testSimpleContentBasedRouter = {
    "mock:a" expect {_.expectedMessageCount(3)}
    "mock:b" expect {_.received("<hello/>")}
    "mock:c" expect {_.received("<hallo/>")}
    "mock:d" expect {_.received("<hellos/>")}
    "direct:a" ! ("<hello/>", "<hallo/>", "<hellos/>")
    "mock:a" assert()
    "mock:b" assert()
    "mock:c" assert()
    "mock:d" assert()
  }

  override protected def createRouteBuilder() =
    new RouteBuilder {
      "direct:a" ==> {
        to ("mock:a")
        choice {
          when (_.in == "<hello/>") to ("mock:b")
          when (_.in == "<hallo/>") to ("mock:c")
          otherwise to ("mock:d")
        }
      }
    }.print

}
