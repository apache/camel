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

import org.apache.camel.scala.dsl.builder.{RouteBuilderSupport, RouteBuilder}
import processor.OnCompletionWhenTest

class SOnCompletionWhenTest extends OnCompletionWhenTest with RouteBuilderSupport {

  override def createRouteBuilder = new RouteBuilder {

    "direct:start" ==> {
      onCompletion(containsHello(_)) {
        to("mock:sync")
      }
      to("mock:result")
    }

    def containsHello(exchange: Exchange) = exchange.getIn.getBody(classOf[String]).contains("Hello")
  }

}