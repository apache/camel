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
import org.apache.camel.model.dataformat.BindyDataFormat
import org.apache.camel.model.dataformat.BindyType

public fun DataFormatDsl.bindyFixed(i: BindyfixedDataFormatDsl.() -> Unit) {
  def = BindyfixedDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class BindyfixedDataFormatDsl {
  public val def: BindyDataFormat

  init {
    def = BindyDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun type(type: BindyType) {
    def.type = type.toString()
  }

  public fun type(type: String) {
    def.type = type
  }

  public fun classType(classType: Class<out Any>) {
    def.classType = classType
  }

  public fun allowEmptyStream(allowEmptyStream: Boolean) {
    def.allowEmptyStream = allowEmptyStream.toString()
  }

  public fun allowEmptyStream(allowEmptyStream: String) {
    def.allowEmptyStream = allowEmptyStream
  }

  public fun unwrapSingleInstance(unwrapSingleInstance: Boolean) {
    def.unwrapSingleInstance = unwrapSingleInstance.toString()
  }

  public fun unwrapSingleInstance(unwrapSingleInstance: String) {
    def.unwrapSingleInstance = unwrapSingleInstance
  }

  public fun locale(locale: String) {
    def.locale = locale
  }
}
