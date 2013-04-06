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

import java.io.InputStream

import scala.xml.Elem
import scala.xml.XML

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.apache.camel.{Exchange, Converter}

/**
 * Converter implementation for supporting some common Scala types within Apache Camel
 */
@Converter
object ScalaTypeConverter {

   @Converter
   def toString(symbol: Symbol): String = symbol.name

   @Converter
   def toSymbol(string: String): Symbol = Symbol(string)

   @Converter
   def convertToDocument(xml: Elem, exchange : Exchange) : Document = {
     exchange.getContext.getTypeConverter.convertTo(classOf[Document], exchange, xml.toString())
   }

   @Converter
   def convertToElem(xmlString: String) : Elem = {
     XML.loadString(xmlString)
   }

   @Converter
   def domDocumentToElem(doc: Document, exchange : Exchange) : Elem = {
     XML.load(exchange.getContext.getTypeConverter.convertTo(classOf[InputStream], exchange, doc))
   }

   @Converter
   def domNodeToElem(node: Node, exchange : Exchange) : Elem = {
     XML.loadString(exchange.getContext.getTypeConverter.convertTo(classOf[String], exchange, node))
   }
}
