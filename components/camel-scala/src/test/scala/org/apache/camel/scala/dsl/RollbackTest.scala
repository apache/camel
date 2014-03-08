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
import junit.framework.Assert._

/**
 * Test case for rollback
 */
class RollbackTest extends ScalaTestSupport {
  
  var handled = false
  
  @Test
  def testSimple() {
    test {
      try {
        template.requestBody("direct:a", "The Joker")
        fail("Expected a RollbackExchangeException")
      } catch {
        // oh no, not the Joker again, let's send Batman
        case e: RuntimeCamelException if (e.getCause.isInstanceOf[RollbackExchangeException]) => template.requestBody("direct:a", "Batman")
        case unknown : Throwable => fail("We didn't expect " + unknown)
      }
    }
  }
  
  @Test
  def testBlock() {
    "mock:b" expect { _.count = 2 }
    "mock:ok" expect { _.count = 1 }
    test {
      try {
        template.requestBody("direct:b", "Lex Luthor")
        fail("Expected a RollbackExchangeException")
      } catch {
        // oh no, not Lex Luthor again, let's send Superman
        case e: RuntimeCamelException if (e.getCause.isInstanceOf[RollbackExchangeException]) => template.requestBody("direct:b", "Superman")
        case unknown : Throwable => fail("We didn't expect " + unknown)
      }
    }
  }
    
  val builder =
    new RouteBuilder {
       //START SNIPPET: simple
       "direct:a" to("mock:a") when(_.in != "Batman") rollback
       //END SNIPPET: simple
      
       //START SNIPPET: block
       "direct:b" ==> {
         to("mock:b")
         choice {
           when(_.in != "Superman") {
             rollback
           }
           otherwise to "mock:ok"
         }
       }
       //END SNIPPET: block
    }

}
