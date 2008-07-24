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
 
import org.w3c.dom.Document
import scala.dsl.builder.RouteBuilder

/**
 * Test case for message aggregator
 */
class AggregatorTest extends ScalaTestSupport {
  
  val count = 100
  
  def testSimpleAggregator = {
    "mock:a" expect { _.received("message " + count) } 
    test {
      for (i <- 1 to count) {
        "direct:a" ! ("message " + i)
      }
    }
  }
  
 def testBlockAggregator = {
    "mock:b" expect { _.received("message " + count) } 
    test {
      for (i <- 1 to count) {
        "direct:b" ! ("message " + i)
      }
    }
  }
  
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" aggregate (_.in(classOf[String]).substring(0, 7)) to "mock:a"
       //END SNIPPET: simple
       
       //START SNIPPET: block
       "direct:b" ==> {
         aggregate(_.in(classOf[String]).substring(0,7)) {
           to ("mock:b")
         }
       }
       //END SNIPPET: block
    }  
}
