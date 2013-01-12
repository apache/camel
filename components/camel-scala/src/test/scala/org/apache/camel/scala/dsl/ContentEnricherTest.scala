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

import org.junit.Test
import builder.RouteBuilder

class ContentEnricherTest extends ScalaTestSupport {

  @Test
  def testContentEnricherBySimpleProcessor() {
    "mock:a" expect { _.received ("James says hello", 
                                  "Hadrian says hello",
                                  "Willem says hello")}
    "direct:a" ! ("James", "Hadrian", "Willem")
    "mock:a" assert()
  }
  
  @Test
  def testContentEnricherByProcessorDef() {
    "mock:b" expect { _.received ("hello from the UK", 
                                  "hallo vanuit Belgie",
                                  "bonjour de la douce France")}
    "direct:b" ! ("hello", "hallo", "bonjour")
    "mock:b" assert()
  }
  
  @Test
  def testContentEnricherWithVelocity() {
    "mock:c" expect { _.received ("<hello>James</hello>", 
                                  "<hello>Hadrian</hello>",
                                  "<hello>Willem</hello>")}
    "direct:c" ! ("James", "Hadrian", "Willem")
    "mock:c" assert()
  }

  val builder = new RouteBuilder {
    // START SNIPPET: simple
    "direct:a" process(_.in += " says hello") to ("mock:a")
    // END SNIPPET: simple
    
    // START SNIPPET: def
    val myProcessor = (exchange: Exchange) => {
      exchange.in match {
        case "hello" => exchange.in = "hello from the UK"
        case "hallo" => exchange.in = "hallo vanuit Belgie"
        case "bonjour" => exchange.in = "bonjour de la douce France"
      }
    }    

    "direct:b" process(myProcessor) to ("mock:b")
    // END SNIPPET: def
    
    // START SNIPPET: velocity
    "direct:c" to ("velocity:org/apache/camel/scala/dsl/enricher.vm") to ("mock:c")   
    // END SNIPPET: velocity

  }

}
