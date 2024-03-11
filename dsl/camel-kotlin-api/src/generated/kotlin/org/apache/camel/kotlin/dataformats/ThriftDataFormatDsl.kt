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
import org.apache.camel.model.dataformat.ThriftDataFormat

/**
 * Serialize and deserialize messages using Apache Thrift binary data format.
 */
public fun DataFormatDsl.thrift(i: ThriftDataFormatDsl.() -> Unit) {
  def = ThriftDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class ThriftDataFormatDsl {
  public val def: ThriftDataFormat

  init {
    def = ThriftDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Name of class to use when unmarshalling
   */
  public fun instanceClass(instanceClass: String) {
    def.instanceClass = instanceClass
  }

  /**
   * Defines a content type format in which thrift message will be serialized/deserialized from(to)
   * the Java been. The format can either be native or json for either native binary thrift, json or
   * simple json fields representation. The default value is binary.
   */
  public fun contentTypeFormat(contentTypeFormat: String) {
    def.contentTypeFormat = contentTypeFormat
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: Boolean) {
    def.contentTypeHeader = contentTypeHeader.toString()
  }

  /**
   * Whether the data format should set the Content-Type header with the type from the data format.
   * For example application/xml for data formats marshalling to XML, or application/json for data
   * formats marshalling to JSON
   */
  public fun contentTypeHeader(contentTypeHeader: String) {
    def.contentTypeHeader = contentTypeHeader
  }
}
