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
package org.apache.camel
package scala.dsl

import org.junit.Test
import builder.{RouteBuilderSupport, RouteBuilder}
import org.apache.camel.processor.MulticastParallelTest

class ExplicitMulticastTest extends ScalaTestSupport {

  @Test
  def testExplicitMulticast() {
    "mock:a" expect { _.count = 3 }
    "mock:b" expect { _.count = 3 }
    "mock:c" expect { _.count = 3 }
    "direct:a" ! ("<hello/>", "<hallo/>", "<bonjour/>")
    "mock:a" assert()
    "mock:b" assert()
    "mock:c" assert()
  }

  val builder = new RouteBuilder {
    // START SNIPPET: multicast
    "direct:a" ==> {
      multicast {
        to ("mock:a")
        to ("mock:b")
        to ("mock:c")
      }
    }
    // END SNIPPET: multicast
  }

}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.MulticastParallelTest
 */
class SMulticastParallelTest extends MulticastParallelTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {

    val appendBodies = (oldExchange: Exchange, newExchange: Exchange) => {
      if (oldExchange == null) {
        newExchange
      } else {
        oldExchange.in = oldExchange.in[String] + newExchange.in[String]
        oldExchange
      }
    }

    "direct:start" ==> {
      multicast.strategy(appendBodies).parallelProcessing {
        to("direct:a")
        to("direct:b")
      }
      to("mock:result")
    }

    "direct:a" delay(100 ms) setBody("A")
    "direct:b" setBody("B")
  }
}
