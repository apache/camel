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
import org.apache.camel.model.dataformat.PGPDataFormat

public fun DataFormatDsl.pgp(i: PgpDataFormatDsl.() -> Unit) {
  def = PgpDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class PgpDataFormatDsl {
  public val def: PGPDataFormat

  init {
    def = PGPDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun keyUserid(keyUserid: String) {
    def.keyUserid = keyUserid
  }

  public fun signatureKeyUserid(signatureKeyUserid: String) {
    def.signatureKeyUserid = signatureKeyUserid
  }

  public fun password(password: String) {
    def.password = password
  }

  public fun signaturePassword(signaturePassword: String) {
    def.signaturePassword = signaturePassword
  }

  public fun keyFileName(keyFileName: String) {
    def.keyFileName = keyFileName
  }

  public fun signatureKeyFileName(signatureKeyFileName: String) {
    def.signatureKeyFileName = signatureKeyFileName
  }

  public fun signatureKeyRing(signatureKeyRing: String) {
    def.signatureKeyRing = signatureKeyRing
  }

  public fun armored(armored: Boolean) {
    def.armored = armored.toString()
  }

  public fun armored(armored: String) {
    def.armored = armored
  }

  public fun integrity(integrity: Boolean) {
    def.integrity = integrity.toString()
  }

  public fun integrity(integrity: String) {
    def.integrity = integrity
  }

  public fun provider(provider: String) {
    def.provider = provider
  }

  public fun algorithm(algorithm: Int) {
    def.algorithm = algorithm.toString()
  }

  public fun algorithm(algorithm: String) {
    def.algorithm = algorithm
  }

  public fun compressionAlgorithm(compressionAlgorithm: Int) {
    def.compressionAlgorithm = compressionAlgorithm.toString()
  }

  public fun compressionAlgorithm(compressionAlgorithm: String) {
    def.compressionAlgorithm = compressionAlgorithm
  }

  public fun hashAlgorithm(hashAlgorithm: Int) {
    def.hashAlgorithm = hashAlgorithm.toString()
  }

  public fun hashAlgorithm(hashAlgorithm: String) {
    def.hashAlgorithm = hashAlgorithm
  }

  public fun signatureVerificationOption(signatureVerificationOption: String) {
    def.signatureVerificationOption = signatureVerificationOption
  }
}
