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
import org.apache.camel.model.dataformat.Base64DataFormat

/**
 * Encode and decode data using Base64.
 */
public fun DataFormatDsl.base64(i: Base64DataFormatDsl.() -> Unit) {
  def = Base64DataFormatDsl().apply(i).def
}

@CamelDslMarker
public class Base64DataFormatDsl {
  public val def: Base64DataFormat

  init {
    def = Base64DataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * To specific a maximum line length for the encoded data. By default 76 is used.
   */
  public fun lineLength(lineLength: Int) {
    def.lineLength = lineLength.toString()
  }

  /**
   * To specific a maximum line length for the encoded data. By default 76 is used.
   */
  public fun lineLength(lineLength: String) {
    def.lineLength = lineLength
  }

  /**
   * The line separators to use. Uses new line characters (CRLF) by default.
   */
  public fun lineSeparator(lineSeparator: String) {
    def.lineSeparator = lineSeparator
  }

  /**
   * Instead of emitting '' and '/' we emit '-' and '_' respectively. urlSafe is only applied to
   * encode operations. Decoding seamlessly handles both modes. Is by default false.
   */
  public fun urlSafe(urlSafe: Boolean) {
    def.urlSafe = urlSafe.toString()
  }

  /**
   * Instead of emitting '' and '/' we emit '-' and '_' respectively. urlSafe is only applied to
   * encode operations. Decoding seamlessly handles both modes. Is by default false.
   */
  public fun urlSafe(urlSafe: String) {
    def.urlSafe = urlSafe
  }
}
