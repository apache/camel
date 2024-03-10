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
import kotlin.Boolean
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.JsonDataFormat

/**
 * Marshal POJOs to JSON and back using JSON-B.
 */
public fun DataFormatDsl.jsonb(i: JsonbDataFormatDsl.() -> Unit) {
  def = JsonbDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class JsonbDataFormatDsl {
  public val def: JsonDataFormat

  init {
    def = JsonDataFormat()}

  /**
   * Lookup and use the existing Jsonb instance with the given id.
   */
  public fun objectMapper(objectMapper: String) {
    def.objectMapper = objectMapper
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: Boolean) {
    def.prettyPrint = prettyPrint.toString()
  }

  /**
   * To enable pretty printing output nicely formatted. Is by default false.
   */
  public fun prettyPrint(prettyPrint: String) {
    def.prettyPrint = prettyPrint
  }

  /**
   * Class name of the java type to use when unmarshalling
   */
  public fun unmarshalType(unmarshalType: Class<*>) {
    def.unmarshalType = unmarshalType
  }
}
