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
 
import builder.RouteBuilder
import org.junit.Test

/**
 * Test case for message throttler
 */
class ThrottlerTest extends ScalaTestSupport {
  
  @Test
  def testSimpleThrottler() {
    "mock:a" expect { _.count = 3 } 
    "mock:a" expect { _.setResultWaitTime(1500) }
    for (id <- 1 to 6) "seda:a" ! id   
    "mock:a" assert()
  }
 
  @Test
  def testBlockThrottler() {
    "mock:b" expect { _.count = 6 }
    "mock:c" expect { _.count = 3 } 
    "mock:c" expect { _.setResultWaitTime(1500) }
    for (id <- 1 to 6) "seda:b" ! id   
    "mock:b" assert()
  }
  
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "seda:a" throttle (3 per (2 seconds)) to ("mock:a")
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "seda:b" ==> {
         to ("mock:b")
         throttle (3 per (2 seconds)) {
           to ("mock:c")
         }
       }
       //END SNIPPET: block
    }

}
