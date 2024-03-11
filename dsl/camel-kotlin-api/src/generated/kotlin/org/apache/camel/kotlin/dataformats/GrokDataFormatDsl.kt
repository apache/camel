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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.GrokDataFormat

/**
 * Unmarshal unstructured data to objects using Logstash based Grok patterns.
 */
public fun DataFormatDsl.grok(i: GrokDataFormatDsl.() -> Unit) {
  def = GrokDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class GrokDataFormatDsl {
  public val def: GrokDataFormat

  init {
    def = GrokDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The grok pattern to match lines of input
   */
  public fun pattern(pattern: String) {
    def.pattern = pattern
  }

  /**
   * Turns on flattened mode. In flattened mode the exception is thrown when there are multiple
   * pattern matches with same key.
   */
  public fun flattened(flattened: Boolean) {
    def.flattened = flattened.toString()
  }

  /**
   * Turns on flattened mode. In flattened mode the exception is thrown when there are multiple
   * pattern matches with same key.
   */
  public fun flattened(flattened: String) {
    def.flattened = flattened
  }

  /**
   * If false, every line of input is matched for pattern only once. Otherwise the line can be
   * scanned multiple times when non-terminal pattern is used.
   */
  public fun allowMultipleMatchesPerLine(allowMultipleMatchesPerLine: Boolean) {
    def.allowMultipleMatchesPerLine = allowMultipleMatchesPerLine.toString()
  }

  /**
   * If false, every line of input is matched for pattern only once. Otherwise the line can be
   * scanned multiple times when non-terminal pattern is used.
   */
  public fun allowMultipleMatchesPerLine(allowMultipleMatchesPerLine: String) {
    def.allowMultipleMatchesPerLine = allowMultipleMatchesPerLine
  }

  /**
   * Whether to capture named expressions only or not (i.e. %{IP:ip} but not ${IP})
   */
  public fun namedOnly(namedOnly: Boolean) {
    def.namedOnly = namedOnly.toString()
  }

  /**
   * Whether to capture named expressions only or not (i.e. %{IP:ip} but not ${IP})
   */
  public fun namedOnly(namedOnly: String) {
    def.namedOnly = namedOnly
  }
}
