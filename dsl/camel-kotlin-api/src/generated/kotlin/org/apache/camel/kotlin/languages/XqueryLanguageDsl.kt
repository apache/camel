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
import org.apache.camel.model.language.XQueryExpression

/**
 * Evaluates an XQuery expressions against an XML payload.
 */
public fun xquery(xquery: String, i: XqueryLanguageDsl.() -> Unit = {}): XQueryExpression {
  val def = XQueryExpression(xquery)
  XqueryLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class XqueryLanguageDsl(
  def: XQueryExpression,
) {
  public val def: XQueryExpression

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
   * Reference to a saxon configuration instance in the registry to use for xquery (requires
   * camel-saxon). This may be needed to add custom functions to a saxon configuration, so these custom
   * functions can be used in xquery expressions.
   */
  public fun configurationRef(configurationRef: String) {
    def.configurationRef = configurationRef
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
