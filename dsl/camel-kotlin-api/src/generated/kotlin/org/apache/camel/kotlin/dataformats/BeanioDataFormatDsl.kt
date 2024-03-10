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

/**
 * Marshal and unmarshal Java beans to and from flat files (such as CSV, delimited, or fixed length
 * formats).
 */
public fun DataFormatDsl.beanio(i: BeanioDataFormatDsl.() -> Unit) {
  def = BeanioDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class BeanioDataFormatDsl {
  public val def: BeanioDataFormat

  init {
    def = BeanioDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The BeanIO mapping file. Is by default loaded from the classpath. You can prefix with file:,
   * http:, or classpath: to denote from where to load the mapping file.
   */
  public fun mapping(mapping: String) {
    def.mapping = mapping
  }

  /**
   * The name of the stream to use.
   */
  public fun streamName(streamName: String) {
    def.streamName = streamName
  }

  /**
   * Whether to ignore unidentified records.
   */
  public fun ignoreUnidentifiedRecords(ignoreUnidentifiedRecords: Boolean) {
    def.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords.toString()
  }

  /**
   * Whether to ignore unidentified records.
   */
  public fun ignoreUnidentifiedRecords(ignoreUnidentifiedRecords: String) {
    def.ignoreUnidentifiedRecords = ignoreUnidentifiedRecords
  }

  /**
   * Whether to ignore unexpected records.
   */
  public fun ignoreUnexpectedRecords(ignoreUnexpectedRecords: Boolean) {
    def.ignoreUnexpectedRecords = ignoreUnexpectedRecords.toString()
  }

  /**
   * Whether to ignore unexpected records.
   */
  public fun ignoreUnexpectedRecords(ignoreUnexpectedRecords: String) {
    def.ignoreUnexpectedRecords = ignoreUnexpectedRecords
  }

  /**
   * Whether to ignore invalid records.
   */
  public fun ignoreInvalidRecords(ignoreInvalidRecords: Boolean) {
    def.ignoreInvalidRecords = ignoreInvalidRecords.toString()
  }

  /**
   * Whether to ignore invalid records.
   */
  public fun ignoreInvalidRecords(ignoreInvalidRecords: String) {
    def.ignoreInvalidRecords = ignoreInvalidRecords
  }

  /**
   * The charset to use. Is by default the JVM platform default charset.
   */
  public fun encoding(encoding: String) {
    def.encoding = encoding
  }

  /**
   * To use a custom org.apache.camel.dataformat.beanio.BeanIOErrorHandler as error handler while
   * parsing. Configure the fully qualified class name of the error handler. Notice the options
   * ignoreUnidentifiedRecords, ignoreUnexpectedRecords, and ignoreInvalidRecords may not be in use
   * when you use a custom error handler.
   */
  public fun beanReaderErrorHandlerType(beanReaderErrorHandlerType: String) {
    def.beanReaderErrorHandlerType = beanReaderErrorHandlerType
  }

  /**
   * This options controls whether to unmarshal as a list of objects or as a single object only. The
   * former is the default mode, and the latter is only intended in special use-cases where beanio maps
   * the Camel message to a single POJO bean.
   */
  public fun unmarshalSingleObject(unmarshalSingleObject: Boolean) {
    def.unmarshalSingleObject = unmarshalSingleObject.toString()
  }

  /**
   * This options controls whether to unmarshal as a list of objects or as a single object only. The
   * former is the default mode, and the latter is only intended in special use-cases where beanio maps
   * the Camel message to a single POJO bean.
   */
  public fun unmarshalSingleObject(unmarshalSingleObject: String) {
    def.unmarshalSingleObject = unmarshalSingleObject
  }
}
