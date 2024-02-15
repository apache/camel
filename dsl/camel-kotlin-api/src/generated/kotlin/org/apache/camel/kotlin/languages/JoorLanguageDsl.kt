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
import kotlin.Any
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.language.JoorExpression

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

  public fun id(id: String) {
    def.id = id
  }

  public fun preCompile(preCompile: Boolean) {
    def.preCompile = preCompile.toString()
  }

  public fun preCompile(preCompile: String) {
    def.preCompile = preCompile
  }

  public fun singleQuotes(singleQuotes: Boolean) {
    def.singleQuotes = singleQuotes.toString()
  }

  public fun singleQuotes(singleQuotes: String) {
    def.singleQuotes = singleQuotes
  }

  public fun resultType(resultType: Class<out Any>) {
    def.resultType = resultType
  }

  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  public fun trim(trim: String) {
    def.trim = trim
  }
}
