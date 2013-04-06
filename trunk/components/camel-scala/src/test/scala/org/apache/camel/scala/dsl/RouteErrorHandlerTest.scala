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
import org.apache.camel.Exchange
import org.junit.Assert._

class RouteErrorHandlerTest extends ScalaTestSupport {

  @Test
  def testRouteHandlerActive() {
    "mock:deadLetter" expect {
      _.expectedMessageCount(1)
    }

    test {
      "direct:hasHandler" ! "hello world"
    }

  }

  @Test
  def testContextHandlerStillActive() {
    try {
      test {
        "direct:noHandler" ! "hello world"
        fail("Default error handler not used.")
      }
    } catch {
      case _:Exception => {} // pass
    }
  }

  val builder = new RouteBuilder {
    "direct:noHandler" process (causeError _) to "mock:noHandler"

    "direct:hasHandler" errorHandler (deadLetterChannel("mock:deadLetter")) process (causeError _) to "mock:hasHandler"
  }

  def causeError(exchange: Exchange) = throw new Exception("Error in route.")
}