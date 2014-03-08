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
package org.apache.camel.scala.dsl

import org.junit.Test
import builder.RouteBuilder
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository
import org.apache.camel.spi.IdempotentRepository
import org.apache.camel.{Processor, Exchange}

/**
 * Test for an idempotent consumer
 */
class IdempotentConsumerTest extends ScalaTestSupport {

  @Test
  def testSimple() {
    doTest("direct:a", "mock:a")
  }

  @Test
  def testBlock() {
    doTest("direct:b", "mock:b")
  }

  def doTest(from: String, to: String) {
    to expect { _.received("message 1", "message 2", "message 3")}
    def send = sendMessage(from, _:String, _:String)
    test {
      send("1", "message 1")
      send("2", "message 2")   
      send("1", "message 1")
      send("2", "message 2")
      send("1", "message 1")
      send("3", "message 3")
    }
  }
  
  def sendMessage(from: String, header: String, body: String) = {
    template.send(from, new Processor() {
      def process(exchange: Exchange) {
        val in = exchange.getIn
        in.setBody(body)
        in.setHeader("messageId", header)
      }
    })
  }

  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" idempotentConsumer(_.in("messageId")) memory(200) to ("mock:a")
     //END SNIPPET: simple
     
     //START SNIPPET: block
     "direct:b" ==> {
       idempotentConsumer(_.in("messageId")) memory(200) apply {
         to ("mock:b")
       }
     }
     //END SNIPPET: block
   }

}

/**
 * Scala DSL equivalent for IdempotentConsumerEagerTest.testEager
 */
class IdempotentConsumerEagerTest extends ScalaTestSupport {

  def testEagerIdempotentConsumer() {
    "mock:result" expect { _.received("one", "two", "three")}
    test {
      sendMessage("1", "one")
      sendMessage("2", "two")
      sendMessage("3", "three")
    }
  }

  def sendMessage(messageId: Any, body: Any) = {
      template.send("direct:start", new Processor() {
          def process(exchange: Exchange) {
              // now lets fire in a message
              val in = exchange.getIn
              in.setBody(body)
              in.setHeader("messageId", messageId)
          }
      })
  }

  val builder = new RouteBuilder {
    val repo : IdempotentRepository[String] = MemoryIdempotentRepository.memoryIdempotentRepository(200)

    "direct:start" ==> {
      idempotentConsumer(_.getIn.getHeader("messageId")).repository(repo).eager(true) {
        process((exchange : Exchange) =>
          if (repo.contains(exchange.getIn.getHeader("messageId").asInstanceOf[String])) {
            // this is OK with the eager = true
          } else {
            throw new RuntimeException("IdemPotentConsumer eager handling is not working properly")
          }
        )
        to("mock:result")
      }
    }
  }
}
