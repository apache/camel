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

/**
 * Encrypt and decrypt messages using Java Cryptographic Extension (JCE) and PGP.
 */
public fun DataFormatDsl.pgp(i: PgpDataFormatDsl.() -> Unit) {
  def = PgpDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class PgpDataFormatDsl {
  public val def: PGPDataFormat

  init {
    def = PGPDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The user ID of the key in the PGP keyring used during encryption. Can also be only a part of a
   * user ID. For example, if the user ID is Test User then you can use the part Test User or to
   * address the user ID.
   */
  public fun keyUserid(keyUserid: String) {
    def.keyUserid = keyUserid
  }

  /**
   * User ID of the key in the PGP keyring used for signing (during encryption) or signature
   * verification (during decryption). During the signature verification process the specified User ID
   * restricts the public keys from the public keyring which can be used for the verification. If no
   * User ID is specified for the signature verficiation then any public key in the public keyring can
   * be used for the verification. Can also be only a part of a user ID. For example, if the user ID is
   * Test User then you can use the part Test User or to address the User ID.
   */
  public fun signatureKeyUserid(signatureKeyUserid: String) {
    def.signatureKeyUserid = signatureKeyUserid
  }

  /**
   * Password used when opening the private key (not used for encryption).
   */
  public fun password(password: String) {
    def.password = password
  }

  /**
   * Password used when opening the private key used for signing (during encryption).
   */
  public fun signaturePassword(signaturePassword: String) {
    def.signaturePassword = signaturePassword
  }

  /**
   * Filename of the keyring; must be accessible as a classpath resource (but you can specify a
   * location in the file system by using the file: prefix).
   */
  public fun keyFileName(keyFileName: String) {
    def.keyFileName = keyFileName
  }

  /**
   * Filename of the keyring to use for signing (during encryption) or for signature verification
   * (during decryption); must be accessible as a classpath resource (but you can specify a location in
   * the file system by using the file: prefix).
   */
  public fun signatureKeyFileName(signatureKeyFileName: String) {
    def.signatureKeyFileName = signatureKeyFileName
  }

  /**
   * Keyring used for signing/verifying as byte array. You can not set the signatureKeyFileName and
   * signatureKeyRing at the same time.
   */
  public fun signatureKeyRing(signatureKeyRing: String) {
    def.signatureKeyRing = signatureKeyRing
  }

  /**
   * This option will cause PGP to base64 encode the encrypted text, making it available for
   * copy/paste, etc.
   */
  public fun armored(armored: Boolean) {
    def.armored = armored.toString()
  }

  /**
   * This option will cause PGP to base64 encode the encrypted text, making it available for
   * copy/paste, etc.
   */
  public fun armored(armored: String) {
    def.armored = armored
  }

  /**
   * Adds an integrity check/sign into the encryption file. The default value is true.
   */
  public fun integrity(integrity: Boolean) {
    def.integrity = integrity.toString()
  }

  /**
   * Adds an integrity check/sign into the encryption file. The default value is true.
   */
  public fun integrity(integrity: String) {
    def.integrity = integrity
  }

  /**
   * Java Cryptography Extension (JCE) provider, default is Bouncy Castle (BC). Alternatively you
   * can use, for example, the IAIK JCE provider; in this case the provider must be registered
   * beforehand and the Bouncy Castle provider must not be registered beforehand. The Sun JCE provider
   * does not work.
   */
  public fun provider(provider: String) {
    def.provider = provider
  }

  /**
   * Symmetric key encryption algorithm; possible values are defined in
   * org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags; for example 2 (= TRIPLE DES), 3 (= CAST5), 4 (=
   * BLOWFISH), 6 (= DES), 7 (= AES_128). Only relevant for encrypting.
   */
  public fun algorithm(algorithm: Int) {
    def.algorithm = algorithm.toString()
  }

  /**
   * Symmetric key encryption algorithm; possible values are defined in
   * org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags; for example 2 (= TRIPLE DES), 3 (= CAST5), 4 (=
   * BLOWFISH), 6 (= DES), 7 (= AES_128). Only relevant for encrypting.
   */
  public fun algorithm(algorithm: String) {
    def.algorithm = algorithm
  }

  /**
   * Compression algorithm; possible values are defined in
   * org.bouncycastle.bcpg.CompressionAlgorithmTags; for example 0 (= UNCOMPRESSED), 1 (= ZIP), 2 (=
   * ZLIB), 3 (= BZIP2). Only relevant for encrypting.
   */
  public fun compressionAlgorithm(compressionAlgorithm: Int) {
    def.compressionAlgorithm = compressionAlgorithm.toString()
  }

  /**
   * Compression algorithm; possible values are defined in
   * org.bouncycastle.bcpg.CompressionAlgorithmTags; for example 0 (= UNCOMPRESSED), 1 (= ZIP), 2 (=
   * ZLIB), 3 (= BZIP2). Only relevant for encrypting.
   */
  public fun compressionAlgorithm(compressionAlgorithm: String) {
    def.compressionAlgorithm = compressionAlgorithm
  }

  /**
   * Signature hash algorithm; possible values are defined in
   * org.bouncycastle.bcpg.HashAlgorithmTags; for example 2 (= SHA1), 8 (= SHA256), 9 (= SHA384), 10 (=
   * SHA512), 11 (=SHA224). Only relevant for signing.
   */
  public fun hashAlgorithm(hashAlgorithm: Int) {
    def.hashAlgorithm = hashAlgorithm.toString()
  }

  /**
   * Signature hash algorithm; possible values are defined in
   * org.bouncycastle.bcpg.HashAlgorithmTags; for example 2 (= SHA1), 8 (= SHA256), 9 (= SHA384), 10 (=
   * SHA512), 11 (=SHA224). Only relevant for signing.
   */
  public fun hashAlgorithm(hashAlgorithm: String) {
    def.hashAlgorithm = hashAlgorithm
  }

  /**
   * Controls the behavior for verifying the signature during unmarshaling. There are 4 values
   * possible: optional: The PGP message may or may not contain signatures; if it does contain
   * signatures, then a signature verification is executed. required: The PGP message must contain at
   * least one signature; if this is not the case an exception (PGPException) is thrown. A signature
   * verification is executed. ignore: Contained signatures in the PGP message are ignored; no
   * signature verification is executed. no_signature_allowed: The PGP message must not contain a
   * signature; otherwise an exception (PGPException) is thrown.
   */
  public fun signatureVerificationOption(signatureVerificationOption: String) {
    def.signatureVerificationOption = signatureVerificationOption
  }
}
