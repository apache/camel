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
import kotlin.ByteArray
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.XMLSecurityDataFormat

/**
 * Encrypt and decrypt XML payloads using Apache Santuario.
 */
public fun DataFormatDsl.xmlSecurity(i: XmlSecurityDataFormatDsl.() -> Unit) {
  def = XmlSecurityDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class XmlSecurityDataFormatDsl {
  public val def: XMLSecurityDataFormat

  init {
    def = XMLSecurityDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * The cipher algorithm to be used for encryption/decryption of the XML message content. The
   * available choices are: XMLCipher.TRIPLEDES XMLCipher.AES_128 XMLCipher.AES_128_GCM
   * XMLCipher.AES_192 XMLCipher.AES_192_GCM XMLCipher.AES_256 XMLCipher.AES_256_GCM XMLCipher.SEED_128
   * XMLCipher.CAMELLIA_128 XMLCipher.CAMELLIA_192 XMLCipher.CAMELLIA_256 The default value is
   * XMLCipher.AES_256_GCM
   */
  public fun xmlCipherAlgorithm(xmlCipherAlgorithm: String) {
    def.xmlCipherAlgorithm = xmlCipherAlgorithm
  }

  /**
   * A String used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The
   * passPhrase needs to be put together in conjunction with the appropriate encryption algorithm. For
   * example using TRIPLEDES the passPhase can be a Only another 24 Byte key
   */
  public fun passPhrase(passPhrase: String) {
    def.passPhrase = passPhrase
  }

  /**
   * A byte used as passPhrase to encrypt/decrypt content. The passPhrase has to be provided. The
   * passPhrase needs to be put together in conjunction with the appropriate encryption algorithm. For
   * example using TRIPLEDES the passPhase can be a Only another 24 Byte key
   */
  public fun passPhraseByte(passPhraseByte: ByteArray) {
    def.passPhraseByte = passPhraseByte
  }

  /**
   * The XPath reference to the XML Element selected for encryption/decryption. If no tag is
   * specified, the entire payload is encrypted/decrypted.
   */
  public fun secureTag(secureTag: String) {
    def.secureTag = secureTag
  }

  /**
   * A boolean value to specify whether the XML Element is to be encrypted or the contents of the
   * XML Element. false = Element Level. true = Element Content Level.
   */
  public fun secureTagContents(secureTagContents: Boolean) {
    def.secureTagContents = secureTagContents.toString()
  }

  /**
   * A boolean value to specify whether the XML Element is to be encrypted or the contents of the
   * XML Element. false = Element Level. true = Element Content Level.
   */
  public fun secureTagContents(secureTagContents: String) {
    def.secureTagContents = secureTagContents
  }

  /**
   * The cipher algorithm to be used for encryption/decryption of the asymmetric key. The available
   * choices are: XMLCipher.RSA_v1dot5 XMLCipher.RSA_OAEP XMLCipher.RSA_OAEP_11 The default value is
   * XMLCipher.RSA_OAEP
   */
  public fun keyCipherAlgorithm(keyCipherAlgorithm: String) {
    def.keyCipherAlgorithm = keyCipherAlgorithm
  }

  /**
   * The key alias to be used when retrieving the recipient's public or private key from a KeyStore
   * when performing asymmetric key encryption or decryption.
   */
  public fun recipientKeyAlias(recipientKeyAlias: String) {
    def.recipientKeyAlias = recipientKeyAlias
  }

  /**
   * Refers to a KeyStore instance to lookup in the registry, which is used for configuration
   * options for creating and loading a KeyStore instance that represents the sender's trustStore or
   * recipient's keyStore.
   */
  public fun keyOrTrustStoreParametersRef(keyOrTrustStoreParametersRef: String) {
    def.keyOrTrustStoreParametersRef = keyOrTrustStoreParametersRef
  }

  /**
   * The password to be used for retrieving the private key from the KeyStore. This key is used for
   * asymmetric decryption.
   */
  public fun keyPassword(keyPassword: String) {
    def.keyPassword = keyPassword
  }

  /**
   * The digest algorithm to use with the RSA OAEP algorithm. The available choices are:
   * XMLCipher.SHA1 XMLCipher.SHA256 XMLCipher.SHA512 The default value is XMLCipher.SHA1
   */
  public fun digestAlgorithm(digestAlgorithm: String) {
    def.digestAlgorithm = digestAlgorithm
  }

  /**
   * The MGF Algorithm to use with the RSA OAEP algorithm. The available choices are:
   * EncryptionConstants.MGF1_SHA1 EncryptionConstants.MGF1_SHA256 EncryptionConstants.MGF1_SHA512 The
   * default value is EncryptionConstants.MGF1_SHA1
   */
  public fun mgfAlgorithm(mgfAlgorithm: String) {
    def.mgfAlgorithm = mgfAlgorithm
  }

  /**
   * Whether to add the public key used to encrypt the session key as a KeyValue in the EncryptedKey
   * structure or not.
   */
  public fun addKeyValueForEncryptedKey(addKeyValueForEncryptedKey: Boolean) {
    def.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey.toString()
  }

  /**
   * Whether to add the public key used to encrypt the session key as a KeyValue in the EncryptedKey
   * structure or not.
   */
  public fun addKeyValueForEncryptedKey(addKeyValueForEncryptedKey: String) {
    def.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey
  }
}
