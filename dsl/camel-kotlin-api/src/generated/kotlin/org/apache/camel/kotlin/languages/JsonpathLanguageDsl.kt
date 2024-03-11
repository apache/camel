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
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.language.JsonPathExpression

/**
 * Evaluates a JSONPath expression against a JSON message body.
 */
public fun jsonpath(jsonpath: String, i: JsonpathLanguageDsl.() -> Unit = {}): JsonPathExpression {
  val def = JsonPathExpression(jsonpath)
  JsonpathLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class JsonpathLanguageDsl(
  def: JsonPathExpression,
) {
  public val def: JsonPathExpression

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
   * Whether to suppress exceptions such as PathNotFoundException.
   */
  public fun suppressExceptions(suppressExceptions: Boolean) {
    def.suppressExceptions = suppressExceptions.toString()
  }

  /**
   * Whether to suppress exceptions such as PathNotFoundException.
   */
  public fun suppressExceptions(suppressExceptions: String) {
    def.suppressExceptions = suppressExceptions
  }

  /**
   * Whether to allow in inlined Simple exceptions in the JSONPath expression
   */
  public fun allowSimple(allowSimple: Boolean) {
    def.allowSimple = allowSimple.toString()
  }

  /**
   * Whether to allow in inlined Simple exceptions in the JSONPath expression
   */
  public fun allowSimple(allowSimple: String) {
    def.allowSimple = allowSimple
  }

  /**
   * Whether to allow using the easy predicate parser to pre-parse predicates.
   */
  public fun allowEasyPredicate(allowEasyPredicate: Boolean) {
    def.allowEasyPredicate = allowEasyPredicate.toString()
  }

  /**
   * Whether to allow using the easy predicate parser to pre-parse predicates.
   */
  public fun allowEasyPredicate(allowEasyPredicate: String) {
    def.allowEasyPredicate = allowEasyPredicate
  }

  /**
   * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO
   * value.
   */
  public fun writeAsString(writeAsString: Boolean) {
    def.writeAsString = writeAsString.toString()
  }

  /**
   * Whether to write the output of each row/element as a JSON String value instead of a Map/POJO
   * value.
   */
  public fun writeAsString(writeAsString: String) {
    def.writeAsString = writeAsString
  }

  /**
   * Whether to unpack a single element json-array into an object.
   */
  public fun unpackArray(unpackArray: Boolean) {
    def.unpackArray = unpackArray.toString()
  }

  /**
   * Whether to unpack a single element json-array into an object.
   */
  public fun unpackArray(unpackArray: String) {
    def.unpackArray = unpackArray
  }

  /**
   * To configure additional options on JSONPath. Multiple values can be separated by comma.
   */
  public fun option(option: String) {
    def.option = option
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
