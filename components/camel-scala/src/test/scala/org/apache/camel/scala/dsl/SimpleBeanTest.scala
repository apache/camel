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
import org.apache.camel.scala.test.CartoonService
import org.junit.Test

/**
 * Test for bean support in simple Scala DSL expressions
 */
class SimpleBeanTest extends ScalaTestSupport {

  @Test
  def testSimpleObject() {
    "mock:a" expect {_.received("Lucky Luke rides Jolly Jumper")}
    test {
      "direct:a" ! ("Lucky Luke")
    }
  }
  
  @Test
  def testSimpleClass() {
    "mock:b" expect {_.received("Batman drives the batmobile")}
    test {
      "direct:b" ! ("Batman")
    }
  }
  
  @Test
  def testSimpleRef() {
    "mock:c" expect {_.received("Aladin flies a carpet")}
    test {
      "direct:c" ! ("Aladin")
    }
  }
  
  val builder = new RouteBuilder {
     //START SNIPPET: object
     "direct:a" bean(new CartoonService()) to("mock:a")
     //END SNIPPET: object
     
     //START SNIPPET: class
     "direct:b" bean(classOf[CartoonService]) to("mock:b")
     //END SNIPPET: class
     
     //START SNIPPET: ref
     "direct:c" bean("CartoonService") to("mock:c")
     //END SNIPPET: ref
   }
  
   override def createRegistry() = {
     val registry = super.createRegistry()
     registry.bind("CartoonService", new CartoonService())
     registry
   }
}


