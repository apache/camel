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

/**
 * Marshal and unmarshal Java objects from and to TSV (Tab-Separated Values) records using UniVocity
 * Parsers.
 */
public fun DataFormatDsl.univocityTsv(i: UnivocityTsvDataFormatDsl.() -> Unit) {
  def = UnivocityTsvDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class UnivocityTsvDataFormatDsl {
  public val def: UniVocityTsvDataFormat

  init {
    def = UniVocityTsvDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The escape character.
   */
  public fun escapeChar(escapeChar: String) {
    def.escapeChar = escapeChar
  }

  /**
   * The string representation of a null value. The default value is null
   */
  public fun nullValue(nullValue: String) {
    def.nullValue = nullValue
  }

  /**
   * Whether or not the empty lines must be ignored. The default value is true
   */
  public fun skipEmptyLines(skipEmptyLines: Boolean) {
    def.skipEmptyLines = skipEmptyLines.toString()
  }

  /**
   * Whether or not the empty lines must be ignored. The default value is true
   */
  public fun skipEmptyLines(skipEmptyLines: String) {
    def.skipEmptyLines = skipEmptyLines
  }

  /**
   * Whether or not the trailing white spaces must be ignored. The default value is true
   */
  public fun ignoreTrailingWhitespaces(ignoreTrailingWhitespaces: Boolean) {
    def.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces.toString()
  }

  /**
   * Whether or not the trailing white spaces must be ignored. The default value is true
   */
  public fun ignoreTrailingWhitespaces(ignoreTrailingWhitespaces: String) {
    def.ignoreTrailingWhitespaces = ignoreTrailingWhitespaces
  }

  /**
   * Whether or not the leading white spaces must be ignored. The default value is true
   */
  public fun ignoreLeadingWhitespaces(ignoreLeadingWhitespaces: Boolean) {
    def.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces.toString()
  }

  /**
   * Whether or not the leading white spaces must be ignored. The default value is true
   */
  public fun ignoreLeadingWhitespaces(ignoreLeadingWhitespaces: String) {
    def.ignoreLeadingWhitespaces = ignoreLeadingWhitespaces
  }

  /**
   * Whether or not the headers are disabled. When defined, this option explicitly sets the headers
   * as null which indicates that there is no header. The default value is false
   */
  public fun headersDisabled(headersDisabled: Boolean) {
    def.headersDisabled = headersDisabled.toString()
  }

  /**
   * Whether or not the headers are disabled. When defined, this option explicitly sets the headers
   * as null which indicates that there is no header. The default value is false
   */
  public fun headersDisabled(headersDisabled: String) {
    def.headersDisabled = headersDisabled
  }

  /**
   * Whether or not the header must be read in the first line of the test document. The default
   * value is false
   */
  public fun headerExtractionEnabled(headerExtractionEnabled: Boolean) {
    def.headerExtractionEnabled = headerExtractionEnabled.toString()
  }

  /**
   * Whether or not the header must be read in the first line of the test document. The default
   * value is false
   */
  public fun headerExtractionEnabled(headerExtractionEnabled: String) {
    def.headerExtractionEnabled = headerExtractionEnabled
  }

  /**
   * The maximum number of record to read.
   */
  public fun numberOfRecordsToRead(numberOfRecordsToRead: Int) {
    def.numberOfRecordsToRead = numberOfRecordsToRead.toString()
  }

  /**
   * The maximum number of record to read.
   */
  public fun numberOfRecordsToRead(numberOfRecordsToRead: String) {
    def.numberOfRecordsToRead = numberOfRecordsToRead
  }

  /**
   * The String representation of an empty value.
   */
  public fun emptyValue(emptyValue: String) {
    def.emptyValue = emptyValue
  }

  /**
   * The line separator of the files. The default value is to use the JVM platform line separator
   */
  public fun lineSeparator(lineSeparator: String) {
    def.lineSeparator = lineSeparator
  }

  /**
   * The normalized line separator of the files. The default value is a new line character.
   */
  public fun normalizedLineSeparator(normalizedLineSeparator: String) {
    def.normalizedLineSeparator = normalizedLineSeparator
  }

  /**
   * The comment symbol. The default value is #
   */
  public fun comment(comment: String) {
    def.comment = comment
  }

  /**
   * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all
   * the lines must be read at once. The default value is false
   */
  public fun lazyLoad(lazyLoad: Boolean) {
    def.lazyLoad = lazyLoad.toString()
  }

  /**
   * Whether the unmarshalling should produce an iterator that reads the lines on the fly or if all
   * the lines must be read at once. The default value is false
   */
  public fun lazyLoad(lazyLoad: String) {
    def.lazyLoad = lazyLoad
  }

  /**
   * Whether the unmarshalling should produce maps for the lines values instead of lists. It
   * requires to have header (either defined or collected). The default value is false
   */
  public fun asMap(asMap: Boolean) {
    def.asMap = asMap.toString()
  }

  /**
   * Whether the unmarshalling should produce maps for the lines values instead of lists. It
   * requires to have header (either defined or collected). The default value is false
   */
  public fun asMap(asMap: String) {
    def.asMap = asMap
  }
}
