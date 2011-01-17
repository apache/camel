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
package org.apache.camel.scala.converter;

import _root_.scala.xml.Elem

import org.apache.camel.Converter
import org.apache.camel.converter.jaxp.XmlConverter
import scala.xml.XML
import org.w3c.dom.Document
import org.w3c.dom.Node

/**
 * Converter implementation for supporting some common Scala types within Apache Camel
 */
@Converter object ScalaTypeConverter {
  
   val converter = new XmlConverter()
  
   @Converter
   def convertToDocument(xml: Elem) = converter.toDOMDocument(xml.toString)

   @Converter
   def convertToElem(xmlString: String) = XML.loadString(xmlString)

   @Converter
   def domDocumentToElem(doc: Document) = XML.load(converter.toInputStream(doc))

   @Converter
   def domNodeToElem(node: Node) = XML.loadString(converter.toString(node, null))
}
