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
import org.junit.Test
import org.junit.Assert._

/**
 * Test case for try (attempt) - catch (handle) - finally (ensure)
 */
class TryCatchFinallyTest extends ScalaTestSupport {
  
  var handled = false
  
  @Test
  def testTryCatchFinally() {
    "mock:a" expect { _.count = 1 }
    "mock:b" expect { _.count = 1 }
    "mock:c" expect { _.count = 2 }
    test {
       "direct:a" ! ("any given message", 256) 
    }
  }
    
  val builder =
    new RouteBuilder {
       val failingProcessor = (exchange: Exchange) => {
         exchange.in match {
           case text: String => //graciously do nothing
           case _ => throw new RuntimeException("Strings are good, the rest is bad")
         }
       }
       
       val catchProcessor = (exchange: Exchange) => {
          // we shouldn't get any Strings here
          assertFalse(exchange.getIn.getBody.getClass.equals(classOf[String]))
          // the exchange shouldn't have been marked failed
          assertFalse(exchange.isFailed)
       }
              
       //START SNIPPET: block}
       "direct:a" ==> {
         attempt {
           process(failingProcessor)
           to ("mock:a")
         } handle(classOf[Exception]) apply {
           process(catchProcessor)
           to ("mock:b")
         } ensure {
           to ("mock:c")
         }
       }
       //END SNIPPET: block
    }

}
