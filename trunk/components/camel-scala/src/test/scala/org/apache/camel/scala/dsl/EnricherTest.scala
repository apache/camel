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

/**
 * Test case for message enricher
 */
class EnricherTest extends ScalaTestSupport {
  
  @Test
  def testSimpleEnricher() {
    testEnricher("direct:a", "mock:a")
  }

  @Test
  def testBlockEnricher() {
    testEnricher("direct:b", "mock:b")
  }
  
  def testEnricher(direct: String, mock: String) {
    mock expect { _.received ("France:Paris", "UK:London") }      
    direct ! ("France", "UK")   
    mock assert()
  }
  
  val builder =
    new RouteBuilder {
       val strategy = new org.apache.camel.processor.enricher.SampleAggregator()

       //START SNIPPET: simple
       "direct:a" enrich("direct:enrich", strategy) to ("mock:a")
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         enrich("direct:enrich", strategy)
         to("mock:b")
       }
       //END SNIPPET: block
       
       "direct:enrich" process((exchange:Exchange) => {
         exchange.out = exchange.in match {
           case "UK" => "London"
           case "France" => "Paris"
         }
       })
  }
}
