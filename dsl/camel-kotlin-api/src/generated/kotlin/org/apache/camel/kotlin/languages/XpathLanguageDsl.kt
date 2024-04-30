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
package org.apache.camel.kotlin.languages

import java.lang.Class
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import kotlin.collections.MutableList
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.PropertyDefinition
import org.apache.camel.model.language.XPathExpression

/**
 * Evaluates an XPath expression against an XML payload.
 */
public fun xpath(xpath: String, i: XpathLanguageDsl.() -> Unit = {}): XPathExpression {
  val def = XPathExpression(xpath)
  XpathLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class XpathLanguageDsl(
  def: XPathExpression,
) {
  public val def: XPathExpression

  init {
    this.def = def
  }

  /**
   * Sets the id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Name of class for document type The default value is org.w3c.dom.Document
   */
  public fun documentType(documentType: Class<*>) {
    def.documentType = documentType
  }

  /**
   * Sets the output type supported by XPath.
   */
  public fun resultQName(resultQName: String) {
    def.resultQName = resultQName
  }

  /**
   * Whether to use Saxon.
   */
  public fun saxon(saxon: Boolean) {
    def.saxon = saxon.toString()
  }

  /**
   * Whether to use Saxon.
   */
  public fun saxon(saxon: String) {
    def.saxon = saxon
  }

  /**
   * References to a custom XPathFactory to lookup in the registry
   */
  public fun factoryRef(factoryRef: String) {
    def.factoryRef = factoryRef
  }

  /**
   * The XPath object model to use
   */
  public fun objectModel(objectModel: String) {
    def.objectModel = objectModel
  }

  /**
   * Whether to log namespaces which can assist during troubleshooting
   */
  public fun logNamespaces(logNamespaces: Boolean) {
    def.logNamespaces = logNamespaces.toString()
  }

  /**
   * Whether to log namespaces which can assist during troubleshooting
   */
  public fun logNamespaces(logNamespaces: String) {
    def.logNamespaces = logNamespaces
  }

  /**
   * Whether to enable thread-safety for the returned result of the xpath expression. This applies
   * to when using NODESET as the result type, and the returned set has multiple elements. In this
   * situation there can be thread-safety issues if you process the NODESET concurrently such as from a
   * Camel Splitter EIP in parallel processing mode. This option prevents concurrency issues by doing
   * defensive copies of the nodes. It is recommended to turn this option on if you are using
   * camel-saxon or Saxon in your application. Saxon has thread-safety issues which can be prevented by
   * turning this option on.
   */
  public fun threadSafety(threadSafety: Boolean) {
    def.threadSafety = threadSafety.toString()
  }

  /**
   * Whether to enable thread-safety for the returned result of the xpath expression. This applies
   * to when using NODESET as the result type, and the returned set has multiple elements. In this
   * situation there can be thread-safety issues if you process the NODESET concurrently such as from a
   * Camel Splitter EIP in parallel processing mode. This option prevents concurrency issues by doing
   * defensive copies of the nodes. It is recommended to turn this option on if you are using
   * camel-saxon or Saxon in your application. Saxon has thread-safety issues which can be prevented by
   * turning this option on.
   */
  public fun threadSafety(threadSafety: String) {
    def.threadSafety = threadSafety
  }

  /**
   * Whether to enable pre-compiling the xpath expression during initialization phase. pre-compile
   * is enabled by default. This can be used to turn off, for example in cases the compilation phase is
   * desired at the starting phase, such as if the application is ahead of time compiled (for example
   * with camel-quarkus) which would then load the xpath factory of the built operating system, and not
   * a JVM runtime.
   */
  public fun preCompile(preCompile: Boolean) {
    def.preCompile = preCompile.toString()
  }

  /**
   * Whether to enable pre-compiling the xpath expression during initialization phase. pre-compile
   * is enabled by default. This can be used to turn off, for example in cases the compilation phase is
   * desired at the starting phase, such as if the application is ahead of time compiled (for example
   * with camel-quarkus) which would then load the xpath factory of the built operating system, and not
   * a JVM runtime.
   */
  public fun preCompile(preCompile: String) {
    def.preCompile = preCompile
  }

  /**
   * Injects the XML Namespaces of prefix - uri mappings
   */
  public fun namespace(namespace: MutableList<PropertyDefinition>) {
    def.namespace = namespace
  }

  /**
   * Source to use, instead of message body. You can prefix with variable:, header:, or property: to
   * specify kind of source. Otherwise, the source is assumed to be a variable. Use empty or null to
   * use default source, which is the message body.
   */
  public fun source(source: String) {
    def.source = source
  }

  /**
   * Sets the class of the result type (type from output)
   */
  public fun resultType(resultType: Class<*>) {
    def.resultType = resultType
  }

  /**
   * Whether to trim the value to remove leading and trailing whitespaces and line breaks
   */
  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  /**
   * Whether to trim the value to remove leading and trailing whitespaces and line breaks
   */
  public fun trim(trim: String) {
    def.trim = trim
  }
}
