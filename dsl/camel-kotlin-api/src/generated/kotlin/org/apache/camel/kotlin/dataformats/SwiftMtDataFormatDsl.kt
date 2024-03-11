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
import org.apache.camel.model.dataformat.SwiftMtDataFormat

/**
 * Encode and decode SWIFT MT messages.
 */
public fun DataFormatDsl.swiftMt(i: SwiftMtDataFormatDsl.() -> Unit) {
  def = SwiftMtDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class SwiftMtDataFormatDsl {
  public val def: SwiftMtDataFormat

  init {
    def = SwiftMtDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
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
}
