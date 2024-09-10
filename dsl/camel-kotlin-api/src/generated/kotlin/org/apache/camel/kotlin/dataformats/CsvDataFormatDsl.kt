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

/**
 * Handle CSV (Comma Separated Values) payloads.
 */
public fun DataFormatDsl.csv(i: CsvDataFormatDsl.() -> Unit) {
  def = CsvDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class CsvDataFormatDsl {
  public val def: CsvDataFormat

  init {
    def = CsvDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The reference format to use, it will be updated with the other format options, the default
   * value is CSVFormat.DEFAULT
   */
  public fun formatRef(formatRef: String) {
    def.formatRef = formatRef
  }

  /**
   * The name of the format to use, the default value is CSVFormat.DEFAULT
   */
  public fun formatName(formatName: String) {
    def.formatName = formatName
  }

  /**
   * Disables the comment marker of the reference format.
   */
  public fun commentMarkerDisabled(commentMarkerDisabled: Boolean) {
    def.commentMarkerDisabled = commentMarkerDisabled.toString()
  }

  /**
   * Disables the comment marker of the reference format.
   */
  public fun commentMarkerDisabled(commentMarkerDisabled: String) {
    def.commentMarkerDisabled = commentMarkerDisabled
  }

  /**
   * Sets the comment marker of the reference format.
   */
  public fun commentMarker(commentMarker: String) {
    def.commentMarker = commentMarker
  }

  /**
   * Sets the delimiter to use. The default value is , (comma)
   */
  public fun delimiter(delimiter: String) {
    def.delimiter = delimiter
  }

  /**
   * Use for disabling using escape character
   */
  public fun escapeDisabled(escapeDisabled: Boolean) {
    def.escapeDisabled = escapeDisabled.toString()
  }

  /**
   * Use for disabling using escape character
   */
  public fun escapeDisabled(escapeDisabled: String) {
    def.escapeDisabled = escapeDisabled
  }

  /**
   * Sets the escape character to use
   */
  public fun escape(escape: String) {
    def.escape = escape
  }

  /**
   * Use for disabling headers
   */
  public fun headerDisabled(headerDisabled: Boolean) {
    def.headerDisabled = headerDisabled.toString()
  }

  /**
   * Use for disabling headers
   */
  public fun headerDisabled(headerDisabled: String) {
    def.headerDisabled = headerDisabled
  }

  /**
   * To configure the CSV headers
   */
  public fun `header`(`header`: MutableList<String>) {
    def.header = header
  }

  /**
   * Whether to allow missing column names.
   */
  public fun allowMissingColumnNames(allowMissingColumnNames: Boolean) {
    def.allowMissingColumnNames = allowMissingColumnNames.toString()
  }

  /**
   * Whether to allow missing column names.
   */
  public fun allowMissingColumnNames(allowMissingColumnNames: String) {
    def.allowMissingColumnNames = allowMissingColumnNames
  }

  /**
   * Whether to ignore empty lines.
   */
  public fun ignoreEmptyLines(ignoreEmptyLines: Boolean) {
    def.ignoreEmptyLines = ignoreEmptyLines.toString()
  }

  /**
   * Whether to ignore empty lines.
   */
  public fun ignoreEmptyLines(ignoreEmptyLines: String) {
    def.ignoreEmptyLines = ignoreEmptyLines
  }

  /**
   * Whether to ignore surrounding spaces
   */
  public fun ignoreSurroundingSpaces(ignoreSurroundingSpaces: Boolean) {
    def.ignoreSurroundingSpaces = ignoreSurroundingSpaces.toString()
  }

  /**
   * Whether to ignore surrounding spaces
   */
  public fun ignoreSurroundingSpaces(ignoreSurroundingSpaces: String) {
    def.ignoreSurroundingSpaces = ignoreSurroundingSpaces
  }

  /**
   * Used to disable null strings
   */
  public fun nullStringDisabled(nullStringDisabled: Boolean) {
    def.nullStringDisabled = nullStringDisabled.toString()
  }

  /**
   * Used to disable null strings
   */
  public fun nullStringDisabled(nullStringDisabled: String) {
    def.nullStringDisabled = nullStringDisabled
  }

  /**
   * Sets the null string
   */
  public fun nullString(nullString: String) {
    def.nullString = nullString
  }

  /**
   * Used to disable quotes
   */
  public fun quoteDisabled(quoteDisabled: Boolean) {
    def.quoteDisabled = quoteDisabled.toString()
  }

  /**
   * Used to disable quotes
   */
  public fun quoteDisabled(quoteDisabled: String) {
    def.quoteDisabled = quoteDisabled
  }

  /**
   * Sets the quote to use which by default is double-quote character
   */
  public fun quote(quote: String) {
    def.quote = quote
  }

  /**
   * Used for disabling record separator
   */
  public fun recordSeparatorDisabled(recordSeparatorDisabled: String) {
    def.recordSeparatorDisabled = recordSeparatorDisabled
  }

  /**
   * Sets the record separator (aka new line) which by default is new line characters (CRLF)
   */
  public fun recordSeparator(recordSeparator: String) {
    def.recordSeparator = recordSeparator
  }

  /**
   * Whether to skip the header record in the output
   */
  public fun skipHeaderRecord(skipHeaderRecord: Boolean) {
    def.skipHeaderRecord = skipHeaderRecord.toString()
  }

  /**
   * Whether to skip the header record in the output
   */
  public fun skipHeaderRecord(skipHeaderRecord: String) {
    def.skipHeaderRecord = skipHeaderRecord
  }

  /**
   * Sets the quote mode
   */
  public fun quoteMode(quoteMode: String) {
    def.quoteMode = quoteMode
  }

  /**
   * Sets whether or not to ignore case when accessing header names.
   */
  public fun ignoreHeaderCase(ignoreHeaderCase: Boolean) {
    def.ignoreHeaderCase = ignoreHeaderCase.toString()
  }

  /**
   * Sets whether or not to ignore case when accessing header names.
   */
  public fun ignoreHeaderCase(ignoreHeaderCase: String) {
    def.ignoreHeaderCase = ignoreHeaderCase
  }

  /**
   * Sets whether or not to trim leading and trailing blanks.
   */
  public fun trim(trim: Boolean) {
    def.trim = trim.toString()
  }

  /**
   * Sets whether or not to trim leading and trailing blanks.
   */
  public fun trim(trim: String) {
    def.trim = trim
  }

  /**
   * Sets whether or not to add a trailing delimiter.
   */
  public fun trailingDelimiter(trailingDelimiter: Boolean) {
    def.trailingDelimiter = trailingDelimiter.toString()
  }

  /**
   * Sets whether or not to add a trailing delimiter.
   */
  public fun trailingDelimiter(trailingDelimiter: String) {
    def.trailingDelimiter = trailingDelimiter
  }

  /**
   * Sets the implementation of the CsvMarshallerFactory interface which is able to customize
   * marshalling/unmarshalling behavior by extending CsvMarshaller or creating it from scratch.
   */
  public fun marshallerFactoryRef(marshallerFactoryRef: String) {
    def.marshallerFactoryRef = marshallerFactoryRef
  }

  /**
   * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all
   * the lines must be read at one.
   */
  public fun lazyLoad(lazyLoad: Boolean) {
    def.lazyLoad = lazyLoad.toString()
  }

  /**
   * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all
   * the lines must be read at one.
   */
  public fun lazyLoad(lazyLoad: String) {
    def.lazyLoad = lazyLoad
  }

  /**
   * Whether the unmarshalling should produce maps (HashMap)for the lines values instead of lists.
   * It requires to have header (either defined or collected).
   */
  public fun useMaps(useMaps: Boolean) {
    def.useMaps = useMaps.toString()
  }

  /**
   * Whether the unmarshalling should produce maps (HashMap)for the lines values instead of lists.
   * It requires to have header (either defined or collected).
   */
  public fun useMaps(useMaps: String) {
    def.useMaps = useMaps
  }

  /**
   * Whether the unmarshalling should produce ordered maps (LinkedHashMap) for the lines values
   * instead of lists. It requires to have header (either defined or collected).
   */
  public fun useOrderedMaps(useOrderedMaps: Boolean) {
    def.useOrderedMaps = useOrderedMaps.toString()
  }

  /**
   * Whether the unmarshalling should produce ordered maps (LinkedHashMap) for the lines values
   * instead of lists. It requires to have header (either defined or collected).
   */
  public fun useOrderedMaps(useOrderedMaps: String) {
    def.useOrderedMaps = useOrderedMaps
  }

  /**
   * Refers to a custom CsvRecordConverter to lookup from the registry to use.
   */
  public fun recordConverterRef(recordConverterRef: String) {
    def.recordConverterRef = recordConverterRef
  }

  /**
   * Whether the unmarshalling should capture the header record and store it in the message header
   */
  public fun captureHeaderRecord(captureHeaderRecord: Boolean) {
    def.captureHeaderRecord = captureHeaderRecord.toString()
  }

  /**
   * Whether the unmarshalling should capture the header record and store it in the message header
   */
  public fun captureHeaderRecord(captureHeaderRecord: String) {
    def.captureHeaderRecord = captureHeaderRecord
  }
}
