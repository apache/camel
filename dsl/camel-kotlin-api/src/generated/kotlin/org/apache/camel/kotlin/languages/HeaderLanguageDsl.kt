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

import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.model.language.HeaderExpression

public fun `header`(`header`: String, i: HeaderLanguageDsl.() -> Unit = {}): HeaderExpression {
  val def = HeaderExpression(header)
  HeaderLanguageDsl(def).apply(i)
  return def
}

@CamelDslMarker
public class HeaderLanguageDsl(
  def: HeaderExpression,
) {
  public val def: HeaderExpression

  init {
    this.def = def
  }

  public fun id(id: String) {
    def.id = id
  }

  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  public fun trim(trim: String) {
    def.trim = trim
  }
}
