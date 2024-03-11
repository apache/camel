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
import org.apache.camel.model.dataformat.ASN1DataFormat

/**
 * Encode and decode data structures using Abstract Syntax Notation One (ASN.1).
 */
public fun DataFormatDsl.asn1(i: Asn1DataFormatDsl.() -> Unit) {
  def = Asn1DataFormatDsl().apply(i).def
}

@CamelDslMarker
public class Asn1DataFormatDsl {
  public val def: ASN1DataFormat

  init {
    def = ASN1DataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Class to use when unmarshalling.
   */
  public fun unmarshalType(unmarshalType: Class<*>) {
    def.unmarshalType = unmarshalType
  }

  /**
   * If the asn1 file has more than one entry, the setting this option to true, allows working with
   * the splitter EIP, to split the data using an iterator in a streaming mode.
   */
  public fun usingIterator(usingIterator: Boolean) {
    def.usingIterator = usingIterator.toString()
  }

  /**
   * If the asn1 file has more than one entry, the setting this option to true, allows working with
   * the splitter EIP, to split the data using an iterator in a streaming mode.
   */
  public fun usingIterator(usingIterator: String) {
    def.usingIterator = usingIterator
  }
}
