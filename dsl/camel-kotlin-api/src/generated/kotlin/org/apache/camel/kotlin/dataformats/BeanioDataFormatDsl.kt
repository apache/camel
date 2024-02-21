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
import org.apache.camel.model.dataformat.BeanioDataFormat

public fun DataFormatDsl.beanio(i: BeanioDataFormatDsl.() -> Unit) {
  def = BeanioDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class BeanioDataFormatDsl {
  public val def: BeanioDataFormat

  init {
    def = BeanioDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun mapping(mapping: String) {
    def.mapping = mapping
  }

  public fun streamName(streamName: String) {
    def.streamName = streamName
  }

  public fun ignoreUnidentifiedRecords(ignoreUnidentifiedRecords: Boolean) {
    def.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords.toString()
  }

  public fun ignoreUnidentifiedRecords(ignoreUnidentifiedRecords: String) {
    def.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords
  }

  public fun ignoreUnexpectedRecords(ignoreUnexpectedRecords: Boolean) {
    def.ignoreUnexpectedRecords = ignoreUnexpectedRecords.toString()
  }

  public fun ignoreUnexpectedRecords(ignoreUnexpectedRecords: String) {
    def.ignoreUnexpectedRecords = ignoreUnexpectedRecords
  }

  public fun ignoreInvalidRecords(ignoreInvalidRecords: Boolean) {
    def.ignoreInvalidRecords = ignoreInvalidRecords.toString()
  }

  public fun ignoreInvalidRecords(ignoreInvalidRecords: String) {
    def.ignoreInvalidRecords = ignoreInvalidRecords
  }

  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  public fun beanReaderErrorHandlerType(beanReaderErrorHandlerType: String) {
    def.beanReaderErrorHandlerType = beanReaderErrorHandlerType
  }

  public fun unmarshalSingleObject(unmarshalSingleObject: Boolean) {
    def.unmarshalSingleObject = unmarshalSingleObject.toString()
  }

  public fun unmarshalSingleObject(unmarshalSingleObject: String) {
    def.unmarshalSingleObject = unmarshalSingleObject
  }
}
