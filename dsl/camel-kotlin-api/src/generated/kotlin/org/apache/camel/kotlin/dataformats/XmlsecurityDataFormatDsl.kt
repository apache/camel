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

public fun DataFormatDsl.xmlSecurity(i: XmlsecurityDataFormatDsl.() -> Unit) {
  def = XmlsecurityDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class XmlsecurityDataFormatDsl {
  public val def: XMLSecurityDataFormat

  init {
    def = XMLSecurityDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun xmlCipherAlgorithm(xmlCipherAlgorithm: String) {
    def.xmlCipherAlgorithm = xmlCipherAlgorithm
  }

  public fun passPhrase(passPhrase: String) {
    def.passPhrase = passPhrase
  }

  public fun passPhraseByte(passPhraseByte: ByteArray) {
    def.passPhraseByte = passPhraseByte
  }

  public fun secureTag(secureTag: String) {
    def.secureTag = secureTag
  }

  public fun secureTagContents(secureTagContents: Boolean) {
    def.secureTagContents = secureTagContents.toString()
  }

  public fun secureTagContents(secureTagContents: String) {
    def.secureTagContents = secureTagContents
  }

  public fun keyCipherAlgorithm(keyCipherAlgorithm: String) {
    def.keyCipherAlgorithm = keyCipherAlgorithm
  }

  public fun recipientKeyAlias(recipientKeyAlias: String) {
    def.recipientKeyAlias = recipientKeyAlias
  }

  public fun keyOrTrustStoreParametersRef(keyOrTrustStoreParametersRef: String) {
    def.keyOrTrustStoreParametersRef = keyOrTrustStoreParametersRef
  }

  public fun keyPassword(keyPassword: String) {
    def.keyPassword = keyPassword
  }

  public fun digestAlgorithm(digestAlgorithm: String) {
    def.digestAlgorithm = digestAlgorithm
  }

  public fun mgfAlgorithm(mgfAlgorithm: String) {
    def.mgfAlgorithm = mgfAlgorithm
  }

  public fun addKeyValueForEncryptedKey(addKeyValueForEncryptedKey: Boolean) {
    def.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey.toString()
  }

  public fun addKeyValueForEncryptedKey(addKeyValueForEncryptedKey: String) {
    def.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey
  }
}
