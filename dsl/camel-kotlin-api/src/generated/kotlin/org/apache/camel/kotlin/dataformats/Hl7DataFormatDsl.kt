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
import org.apache.camel.model.dataformat.HL7DataFormat

/**
 * Marshal and unmarshal HL7 (Health Care) model objects using the HL7 MLLP codec.
 */
public fun DataFormatDsl.hl7(i: Hl7DataFormatDsl.() -> Unit) {
  def = Hl7DataFormatDsl().apply(i).def
}

@CamelDslMarker
public class Hl7DataFormatDsl {
  public val def: HL7DataFormat

  init {
    def = HL7DataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Whether to validate the HL7 message Is by default true.
   */
  public fun validate(validate: Boolean) {
    def.validate = validate.toString()
  }

  /**
   * Whether to validate the HL7 message Is by default true.
   */
  public fun validate(validate: String) {
    def.validate = validate
  }
}
