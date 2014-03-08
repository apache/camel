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

class ContentBasedRouterTest extends ScalaTestSupport {

  @Test
  def testSimpleContentBasedRouter() {
    "mock:polyglot" expect {_.expectedMessageCount(3)}
    "mock:english" expect {_.received("<hello/>")}
    "mock:dutch" expect {_.received("<hallo/>")}
    "mock:german" expect {_.received("<hallo/>")}
    "mock:french" expect {_.received("<hellos/>")}
    "direct:a" ! ("<hello/>", "<hallo/>", "<hellos/>")
    "mock:polyglot" assert()
    "mock:english" assert()
    "mock:dutch" assert()
    "mock:german" assert()
    "mock:french" assert()
  }

  val builder = new RouteBuilder {
     //START SNIPPET: cbr
     "direct:a" ==> {
     to ("mock:polyglot")
     choice {
        when (_.in == "<hello/>") to ("mock:english")
        when (_.in == "<hallo/>") {
          to ("mock:dutch")
          to ("mock:german")
        } 
        otherwise to ("mock:french")
      }
    }
    //END SNIPPET: cbr
  }

}
