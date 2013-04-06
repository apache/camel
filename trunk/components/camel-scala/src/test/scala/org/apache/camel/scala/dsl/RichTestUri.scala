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

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.Exchange

class RichTestUri(uri: String, support: ScalaTestSupport) {

  def !(messages: Any*) {
    messages.foreach { 
      _ match {
        case exchange: Exchange => support.getTemplate.send(uri, exchange)
        case anything: Any => support.getTemplate.sendBody(uri, anything)
      }
    }
  }

  def !?(message: Any) = {
    message match {
      case processor: Processor => support.getTemplate.request(uri, processor)
      case body : Object => support.getTemplate.requestBody(uri, body)
    }
  }

  def !!(message: Any) = {
    message match {
      case exchange : Exchange => support.getTemplate.asyncSend(uri, exchange)
      case processor : Processor => support.getTemplate.asyncSend(uri, processor)
      case body : Object => support.getTemplate.asyncRequestBody(uri, body)
    }
  }

  def expect(block: MockEndpoint => Unit) {
    val mock = support.mock(uri)
    block(mock)
  }

  def assert() {
    support.mock(uri).assertIsSatisfied()
  }

}
