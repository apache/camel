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
 
import scala.builder.RouteBuilder

class ExplicitMulticastTest extends ScalaTestSupport {

  def testExplicitMulticast = {
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
