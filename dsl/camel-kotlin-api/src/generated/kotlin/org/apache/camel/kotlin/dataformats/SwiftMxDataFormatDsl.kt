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
import org.apache.camel.model.dataformat.SwiftMxDataFormat

/**
 * Encode and decode SWIFT MX messages.
 */
public fun DataFormatDsl.swiftMx(i: SwiftMxDataFormatDsl.() -> Unit) {
  def = SwiftMxDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SwiftMxDataFormatDsl {
  public val def: SwiftMxDataFormat

  init {
    def = SwiftMxDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Refers to a specific configuration to use when marshalling a message to lookup from the
   * registry.
   */
  public fun writeConfigRef(writeConfigRef: String) {
    def.writeConfigRef = writeConfigRef
  }

  /**
   * The flag indicating that messages must be marshalled in a JSON format.
   */
  public fun writeInJson(writeInJson: Boolean) {
    def.writeInJson = writeInJson.toString()
  }

  /**
   * The flag indicating that messages must be marshalled in a JSON format.
   */
  public fun writeInJson(writeInJson: String) {
    def.writeInJson = writeInJson
  }

  /**
   * The type of MX message to produce when unmarshalling an input stream. If not set, it will be
   * automatically detected from the namespace used.
   */
  public fun readMessageId(readMessageId: String) {
    def.readMessageId = readMessageId
  }

  /**
   * Refers to a specific configuration to use when unmarshalling an input stream to lookup from the
   * registry.
   */
  public fun readConfigRef(readConfigRef: String) {
    def.readConfigRef = readConfigRef
  }
}
