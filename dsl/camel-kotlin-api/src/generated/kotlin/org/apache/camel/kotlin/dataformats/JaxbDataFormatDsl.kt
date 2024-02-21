/*
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
package org.apache.camel.kotlin.dataformats

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.JaxbDataFormat

public fun DataFormatDsl.jaxb(i: JaxbDataFormatDsl.() -> Unit) {
  def = JaxbDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class JaxbDataFormatDsl {
  public val def: JaxbDataFormat

  init {
    def = JaxbDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun contextPath(contextPath: String) {
    def.contextPath = contextPath
  }

  public fun contextPathIsClassName(contextPathIsClassName: Boolean) {
    def.contextPathIsClassName = contextPathIsClassName.toString()
  }

  public fun contextPathIsClassName(contextPathIsClassName: String) {
    def.contextPathIsClassName = contextPathIsClassName
  }

  public fun schema(schema: String) {
    def.schema = schema
  }

  public fun schemaSeverityLevel(schemaSeverityLevel: Int) {
    def.schemaSeverityLevel = schemaSeverityLevel.toString()
  }

  public fun schemaSeverityLevel(schemaSeverityLevel: String) {
    def.schemaSeverityLevel = schemaSeverityLevel
  }

  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  public fun objectFactory(objectFactory: Boolean) {
    def.objectFactory = objectFactory.toString()
  }

  public fun objectFactory(objectFactory: String) {
    def.objectFactory = objectFactory
  }

  public fun ignoreJAXBElement(ignoreJAXBElement: Boolean) {
    def.ignoreJAXBElement = ignoreJAXBElement.toString()
  }

  public fun ignoreJAXBElement(ignoreJAXBElement: String) {
    def.ignoreJAXBElement = ignoreJAXBElement
  }

  public fun mustBeJAXBElement(mustBeJAXBElement: Boolean) {
    def.mustBeJAXBElement = mustBeJAXBElement.toString()
  }

  public fun mustBeJAXBElement(mustBeJAXBElement: String) {
    def.mustBeJAXBElement = mustBeJAXBElement
  }

  public fun filterNonXmlChars(filterNonXmlChars: Boolean) {
    def.filterNonXmlChars = filterNonXmlChars.toString()
  }

  public fun filterNonXmlChars(filterNonXmlChars: String) {
    def.filterNonXmlChars = filterNonXmlChars
  }

  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  public fun fragment(fragment: Boolean) {
    def.fragment = fragment.toString()
  }

  public fun fragment(fragment: String) {
    def.fragment = fragment
  }

  public fun partClass(partClass: String) {
    def.partClass = partClass
  }

  public fun partNamespace(partNamespace: String) {
    def.partNamespace = partNamespace
  }

  public fun namespacePrefixRef(namespacePrefixRef: String) {
    def.namespacePrefixRef = namespacePrefixRef
  }

  public fun xmlStreamWriterWrapper(xmlStreamWriterWrapper: String) {
    def.xmlStreamWriterWrapper = xmlStreamWriterWrapper
  }

  public fun schemaLocation(schemaLocation: String) {
    def.schemaLocation = schemaLocation
  }

  public fun noNamespaceSchemaLocation(noNamespaceSchemaLocation: String) {
    def.noNamespaceSchemaLocation = noNamespaceSchemaLocation
  }

  public fun jaxbProviderProperties(jaxbProviderProperties: String) {
    def.jaxbProviderProperties = jaxbProviderProperties
  }

  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }

  public fun accessExternalSchemaProtocols(accessExternalSchemaProtocols: String) {
    def.accessExternalSchemaProtocols = accessExternalSchemaProtocols
  }
}
