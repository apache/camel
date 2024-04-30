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

/**
 * Encrypt and decrypt messages using Java Cryptography Extension (JCE).
 */
public fun DataFormatDsl.crypto(i: CryptoDataFormatDsl.() -> Unit) {
  def = CryptoDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class CryptoDataFormatDsl {
  public val def: CryptoDataFormat

  init {
    def = CryptoDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The JCE algorithm name indicating the cryptographic algorithm that will be used.
   */
  public fun algorithm(algorithm: String) {
    def.algorithm = algorithm
  }

  /**
   * Refers to the secret key to lookup from the register to use.
   */
  public fun keyRef(keyRef: String) {
    def.keyRef = keyRef
  }

  /**
   * The name of the JCE Security Provider that should be used.
   */
  public fun cryptoProvider(cryptoProvider: String) {
    def.cryptoProvider = cryptoProvider
  }

  /**
   * Refers to a byte array containing the Initialization Vector that will be used to initialize the
   * Cipher.
   */
  public fun initVectorRef(initVectorRef: String) {
    def.initVectorRef = initVectorRef
  }

  /**
   * A JCE AlgorithmParameterSpec used to initialize the Cipher. Will lookup the type using the
   * given name as a java.security.spec.AlgorithmParameterSpec type.
   */
  public fun algorithmParameterRef(algorithmParameterRef: String) {
    def.algorithmParameterRef = algorithmParameterRef
  }

  /**
   * The size of the buffer used in the signature process.
   */
  public fun bufferSize(bufferSize: Int) {
    def.bufferSize = bufferSize.toString()
  }

  /**
   * The size of the buffer used in the signature process.
   */
  public fun bufferSize(bufferSize: String) {
    def.bufferSize = bufferSize
  }

  /**
   * The JCE algorithm name indicating the Message Authentication algorithm.
   */
  public fun macAlgorithm(macAlgorithm: String) {
    def.macAlgorithm = macAlgorithm
  }

  /**
   * Flag indicating that a Message Authentication Code should be calculated and appended to the
   * encrypted data.
   */
  public fun shouldAppendHMAC(shouldAppendHMAC: Boolean) {
    def.shouldAppendHMAC = shouldAppendHMAC.toString()
  }

  /**
   * Flag indicating that a Message Authentication Code should be calculated and appended to the
   * encrypted data.
   */
  public fun shouldAppendHMAC(shouldAppendHMAC: String) {
    def.shouldAppendHMAC = shouldAppendHMAC
  }

  /**
   * Flag indicating that the configured IV should be inlined into the encrypted data stream. Is by
   * default false.
   */
  public fun `inline`(`inline`: Boolean) {
    def.inline = inline.toString()
  }

  /**
   * Flag indicating that the configured IV should be inlined into the encrypted data stream. Is by
   * default false.
   */
  public fun `inline`(`inline`: String) {
    def.inline = inline
  }
}
