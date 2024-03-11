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
import org.apache.camel.model.language.JoorExpression

/**
 * Evaluates a jOOR (Java compiled once at runtime) expression.
 */
public fun joor(joor: String, i: JoorLanguageDsl.() -> Unit = {}): JoorExpression {
  val def = JoorExpression(joor)
  JoorLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class JoorLanguageDsl(
  def: JoorExpression,
) {
  public val def: JoorExpression

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
   * Whether the expression should be pre compiled once during initialization phase. If this is
   * turned off, then the expression is reloaded and compiled on each evaluation.
   */
  public fun preCompile(preCompile: Boolean) {
    def.preCompile = preCompile.toString()
  }

  /**
   * Whether the expression should be pre compiled once during initialization phase. If this is
   * turned off, then the expression is reloaded and compiled on each evaluation.
   */
  public fun preCompile(preCompile: String) {
    def.preCompile = preCompile
  }

  /**
   * Whether single quotes can be used as replacement for double quotes. This is convenient when you
   * need to work with strings inside strings.
   */
  public fun singleQuotes(singleQuotes: Boolean) {
    def.singleQuotes = singleQuotes.toString()
  }

  /**
   * Whether single quotes can be used as replacement for double quotes. This is convenient when you
   * need to work with strings inside strings.
   */
  public fun singleQuotes(singleQuotes: String) {
    def.singleQuotes = singleQuotes
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
