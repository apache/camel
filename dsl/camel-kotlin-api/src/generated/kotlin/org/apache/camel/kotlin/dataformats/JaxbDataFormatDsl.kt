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

/**
 * Unmarshal XML payloads to POJOs and back using JAXB2 XML marshalling standard.
 */
public fun DataFormatDsl.jaxb(i: JaxbDataFormatDsl.() -> Unit) {
  def = JaxbDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class JaxbDataFormatDsl {
  public val def: JaxbDataFormat

  init {
    def = JaxbDataFormat()}

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
   * This can be set to true to mark that the contextPath is referring to a classname and not a
   * package name.
   */
  public fun contextPathIsClassName(contextPathIsClassName: Boolean) {
    def.contextPathIsClassName = contextPathIsClassName.toString()
  }

  /**
   * This can be set to true to mark that the contextPath is referring to a classname and not a
   * package name.
   */
  public fun contextPathIsClassName(contextPathIsClassName: String) {
    def.contextPathIsClassName = contextPathIsClassName
  }

  /**
   * To validate against an existing schema. Your can use the prefix classpath:, file: or http: to
   * specify how the resource should be resolved. You can separate multiple schema files by using the
   * ',' character.
   */
  public fun schema(schema: String) {
    def.schema = schema
  }

  /**
   * Sets the schema severity level to use when validating against a schema. This level determines
   * the minimum severity error that triggers JAXB to stop continue parsing. The default value of 0
   * (warning) means that any error (warning, error or fatal error) will trigger JAXB to stop. There
   * are the following three levels: 0=warning, 1=error, 2=fatal error.
   */
  public fun schemaSeverityLevel(schemaSeverityLevel: Int) {
    def.schemaSeverityLevel = schemaSeverityLevel.toString()
  }

  /**
   * Sets the schema severity level to use when validating against a schema. This level determines
   * the minimum severity error that triggers JAXB to stop continue parsing. The default value of 0
   * (warning) means that any error (warning, error or fatal error) will trigger JAXB to stop. There
   * are the following three levels: 0=warning, 1=error, 2=fatal error.
   */
  public fun schemaSeverityLevel(schemaSeverityLevel: String) {
    def.schemaSeverityLevel = schemaSeverityLevel
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  /**
   * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling.
   * This only applies to POJO classes that has not been annotated with JAXB and providing jaxb.index
   * descriptor files.
   */
  public fun objectFactory(objectFactory: Boolean) {
    def.objectFactory = objectFactory.toString()
  }

  /**
   * Whether to allow using ObjectFactory classes to create the POJO classes during marshalling.
   * This only applies to POJO classes that has not been annotated with JAXB and providing jaxb.index
   * descriptor files.
   */
  public fun objectFactory(objectFactory: String) {
    def.objectFactory = objectFactory
  }

  /**
   * Whether to ignore JAXBElement elements - only needed to be set to false in very special
   * use-cases.
   */
  public fun ignoreJAXBElement(ignoreJAXBElement: Boolean) {
    def.ignoreJAXBElement = ignoreJAXBElement.toString()
  }

  /**
   * Whether to ignore JAXBElement elements - only needed to be set to false in very special
   * use-cases.
   */
  public fun ignoreJAXBElement(ignoreJAXBElement: String) {
    def.ignoreJAXBElement = ignoreJAXBElement
  }

  /**
   * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails. This
   * option can be set to false to relax that, such as when the data is already in XML format.
   */
  public fun mustBeJAXBElement(mustBeJAXBElement: Boolean) {
    def.mustBeJAXBElement = mustBeJAXBElement.toString()
  }

  /**
   * Whether marhsalling must be java objects with JAXB annotations. And if not then it fails. This
   * option can be set to false to relax that, such as when the data is already in XML format.
   */
  public fun mustBeJAXBElement(mustBeJAXBElement: String) {
    def.mustBeJAXBElement = mustBeJAXBElement
  }

  /**
   * To ignore non xml characheters and replace them with an empty space.
   */
  public fun filterNonXmlChars(filterNonXmlChars: Boolean) {
    def.filterNonXmlChars = filterNonXmlChars.toString()
  }

  /**
   * To ignore non xml characheters and replace them with an empty space.
   */
  public fun filterNonXmlChars(filterNonXmlChars: String) {
    def.filterNonXmlChars = filterNonXmlChars
  }

  /**
   * To overrule and use a specific encoding
   */
  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  /**
   * To turn on marshalling XML fragment trees. By default JAXB looks for XmlRootElement annotation
   * on given class to operate on whole XML tree. This is useful but not always - sometimes generated
   * code does not have XmlRootElement annotation, sometimes you need unmarshall only part of tree. In
   * that case you can use partial unmarshalling. To enable this behaviours you need set property
   * partClass. Camel will pass this class to JAXB's unmarshaler.
   */
  public fun fragment(fragment: Boolean) {
    def.fragment = fragment.toString()
  }

  /**
   * To turn on marshalling XML fragment trees. By default JAXB looks for XmlRootElement annotation
   * on given class to operate on whole XML tree. This is useful but not always - sometimes generated
   * code does not have XmlRootElement annotation, sometimes you need unmarshall only part of tree. In
   * that case you can use partial unmarshalling. To enable this behaviours you need set property
   * partClass. Camel will pass this class to JAXB's unmarshaler.
   */
  public fun fragment(fragment: String) {
    def.fragment = fragment
  }

  /**
   * Name of class used for fragment parsing. See more details at the fragment option.
   */
  public fun partClass(partClass: String) {
    def.partClass = partClass
  }

  /**
   * XML namespace to use for fragment parsing. See more details at the fragment option.
   */
  public fun partNamespace(partNamespace: String) {
    def.partNamespace = partNamespace
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
   * To use a custom xml stream writer.
   */
  public fun xmlStreamWriterWrapper(xmlStreamWriterWrapper: String) {
    def.xmlStreamWriterWrapper = xmlStreamWriterWrapper
  }

  /**
   * To define the location of the schema
   */
  public fun schemaLocation(schemaLocation: String) {
    def.schemaLocation = schemaLocation
  }

  /**
   * To define the location of the namespaceless schema
   */
  public fun noNamespaceSchemaLocation(noNamespaceSchemaLocation: String) {
    def.noNamespaceSchemaLocation = noNamespaceSchemaLocation
  }

  /**
   * Refers to a custom java.util.Map to lookup in the registry containing custom JAXB provider
   * properties to be used with the JAXB marshaller.
   */
  public fun jaxbProviderProperties(jaxbProviderProperties: String) {
    def.jaxbProviderProperties = jaxbProviderProperties
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }

  /**
   * Only in use if schema validation has been enabled. Restrict access to the protocols specified
   * for external reference set by the schemaLocation attribute, Import and Include element. Examples
   * of protocols are file, http, jar:file. false or none to deny all access to external references; a
   * specific protocol, such as file, to give permission to only the protocol; the keyword all to grant
   * permission to all protocols.
   */
  public fun accessExternalSchemaProtocols(accessExternalSchemaProtocols: String) {
    def.accessExternalSchemaProtocols = accessExternalSchemaProtocols
  }
}
