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

import org.apache.camel.ExchangePattern._
import builder.RouteBuilder
import org.junit.Test
import org.junit.Assert.assertEquals


/**
 * Test for setting the ExchangePattern 
 */
class SetExchangePatternTest extends ScalaTestSupport {

  @Test
  def testSimpleInOnly() {
    doTest("direct:a", "mock:a", InOnly)
  }
  @Test
  def testBlockInOnly() {
    doTest("direct:b", "mock:b", InOnly)
  }
  @Test
  def testSimpleInOut() {
    doTest("direct:c", "mock:c", InOut)
  }
  @Test
  def testBlockInOut() {
    doTest("direct:d", "mock:d", InOut)
  }
  
  
  def doTest(from: String, to: String, expected: ExchangePattern) {
    to expect { _.count = 1}
    val exchange = in("MyTestBody")
    expected match {
      case InOut => exchange.setPattern(InOnly)
      case InOnly => exchange.setPattern(InOut)
      case _ => throw new IllegalArgumentException("We only test for InOnly and InOut")
    }
    test {
      from ! exchange
    }
    assertEquals(expected, getMockEndpoint(to).getReceivedExchanges.get(0).getPattern)
  }

  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" inOnly to ("mock:a")
     "direct:c" inOut to ("mock:c")
     //END SNIPPET: simple
     
     //START SNIPPET: block
     "direct:b" ==> {
       inOnly() {
         to ("mock:b")
       }
     }
     "direct:d" ==> {
       inOut() {
         to ("mock:d")
       }
     }
     //END SNIPPET: block
   }

}
