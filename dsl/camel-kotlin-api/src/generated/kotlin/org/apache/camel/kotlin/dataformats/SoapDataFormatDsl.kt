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

import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.SoapDataFormat

/**
 * Marshal Java objects to SOAP messages and back.
 */
public fun DataFormatDsl.soap(i: SoapDataFormatDsl.() -> Unit) {
  def = SoapDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SoapDataFormatDsl {
  public val def: SoapDataFormat

  init {
    def = SoapDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Package name where your JAXB classes are located.
   */
  public fun contextPath(contextPath: String) {
    def.contextPath = contextPath
  }

  /**
   * To overrule and use a specific encoding
   */
  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  /**
   * Refers to an element strategy to lookup from the registry. An element name strategy is used for
   * two purposes. The first is to find a xml element name for a given object and soap action when
   * marshaling the object into a SOAP message. The second is to find an Exception class for a given
   * soap fault name. The following three element strategy class name is provided out of the box.
   * QNameStrategy - Uses a fixed qName that is configured on instantiation. Exception lookup is not
   * supported TypeNameStrategy - Uses the name and namespace from the XMLType annotation of the given
   * type. If no namespace is set then package-info is used. Exception lookup is not supported
   * ServiceInterfaceStrategy - Uses information from a webservice interface to determine the type name
   * and to find the exception class for a SOAP fault All three classes is located in the package name
   * org.apache.camel.dataformat.soap.name If you have generated the web service stub code with
   * cxf-codegen or a similar tool then you probably will want to use the ServiceInterfaceStrategy. In
   * the case you have no annotated service interface you should use QNameStrategy or TypeNameStrategy.
   */
  public fun elementNameStrategyRef(elementNameStrategyRef: String) {
    def.elementNameStrategyRef = elementNameStrategyRef
  }

  /**
   * SOAP version should either be 1.1 or 1.2. Is by default 1.1
   */
  public fun version(version: String) {
    def.version = version
  }

  /**
   * When marshalling using JAXB or SOAP then the JAXB implementation will automatic assign
   * namespace prefixes, such as ns2, ns3, ns4 etc. To control this mapping, Camel allows you to refer
   * to a map which contains the desired mapping.
   */
  public fun namespacePrefixRef(namespacePrefixRef: String) {
    def.namespacePrefixRef = namespacePrefixRef
  }

  /**
   * To validate against an existing schema. Your can use the prefix classpath:, file: or http: to
   * specify how the resource should be resolved. You can separate multiple schema files by using the
   * ',' character.
   */
  public fun schema(schema: String) {
    def.schema = schema
  }
}
