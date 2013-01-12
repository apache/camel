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
package org.apache.camel.scala.converter

import javax.xml.parsers.DocumentBuilderFactory
import org.apache.camel.test.junit4.CamelTestSupport

import org.junit.Assert._
import org.junit.Test
import org.w3c.dom.Document

import scala.xml.Elem

/**
 * Test case for ScalaTypeConverter
 */
class ScalaTypeConverterTest extends CamelTestSupport {
  
  @Test
  def testDocumentConverter() {
    val exchange = context.getEndpoint("direct:start").createExchange

    val result = context.getTypeConverter.convertTo(classOf[Document], exchange, <persons/>)
    assertNotNull(result)
    assertNotNull(result.getElementsByTagName("persons"))
  }

  @Test
  def testXmlStringToElemConverter() {
    val exchange = context.getEndpoint("direct:start").createExchange

    val result = context.getTypeConverter.convertTo(classOf[Elem], exchange, "<persons/>")
    assertNotNull(result)
    assertEquals(<persons/>, result)
  }

  @Test
  def testDomDocumentToElemConverter() {
    val exchange = context.getEndpoint("direct:start").createExchange

    val factory = DocumentBuilderFactory.newInstance()
    val parser = factory.newDocumentBuilder()
    val doc = parser.newDocument()
    val root = doc.createElement("persons")
    doc.appendChild(root)

    val result = context.getTypeConverter.convertTo(classOf[Elem], exchange, doc)
    assertNotNull(result)
    assertEquals(<persons/>, result)
  }

  @Test
  def testDomNodeToElemConverter() {
    val exchange = context.getEndpoint("direct:start").createExchange

    val factory = DocumentBuilderFactory.newInstance()
    val parser = factory.newDocumentBuilder()
    val doc = parser.newDocument()
    val node = doc.createElement("persons")

    val result = context.getTypeConverter.convertTo(classOf[Elem], exchange, node)
    assertNotNull(result)
    assertEquals(<persons/>, result)
  }
}
