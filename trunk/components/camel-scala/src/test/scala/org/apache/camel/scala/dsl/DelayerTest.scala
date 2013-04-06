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
 * Test case for message delayer
 */
class DelayerTest extends ScalaTestSupport {
  
  @Test
  def testSimpleDelayer() {
    "mock:a" expect { _.count = 0 }      
    "seda:a" ! "any given message"   
    "mock:a" assert()
    //messages should only arrive after waiting a while 
    "mock:a" expect { _.count = 1 }
    "mock:a" expect { _.setResultWaitTime(2500)}
    "mock:a" assert()
  }

  @Test
  def testBlockDelayer() {
    "mock:b" expect { _.count = 1 }
    "mock:c" expect { _.count = 0 }
    "seda:b" ! "any given message"   
    "mock:b" assert()
    "mock:c" assert()
    //messages should only arrive after waiting a while 
    "mock:c" expect { _.count = 1 }
    "mock:c" expect { _.setResultWaitTime(2500)}
    "mock:b" assert()
    "mock:c" assert()
  }    
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "seda:a" delay(1 seconds) to ("mock:a")
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "seda:b" ==> {
         to ("mock:b")
         delay(1 seconds) {
           to ("mock:c")
         }
       }
       //END SNIPPET: block
    }

}
