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
package scala
package dsl

import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.test.junit4.CamelTestSupport
import builder.{RouteBuilder,RouteBuilderSupport}
import _root_.scala.collection.mutable.ArrayBuffer

abstract class ScalaTestSupport extends CamelTestSupport with RouteBuilderSupport with Preamble {
  
  implicit def stringToUri(uri:String) = new RichTestUri(uri, this)
  implicit def mockWrapper(endpoint: MockEndpoint) = new RichMockEndpoint(endpoint)
  val endpoints = new ArrayBuffer[MockEndpoint]()

  def assert(uri: String) {
    getMockEndpoint(uri).assertIsSatisfied()
  }

  protected[scala] def getTemplate = template

  protected[scala] def mock(uri: String) = {
    val mock = getMockEndpoint(uri)
    endpoints += mock
    mock
  }

  def in(message: Any) : Exchange =  createExchangeWithBody(message)
  
  val builder : RouteBuilder

  /**
   * Creates the route builder, make sure to use lazy modifier, and create a ScalaRouteBuilder instance
   */
  override protected def createRouteBuilder = builder

  override def setUp() {
    super.setUp()
    endpoints.foreach(_.reset())
  }
  
  def test(block : => Unit) {
    block
    assertEndpoints
  }

  def assertEndpoints() {
    endpoints.foreach(_.assertIsSatisfied())
  }

  override def createJndiContext = {
    jndi match {
      case Some(map) => {
        val context = super.createJndiContext
        map.foreach({case (key, value) => context.bind(key, value) })
        context
      }
      case None => super.createJndiContext
    }
  }

  def jndi : Option[Map[String, Any]] = None
}
