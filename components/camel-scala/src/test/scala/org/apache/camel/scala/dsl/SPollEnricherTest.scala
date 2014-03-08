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

import builder.{RouteBuilder, RouteBuilderSupport}
import org.apache.camel.processor.enricher.{SampleAggregator, PollEnricherTest}

/**
 * Scala DSL equivalent for the org.apache.camel.processor.enricher.PollEnricherTest
 */
class SPollEnricherTest extends PollEnricherTest with RouteBuilderSupport {

  val strategy = new SampleAggregator

  override def createRouteBuilder = new RouteBuilder {
    "direct:enricher-test-1" pollEnrich("seda:foo1", strategy) to("mock:mock")

    "direct:enricher-test-2" ==> {
      pollEnrich("seda:foo2", strategy, 1000)
      to("mock:mock")
    }

    "direct:enricher-test-3" ==> {
      pollEnrich("seda:foo3", strategy, -1)
      to("mock:mock")
    }

    "direct:enricher-test-4" pollEnrich("seda:foo4", strategy)
  }
}
