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
import org.apache.camel.model.dataformat.FlatpackDataFormat

/**
 * Marshal and unmarshal Java lists and maps to/from flat files (such as CSV, delimited, or fixed
 * length formats) using Flatpack library.
 */
public fun DataFormatDsl.flatpack(i: FlatpackDataFormatDsl.() -> Unit) {
  def = FlatpackDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class FlatpackDataFormatDsl {
  public val def: FlatpackDataFormat

  init {
    def = FlatpackDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The flatpack pzmap configuration file. Can be omitted in simpler situations, but its preferred
   * to use the pzmap.
   */
  public fun definition(definition: String) {
    def.definition = definition
  }

  /**
   * Delimited or fixed. Is by default false = delimited
   */
  public fun fixed(fixed: Boolean) {
    def.fixed = fixed.toString()
  }

  /**
   * Delimited or fixed. Is by default false = delimited
   */
  public fun fixed(fixed: String) {
    def.fixed = fixed
  }

  /**
   * The delimiter char (could be ; , or similar)
   */
  public fun delimiter(delimiter: String) {
    def.delimiter = delimiter
  }

  /**
   * Whether the first line is ignored for delimited files (for the column headers). Is by default
   * true.
   */
  public fun ignoreFirstRecord(ignoreFirstRecord: Boolean) {
    def.ignoreFirstRecord = ignoreFirstRecord.toString()
  }

  /**
   * Whether the first line is ignored for delimited files (for the column headers). Is by default
   * true.
   */
  public fun ignoreFirstRecord(ignoreFirstRecord: String) {
    def.ignoreFirstRecord = ignoreFirstRecord
  }

  /**
   * Allows for lines to be shorter than expected and ignores the extra characters
   */
  public fun allowShortLines(allowShortLines: Boolean) {
    def.allowShortLines = allowShortLines.toString()
  }

  /**
   * Allows for lines to be shorter than expected and ignores the extra characters
   */
  public fun allowShortLines(allowShortLines: String) {
    def.allowShortLines = allowShortLines
  }

  /**
   * Allows for lines to be longer than expected and ignores the extra characters.
   */
  public fun ignoreExtraColumns(ignoreExtraColumns: Boolean) {
    def.ignoreExtraColumns = ignoreExtraColumns.toString()
  }

  /**
   * Allows for lines to be longer than expected and ignores the extra characters.
   */
  public fun ignoreExtraColumns(ignoreExtraColumns: String) {
    def.ignoreExtraColumns = ignoreExtraColumns
  }

  /**
   * If the text is qualified with a character. Uses quote character by default.
   */
  public fun textQualifier(textQualifier: String) {
    def.textQualifier = textQualifier
  }

  /**
   * References to a custom parser factory to lookup in the registry
   */
  public fun parserFactoryRef(parserFactoryRef: String) {
    def.parserFactoryRef = parserFactoryRef
  }
}
