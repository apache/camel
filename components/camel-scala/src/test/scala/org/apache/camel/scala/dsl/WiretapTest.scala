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
 
import org.apache.camel.scala.test.Adult
import builder.RouteBuilder
import org.junit.Test

/**
 * Test case for wiretap
 */
class WiretapTest extends ScalaTestSupport {
  
  @Test
  def testSimpleTap() {
    doTestWiretap("direct:a", "mock:a")
  }

  @Test
  def testBlockTap() {
    doTestWiretap("direct:b", "mock:b")
  }
  
  @Test
  def testSimpleTapWithBody() {
    doTestWiretapWithBody("direct:c", "mock:c")
  }

  @Test
  def testBlockTapWithBody() {
    doTestWiretapWithBody("direct:d", "mock:d")
  }


  @Test
  def testBuilderWiretap() {
    doTestWiretap("direct:e", "mock:e")
  }
  
  def doTestWiretap(from: String, to: String) {
    to expect { _.received("Calling Elvis", "Calling Paul")}
    mock("mock:tap").expectedBodiesReceivedInAnyOrder("Elvis is alive!", "Stop singing, you're not Elvis")
    test {
      from ! (Adult("Elvis"), Adult("Paul"))
    }
  }
  
  def doTestWiretapWithBody(from: String, to: String) {
    to expect { _.received(Adult("Elvis"), Adult("Paul"))}
    mock("mock:tap-with-body").expectedBodiesReceived("Tapped!", "Tapped!")
    test {
      from ! (Adult("Elvis"), Adult("Paul"))
    }
  }
  
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" wireTap("direct:tap") setBody("Calling " + _.in[Adult].name) to ("mock:a")
       "direct:c" wireTap("direct:tap-with-body", "Tapped!") to ("mock:c")
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         wireTap("direct:tap")
         setBody("Calling " + _.in[Adult].name)
         to ("mock:b")
       }
       "direct:d" ==> {
         wireTap("direct:tap-with-body", "Tapped!")
         to ("mock:d")
       }
       //END SNIPPET: block
       
       "direct:tap" setBody(_.in match {
          case Adult("Elvis") => "Elvis is alive!"
          case Adult(_) => "Stop singing, you're not Elvis"
        }) to "mock:tap"
       
       "direct:tap-with-body" to "mock:tap-with-body"

       from("direct:e").wireTap("direct:tap").setBody("Calling " + _.in[Adult].name).to("mock:e")
    }

}
