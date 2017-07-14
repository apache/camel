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

import org.apache.camel.ServiceStatus.Stopped

import org.junit.Test
import org.junit.Assert.assertEquals
import builder.RouteBuilder

class AutoStartupNotReturningRouteDefinitionTest extends ScalaTestSupport {
  @Test
  def testBooleanAutoStartup() {
    assertEquals(context.getRouteStatus("boolean-auto-startup"), Stopped)
  }

  @Test
  def testStringAutoStartup() {
    assertEquals(context.getRouteStatus("string-auto-startup"), Stopped)
  }

  val builder =
    new RouteBuilder {
      // will throw an exception if bug is present
      "boolean-auto-startup" :: "direct:a".autoStartup(false) ==> {
        to ("mock:a")
      }

      // will throw an exception if bug is present
      "string-auto-startup" :: "direct:b".autoStartup("false") ==> {
        to ("mock:b")
      }
    }
}
