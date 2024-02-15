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
package org.apache.camel.kotlin.components

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.crypto(i: CryptoUriDsl.() -> Unit) {
  CryptoUriDsl(this).apply(i)
}

@CamelDslMarker
public class CryptoUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("crypto")
  }

  private var cryptoOperation: String = ""

  private var name: String = ""

  public fun cryptoOperation(cryptoOperation: String) {
    this.cryptoOperation = cryptoOperation
    it.url("$cryptoOperation:$name")
  }

  public fun name(name: String) {
    this.name = name
    it.url("$cryptoOperation:$name")
  }

  public fun algorithm(algorithm: String) {
    it.property("algorithm", algorithm)
  }

  public fun alias(alias: String) {
    it.property("alias", alias)
  }

  public fun certificateName(certificateName: String) {
    it.property("certificateName", certificateName)
  }

  public fun keystore(keystore: String) {
    it.property("keystore", keystore)
  }

  public fun keystoreName(keystoreName: String) {
    it.property("keystoreName", keystoreName)
  }

  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  public fun privateKeyName(privateKeyName: String) {
    it.property("privateKeyName", privateKeyName)
  }

  public fun provider(provider: String) {
    it.property("provider", provider)
  }

  public fun publicKeyName(publicKeyName: String) {
    it.property("publicKeyName", publicKeyName)
  }

  public fun secureRandomName(secureRandomName: String) {
    it.property("secureRandomName", secureRandomName)
  }

  public fun signatureHeaderName(signatureHeaderName: String) {
    it.property("signatureHeaderName", signatureHeaderName)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun bufferSize(bufferSize: String) {
    it.property("bufferSize", bufferSize)
  }

  public fun bufferSize(bufferSize: Int) {
    it.property("bufferSize", bufferSize.toString())
  }

  public fun certificate(certificate: String) {
    it.property("certificate", certificate)
  }

  public fun clearHeaders(clearHeaders: String) {
    it.property("clearHeaders", clearHeaders)
  }

  public fun clearHeaders(clearHeaders: Boolean) {
    it.property("clearHeaders", clearHeaders.toString())
  }

  public fun keyStoreParameters(keyStoreParameters: String) {
    it.property("keyStoreParameters", keyStoreParameters)
  }

  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }

  public fun secureRandom(secureRandom: String) {
    it.property("secureRandom", secureRandom)
  }

  public fun password(password: String) {
    it.property("password", password)
  }
}
