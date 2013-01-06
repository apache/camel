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

/**
 * Test for looping from the Scala DSL
 */
class LoopTest extends ScalaTestSupport {

  def testSimpleStaticLoop() {
    doTestLoopStatic("direct:a", "mock:a")
  }
  def testSimpleDynamicLoop() {
    doTestLoopDynamic("direct:b", "mock:b")
  }
  def testBlockStaticLoop() {
    doTestLoopStatic("direct:c", "mock:c")
  }
  def testBlockDynamicLoop() {
    doTestLoopDynamic("direct:d", "mock:d")
  }
  
  def doTestLoopStatic(from: String, mock: String) {
    mock expect {_.count = 5}
    test {
      from ! ("ping")
    }    
  }
  
  def doTestLoopDynamic(from: String, mock: String) {
    mock expect {_.count = 5}
    test {
      from ! ("5")
    }    
  }
  
    
  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" loop(5) to "mock:a"
     "direct:b" loop(_.in) to "mock:b"
     //END SNIPPET: simple
     
     //START SNIPPET: block
     "direct:c" ==> {
       loop(5) {
         to("mock:c")
       }
     }
     "direct:d" ==> {
       loop(_.in) {
         to("mock:d")
       }
     }
     //START SNIPPET: block
   }
}


