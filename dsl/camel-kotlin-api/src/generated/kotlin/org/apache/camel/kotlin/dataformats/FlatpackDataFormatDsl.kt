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

public fun DataFormatDsl.flatpack(i: FlatpackDataFormatDsl.() -> Unit) {
  def = FlatpackDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class FlatpackDataFormatDsl {
  public val def: FlatpackDataFormat

  init {
    def = FlatpackDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun definition(definition: String) {
    def.definition = definition
  }

  public fun fixed(fixed: Boolean) {
    def.fixed = fixed.toString()
  }

  public fun fixed(fixed: String) {
    def.fixed = fixed
  }

  public fun delimiter(delimiter: String) {
    def.delimiter = delimiter
  }

  public fun ignoreFirstRecord(ignoreFirstRecord: Boolean) {
    def.ignoreFirstRecord = ignoreFirstRecord.toString()
  }

  public fun ignoreFirstRecord(ignoreFirstRecord: String) {
    def.ignoreFirstRecord = ignoreFirstRecord
  }

  public fun allowShortLines(allowShortLines: Boolean) {
    def.allowShortLines = allowShortLines.toString()
  }

  public fun allowShortLines(allowShortLines: String) {
    def.allowShortLines = allowShortLines
  }

  public fun ignoreExtraColumns(ignoreExtraColumns: Boolean) {
    def.ignoreExtraColumns = ignoreExtraColumns.toString()
  }

  public fun ignoreExtraColumns(ignoreExtraColumns: String) {
    def.ignoreExtraColumns = ignoreExtraColumns
  }

  public fun textQualifier(textQualifier: String) {
    def.textQualifier = textQualifier
  }

  public fun parserFactoryRef(parserFactoryRef: String) {
    def.parserFactoryRef = parserFactoryRef
  }
}
