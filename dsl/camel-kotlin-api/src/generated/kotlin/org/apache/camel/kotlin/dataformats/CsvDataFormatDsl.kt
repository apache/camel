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
import kotlin.collections.MutableList
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.CsvDataFormat

public fun DataFormatDsl.csv(i: CsvDataFormatDsl.() -> Unit) {
  def = CsvDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class CsvDataFormatDsl {
  public val def: CsvDataFormat

  init {
    def = CsvDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun formatRef(formatRef: String) {
    def.formatRef = formatRef
  }

  public fun formatName(formatName: String) {
    def.formatName = formatName
  }

  public fun commentMarkerDisabled(commentMarkerDisabled: Boolean) {
    def.commentMarkerDisabled = commentMarkerDisabled.toString()
  }

  public fun commentMarkerDisabled(commentMarkerDisabled: String) {
    def.commentMarkerDisabled = commentMarkerDisabled
  }

  public fun commentMarker(commentMarker: String) {
    def.commentMarker = commentMarker
  }

  public fun delimiter(delimiter: String) {
    def.delimiter = delimiter
  }

  public fun escapeDisabled(escapeDisabled: Boolean) {
    def.escapeDisabled = escapeDisabled.toString()
  }

  public fun escapeDisabled(escapeDisabled: String) {
    def.escapeDisabled = escapeDisabled
  }

  public fun escape(escape: String) {
    def.escape = escape
  }

  public fun headerDisabled(headerDisabled: Boolean) {
    def.headerDisabled = headerDisabled.toString()
  }

  public fun headerDisabled(headerDisabled: String) {
    def.headerDisabled = headerDisabled
  }

  public fun `header`(`header`: MutableList<String>) {
    def.header = header
  }

  public fun allowMissingColumnNames(allowMissingColumnNames: Boolean) {
    def.allowMissingColumnNames = allowMissingColumnNames.toString()
  }

  public fun allowMissingColumnNames(allowMissingColumnNames: String) {
    def.allowMissingColumnNames = allowMissingColumnNames
  }

  public fun ignoreEmptyLines(ignoreEmptyLines: Boolean) {
    def.ignoreEmptyLines = ignoreEmptyLines.toString()
  }

  public fun ignoreEmptyLines(ignoreEmptyLines: String) {
    def.ignoreEmptyLines = ignoreEmptyLines
  }

  public fun ignoreSurroundingSpaces(ignoreSurroundingSpaces: Boolean) {
    def.ignoreSurroundingSpaces = ignoreSurroundingSpaces.toString()
  }

  public fun ignoreSurroundingSpaces(ignoreSurroundingSpaces: String) {
    def.ignoreSurroundingSpaces = ignoreSurroundingSpaces
  }

  public fun nullStringDisabled(nullStringDisabled: Boolean) {
    def.nullStringDisabled = nullStringDisabled.toString()
  }

  public fun nullStringDisabled(nullStringDisabled: String) {
    def.nullStringDisabled = nullStringDisabled
  }

  public fun nullString(nullString: String) {
    def.nullString = nullString
  }

  public fun quoteDisabled(quoteDisabled: Boolean) {
    def.quoteDisabled = quoteDisabled.toString()
  }

  public fun quoteDisabled(quoteDisabled: String) {
    def.quoteDisabled = quoteDisabled
  }

  public fun quote(quote: String) {
    def.quote = quote
  }

  public fun recordSeparatorDisabled(recordSeparatorDisabled: String) {
    def.recordSeparatorDisabled = recordSeparatorDisabled
  }

  public fun recordSeparator(recordSeparator: String) {
    def.recordSeparator = recordSeparator
  }

  public fun skipHeaderRecord(skipHeaderRecord: Boolean) {
    def.skipHeaderRecord = skipHeaderRecord.toString()
  }

  public fun skipHeaderRecord(skipHeaderRecord: String) {
    def.skipHeaderRecord = skipHeaderRecord
  }

  public fun quoteMode(quoteMode: String) {
    def.quoteMode = quoteMode
  }

  public fun ignoreHeaderCase(ignoreHeaderCase: Boolean) {
    def.ignoreHeaderCase = ignoreHeaderCase.toString()
  }

  public fun ignoreHeaderCase(ignoreHeaderCase: String) {
    def.ignoreHeaderCase = ignoreHeaderCase
  }

  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  public fun trim(trim: String) {
    def.trim = trim
  }

  public fun trailingDelimiter(trailingDelimiter: Boolean) {
    def.trailingDelimiter = trailingDelimiter.toString()
  }

  public fun trailingDelimiter(trailingDelimiter: String) {
    def.trailingDelimiter = trailingDelimiter
  }

  public fun marshallerFactoryRef(marshallerFactoryRef: String) {
    def.marshallerFactoryRef = marshallerFactoryRef
  }

  public fun lazyLoad(lazyLoad: Boolean) {
    def.lazyLoad = lazyLoad.toString()
  }

  public fun lazyLoad(lazyLoad: String) {
    def.lazyLoad = lazyLoad
  }

  public fun useMaps(useMaps: Boolean) {
    def.useMaps = useMaps.toString()
  }

  public fun useMaps(useMaps: String) {
    def.useMaps = useMaps
  }

  public fun useOrderedMaps(useOrderedMaps: Boolean) {
    def.useOrderedMaps = useOrderedMaps.toString()
  }

  public fun useOrderedMaps(useOrderedMaps: String) {
    def.useOrderedMaps = useOrderedMaps
  }

  public fun recordConverterRef(recordConverterRef: String) {
    def.recordConverterRef = recordConverterRef
  }

  public fun captureHeaderRecord(captureHeaderRecord: Boolean) {
    def.captureHeaderRecord = captureHeaderRecord.toString()
  }

  public fun captureHeaderRecord(captureHeaderRecord: String) {
    def.captureHeaderRecord = captureHeaderRecord
  }
}
