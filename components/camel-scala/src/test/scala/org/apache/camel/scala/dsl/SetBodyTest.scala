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
 
import builder.RouteBuilder
import org.apache.camel.scala.test.Cat

/**
 * Test for setting the message body from the Scala DSL
 */
class SetBodyTest extends ScalaTestSupport {

  def testSimpleSetBody() = doTestConstant("direct:a", "mock:a")
  def testBlockSetBody() = doTestConstant("direct:b", "mock:b")
  
  def testSimpleExpression() = doTestExpression("direct:c", "mock:c")
  def testBodyExpression() = doTestExpression("direct:d", "mock:d")
  
  
  def doTestConstant(from: String, mock: String) = {
    mock expect {_.received("pong")}
    test {
      from ! ("ping")
    }    
  }
  
  def doTestExpression(from: String, mock: String) = {
    mock expect {_.received("Duchess", "Toulouse")}
    test {
      from ! (new Cat("Duchess"), new Cat("Toulouse"))
    }    
  }
    
  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" setbody "pong" to "mock:a"
     "direct:c" setbody el("${in.body.name}") to "mock:c"
     //END SNIPPET: simple
     
     //START SNIPPET: block
     "direct:b" ==> {
       setbody("pong")
       to ("mock:b")
     }
     
     "direct:d" ==> {
       setbody(el("${in.body.name}"))
       to ("mock:d")
     }
     //END SNIPPET: block
     
   }
}


