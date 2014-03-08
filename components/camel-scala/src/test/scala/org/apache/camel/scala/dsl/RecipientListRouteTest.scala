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
package org.apache.camel.scala
package dsl

import org.junit.Test
import builder.RouteBuilder
import test.{Adult, Toddler, Envelope}
 
class RecipientListRouteTest extends ScalaTestSupport {
  
  @Test
  def testRecipientList() {
    "mock:a" expect {_.count = 1}
    "direct:a" ! ("send this message to mock:a", "send this message to mock:z")
    "mock:a" assert()
  }
  
  @Test
  def testRecipientListWithPatternMatching() {
    "mock:playgarden" expect {_.count = 1}
    "direct:b" ! (new Adult("Gert"), new Toddler("Ewan"))
    "mock:playgarden" assert()    
  }
  
  @Test
  def testRecipientListWithJXPath() {
    "mock:c" expect {_.count = 2}
    "mock:d" expect {_.count = 1}
    "direct:c" ! (new Envelope("mock:d"), new Envelope("mock:y"))
    "mock:c" assert()    
    "mock:d" assert()
  }
   
  val builder = new RouteBuilder {
    //START SNIPPET: simple
    "direct:a" recipients(_.in[String].substring(21))
    //END SNIPPET: simple
      
    //START SNIPPET: pattern 
    "direct:b" recipients(_.getIn.getBody match {
      case Toddler(_) => "mock:playgarden"
      case _ => "mock:work"
    })
    //END SNIPPET: pattern
      
    //START SNIPPET: block
    "direct:c" ==> {
      to("mock:c")
      recipients(jxpath("./in/body/destination"))
    }
    //END SNIPPET: block
  }

}
