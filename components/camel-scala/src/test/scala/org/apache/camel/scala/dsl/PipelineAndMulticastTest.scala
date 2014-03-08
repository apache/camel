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
 
import builder.RouteBuilder
import org.junit.Test

class PipelineAndMulticastTest extends ScalaTestSupport {

  @Test
  def testArrowRoute() {
    testRoute("direct:a", "mock:c", "mock:a", "mock:b")
  }

  @Test
  def testToRoute() {
    testRoute("direct:d", "mock:f", "mock:d", "mock:e")
  }

  @Test
  def testArrowBlockRoute() {
    testRoute("direct:g", "mock:i", "mock:g", "mock:h")
  }

  @Test
  def testToBlockRoute() {
    testRoute("direct:j", "mock:l", "mock:j", "mock:k")
  }

  def testRoute(from: String, end: String, multis: String*) {
    multis.foreach ( _.expect { _.received("<hello/>")})
    end expect { _.received("<olleh/>")}

    val exchange = in("<hello/>")
    exchange.out = "<olleh/>"
    getTemplate.send(from, exchange)

    multis.foreach( _.assert())
    end assert()
  }


  val builder = new RouteBuilder {
    // START SNIPPET: simple
    "direct:a" --> ("mock:a", "mock:b") --> "mock:c"
    "direct:d" to ("mock:d", "mock:e") to "mock:f"
    // END SNIPPET: simple
      
    // START SNIPPET: block
    "direct:g" ==> {
      --> ("mock:g", "mock:h")
      --> ("mock:i")
    }
    "direct:j" ==> {
      to ("mock:j", "mock:k")
      to ("mock:l")
    }
    // END SNIPPET: block
  }

}
