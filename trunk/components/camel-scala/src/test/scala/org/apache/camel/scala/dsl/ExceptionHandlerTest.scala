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
import junit.framework.TestCase.assertFalse

/**
 * Test case for exception handler
 */
class ExceptionHandlerTest extends ScalaTestSupport {

  var handled = false

  @Test
  def testTryCatchFinally() {
    "mock:a" expect { _.count = 1 }
    "mock:b" expect { _.count = 1 }
    "mock:c" expect { _.count = 1 }
    try {
      test {
       "direct:a" ! ("any given message", 'Symbol, 256)
      }
    } catch { case _: Throwable => log.info("get the exception here")}
  }

  val builder =
    new RouteBuilder {
       val failingProcessor = (exchange: Exchange) => {
         exchange.in[AnyRef] match {
           case text: String => //graciously do nothing
           case symbol: Symbol => throw new UnsupportedOperationException("We don't know how to deal with this symbolically correct")
           case _ => throw new RuntimeException("Strings are good, the rest is bad")
         }
       }

       val catchProcessor = (exchange: Exchange) => {
          // we shouldn't get any Strings here
          assertFalse(exchange.getIn.getBody.getClass.equals(classOf[String]))
          // the exchange shouldn't have been marked failed
          assertFalse(exchange.isFailed)
       }

       //START SNIPPET: simple
       handle[UnsupportedOperationException] { to ("mock:c") }
       //END SNIPPET: simple

       //START SNIPPET: block
       handle[RuntimeException] {
           process(catchProcessor)
           to ("mock:b")
       }
       //END SNIPPET: block

       "direct:a" process(failingProcessor) to ("mock:a")

    }

}
