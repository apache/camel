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
 
import org.apache.camel.scala.test.Adult
import org.w3c.dom.Document
import scala.dsl.builder.RouteBuilder

/**
 * Test case for wiretap
 */
class WiretapTest extends ScalaTestSupport {
  
  def testSimpleTap = doTestWiretap("direct:a", "mock:a")
  def testBlockTap = doTestWiretap("direct:b", "mock:b")
  
  def doTestWiretap(from: String, to: String) = {
    to expect { _.received("Calling Elvis", "Calling Paul")}
    "mock:tap" expect { _.received("Elvis is alive!", "Stop singing, you're not Elvis")}
    test {
      from ! (Adult("Elvis"), Adult("Paul"))
    }
  }
  
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" wiretap("direct:tap") setbody("Calling " + _.in(classOf[Adult]).name) to ("mock:a")
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         wiretap("direct:tap")
         setbody("Calling " + _.in(classOf[Adult]).name)
         to ("mock:b")
       }
       //END SNIPPET: block
       
       "direct:tap" setbody(_.in match {
          case Adult("Elvis") => "Elvis is alive!"
          case Adult(_) => "Stop singing, you're not Elvis"
        }) to "mock:tap"
    }

}
