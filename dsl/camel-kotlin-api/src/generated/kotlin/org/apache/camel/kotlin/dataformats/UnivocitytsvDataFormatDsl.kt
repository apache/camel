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
import org.apache.camel.model.dataformat.UniVocityTsvDataFormat

public fun DataFormatDsl.univocityTsv(i: UnivocitytsvDataFormatDsl.() -> Unit) {
  def = UnivocitytsvDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class UnivocitytsvDataFormatDsl {
  public val def: UniVocityTsvDataFormat

  init {
    def = UniVocityTsvDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun escapeChar(escapeChar: String) {
    def.escapeChar = escapeChar
  }

  public fun nullValue(nullValue: String) {
    def.nullValue = nullValue
  }

  public fun skipEmptyLines(skipEmptyLines: Boolean) {
    def.skipEmptyLines = skipEmptyLines.toString()
  }

  public fun skipEmptyLines(skipEmptyLines: String) {
    def.skipEmptyLines = skipEmptyLines
  }

  public fun ignoreTrailingWhitespaces(ignoreTrailingWhitespaces: Boolean) {
    def.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces.toString()
  }

  public fun ignoreTrailingWhitespaces(ignoreTrailingWhitespaces: String) {
    def.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces
  }

  public fun ignoreLeadingWhitespaces(ignoreLeadingWhitespaces: Boolean) {
    def.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces.toString()
  }

  public fun ignoreLeadingWhitespaces(ignoreLeadingWhitespaces: String) {
    def.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces
  }

  public fun headersDisabled(headersDisabled: Boolean) {
    def.headersDisabled = headersDisabled.toString()
  }

  public fun headersDisabled(headersDisabled: String) {
    def.headersDisabled = headersDisabled
  }

  public fun headerExtractionEnabled(headerExtractionEnabled: Boolean) {
    def.headerExtractionEnabled = headerExtractionEnabled.toString()
  }

  public fun headerExtractionEnabled(headerExtractionEnabled: String) {
    def.headerExtractionEnabled = headerExtractionEnabled
  }

  public fun numberOfRecordsToRead(numberOfRecordsToRead: Int) {
    def.numberOfRecordsToRead = numberOfRecordsToRead.toString()
  }

  public fun numberOfRecordsToRead(numberOfRecordsToRead: String) {
    def.numberOfRecordsToRead = numberOfRecordsToRead
  }

  public fun emptyValue(emptyValue: String) {
    def.emptyValue = emptyValue
  }

  public fun lineSeparator(lineSeparator: String) {
    def.lineSeparator = lineSeparator
  }

  public fun normalizedLineSeparator(normalizedLineSeparator: String) {
    def.normalizedLineSeparator = normalizedLineSeparator
  }

  public fun comment(comment: String) {
    def.comment = comment
  }

  public fun lazyLoad(lazyLoad: Boolean) {
    def.lazyLoad = lazyLoad.toString()
  }

  public fun lazyLoad(lazyLoad: String) {
    def.lazyLoad = lazyLoad
  }

  public fun asMap(asMap: Boolean) {
    def.asMap = asMap.toString()
  }

  public fun asMap(asMap: String) {
    def.asMap = asMap
  }
}
