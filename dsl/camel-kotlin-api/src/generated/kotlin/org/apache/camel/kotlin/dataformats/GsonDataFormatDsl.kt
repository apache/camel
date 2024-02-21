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

import java.lang.Class
import kotlin.Any
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.JsonDataFormat

public fun DataFormatDsl.gson(i: GsonDataFormatDsl.() -> Unit) {
  def = GsonDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class GsonDataFormatDsl {
  public val def: JsonDataFormat

  init {
    def = JsonDataFormat()}

  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  public fun unmarshalType(unmarshalType: Class<out Any>) {
    def.unmarshalType = unmarshalType
  }

  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }

  public fun dateFormatPattern(dateFormatPattern: String) {
    def.dateFormatPattern = dateFormatPattern
  }
}
