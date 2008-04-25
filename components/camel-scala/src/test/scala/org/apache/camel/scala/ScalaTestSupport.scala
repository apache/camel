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
package org.apache.camel.scala;

import org.apache.camel.ContextTestSupport
import org.apache.camel.component.mock.MockEndpoint
import builder.RouteBuilderSupport
import org.apache.camel.scala.dsl._

abstract class ScalaTestSupport extends ContextTestSupport with RouteBuilderSupport with Preamble {

  implicit def stringToUri(uri:String) = new RichTestUri(uri, this)
  implicit def mockWrapper(endpoint: MockEndpoint) = new RichMockEndpoint(endpoint)

  def assert(uri: String) = getMockEndpoint(uri).assertIsSatisfied

  protected[scala] def getTemplate() = template

  protected[scala] def mock(uri: String) = getMockEndpoint(uri)

  def in(message: Any) : Exchange = {
    val exchange = createExchangeWithBody(message)
    println(exchange)
    exchange
  }

}
