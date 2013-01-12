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
 * Test case for message resequencer
 */
class ResequencerTest extends ScalaTestSupport {
  
  @Test
  def testSimpleResequencer() {
    "mock:a" expect { _.received("message 1", "message 2", "message 3", "message 4", "message 5") } 
    test {
      "direct:a" ! ("message 5", "message 1", "message 3", "message 2", "message 4")
    }
  }
  
  @Test
  def testBlockResequencer() {
    "mock:b" expect (_.received("message 5", "message 1", "message 3", "message 2", "message 4"))
    "mock:c" expect (_.received("message 1", "message 2", "message 3", "message 4", "message 5"))
    test {
      "direct:b" ! ("message 5", "message 1", "message 3", "message 2", "message 4")
    }
  }
  
  @Test
  def testBatchResequencer() {
    "mock:d" expect (_.received("message 5", "message 1", "message 3", "message 2"))
    "mock:e" expect (_.count = 0)
    test {
      "direct:d" ! ("message 5", "message 1", "message 3", "message 2")
    }
    "mock:d" expect (_.count = 5)
    "mock:e" expect (_.received("message 1", "message 2", "message 3", "message 4", "message 5"))
    test {
      "direct:d" ! "message 4"
    }
  }
    
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" resequence (_.in) to "mock:a"
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         to ("mock:b")
         resequence (_.in) {
           to ("mock:c")
         }
       }
       //END SNIPPET: block
       
       //START SNIPPET: batch
       "direct:d" ==> {
         to ("mock:d")
         resequence(_.in).batch(5) {
           to ("mock:e")
         }
       }
       //END SNIPPET: batch
    }

}
