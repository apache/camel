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
import org.apache.camel.processor.LogProcessorTest
import org.apache.camel.LoggingLevel

/**
 * Scala DSL equivalent for the org.apache.camel.processor.LogProcessorTest
 */
class SLogProcessorTest extends LogProcessorTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {
    "foo" :: "direct:foo" log("Got ${body}") to("mock:foo")

    "bar" :: "direct:bar" log(LoggingLevel.WARN, "Also got ${body}") to("mock:bar")

    "baz" :: "direct:baz" ==> {
      log(LoggingLevel.ERROR, "cool", "Me got ${body}")
      to("mock:baz")
    }

    "wombat" :: "direct:wombat" ==> {
      // log(LoggingLevel.INFO, "cool", "mymarker", "Me got ${body}")
      to("mock:wombat")
    }

    "nolog" :: "direct:nolog" ==> {
      log(LoggingLevel.TRACE, "Should not log ${body}")
      to("mock:bar")
    }
  }
}
