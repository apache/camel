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
import org.apache.camel.model.dataformat.BindyDataFormat
import org.apache.camel.model.dataformat.BindyType

/**
 * Marshal and unmarshal between POJOs and Comma separated values (CSV) format using Camel Bindy
 */
public fun DataFormatDsl.bindyCsv(i: BindyCsvDataFormatDsl.() -> Unit) {
  def = BindyCsvDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class BindyCsvDataFormatDsl {
  public val def: BindyDataFormat

  init {
    def = BindyDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Whether to use Csv, Fixed, or KeyValue.
   */
  public fun type(type: BindyType) {
    def.type = type.toString()
  }

  /**
   * Whether to use Csv, Fixed, or KeyValue.
   */
  public fun type(type: String) {
    def.type = type
  }

  /**
   * Name of model class to use.
   */
  public fun classType(classType: Class<*>) {
    def.classType = classType
  }

  /**
   * Whether to allow empty streams in the unmarshal process. If true, no exception will be thrown
   * when a body without records is provided.
   */
  public fun allowEmptyStream(allowEmptyStream: Boolean) {
    def.allowEmptyStream = allowEmptyStream.toString()
  }

  /**
   * Whether to allow empty streams in the unmarshal process. If true, no exception will be thrown
   * when a body without records is provided.
   */
  public fun allowEmptyStream(allowEmptyStream: String) {
    def.allowEmptyStream = allowEmptyStream
  }

  /**
   * When unmarshalling should a single instance be unwrapped and returned instead of wrapped in a
   * java.util.List.
   */
  public fun unwrapSingleInstance(unwrapSingleInstance: Boolean) {
    def.unwrapSingleInstance = unwrapSingleInstance.toString()
  }

  /**
   * When unmarshalling should a single instance be unwrapped and returned instead of wrapped in a
   * java.util.List.
   */
  public fun unwrapSingleInstance(unwrapSingleInstance: String) {
    def.unwrapSingleInstance = unwrapSingleInstance
  }

  /**
   * To configure a default locale to use, such as us for united states. To use the JVM platform
   * default locale then use the name default
   */
  public fun locale(locale: String) {
    def.locale = locale
  }
}
