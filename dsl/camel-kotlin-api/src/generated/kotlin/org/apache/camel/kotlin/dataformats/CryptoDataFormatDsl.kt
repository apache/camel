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
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.CryptoDataFormat

public fun DataFormatDsl.crypto(i: CryptoDataFormatDsl.() -> Unit) {
  def = CryptoDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class CryptoDataFormatDsl {
  public val def: CryptoDataFormat

  init {
    def = CryptoDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun algorithm(algorithm: String) {
    def.algorithm = algorithm
  }

  public fun keyRef(keyRef: String) {
    def.keyRef = keyRef
  }

  public fun cryptoProvider(cryptoProvider: String) {
    def.cryptoProvider = cryptoProvider
  }

  public fun initVectorRef(initVectorRef: String) {
    def.initVectorRef = initVectorRef
  }

  public fun algorithmParameterRef(algorithmParameterRef: String) {
    def.algorithmParameterRef = algorithmParameterRef
  }

  public fun bufferSize(bufferSize: Int) {
    def.bufferSize = bufferSize.toString()
  }

  public fun bufferSize(bufferSize: String) {
    def.bufferSize = bufferSize
  }

  public fun macAlgorithm(macAlgorithm: String) {
    def.macAlgorithm = macAlgorithm
  }

  public fun shouldAppendHMAC(shouldAppendHMAC: Boolean) {
    def.shouldAppendHMAC = shouldAppendHMAC.toString()
  }

  public fun shouldAppendHMAC(shouldAppendHMAC: String) {
    def.shouldAppendHMAC = shouldAppendHMAC
  }

  public fun `inline`(`inline`: Boolean) {
    def.inline = inline.toString()
  }

  public fun `inline`(`inline`: String) {
    def.inline = inline
  }
}
