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

import builder.RouteBuilder
import org.junit.Test

class PipelineRouteBuilderTest extends ScalaTestSupport {
  @Test
  def testPipelineWithArrows() {
    testPipeline("direct:a", "mock:a", "mock:b")
  }

  @Test
  def testPipelineWithTos() {
    testPipeline("direct:c", "mock:c", "mock:d")
  }

  @Test
  def testPipelineBlockWithArrows() {
    testPipeline("direct:e", "mock:e", "mock:f")
  }

  @Test
  def testPipelineBlockWithTos() {
    testPipeline("direct:g", "mock:g", "mock:h")
  }

  def testPipeline(from: String, to: String*) {
    to.foreach {
      _.expect { _.expectedMessageCount(1) }
    }
    from ! "<hello/>"
    to.foreach {
      _.assert()
    }
  }

  val builder = new RouteBuilder {
     //START SNIPPET: simple
     "direct:a" --> "mock:a" --> "mock:b"
     "direct:c" to "mock:c" to "mock:d"
     //END SNIPPET: simple

     //START SNIPPET: block
     "direct:e" ==> {
       --> ("mock:e")
       --> ("mock:f")
     }

     "direct:g" ==> {
       to ("mock:g")
       to ("mock:h")
     }
     //END SNIPPET: block
  }

}
