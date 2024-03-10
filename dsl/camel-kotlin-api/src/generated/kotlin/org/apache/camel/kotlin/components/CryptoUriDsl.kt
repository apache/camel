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

/**
 * Sign and verify exchanges using the Signature Service of the Java Cryptographic Extension (JCE).
 */
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

  /**
   * Set the Crypto operation from that supplied after the crypto scheme in the endpoint uri e.g.
   * crypto:sign sets sign as the operation.
   */
  public fun cryptoOperation(cryptoOperation: String) {
    this.cryptoOperation = cryptoOperation
    it.url("$cryptoOperation:$name")
  }

  /**
   * The logical name of this operation.
   */
  public fun name(name: String) {
    this.name = name
    it.url("$cryptoOperation:$name")
  }

  /**
   * Sets the JCE name of the Algorithm that should be used for the signer.
   */
  public fun algorithm(algorithm: String) {
    it.property("algorithm", algorithm)
  }

  /**
   * Sets the alias used to query the KeyStore for keys and {link java.security.cert.Certificate
   * Certificates} to be used in signing and verifying exchanges. This value can be provided at runtime
   * via the message header org.apache.camel.component.crypto.DigitalSignatureConstants#KEYSTORE_ALIAS
   */
  public fun alias(alias: String) {
    it.property("alias", alias)
  }

  /**
   * Sets the reference name for a PrivateKey that can be found in the registry.
   */
  public fun certificateName(certificateName: String) {
    it.property("certificateName", certificateName)
  }

  /**
   * Sets the KeyStore that can contain keys and Certficates for use in signing and verifying
   * exchanges. A KeyStore is typically used with an alias, either one supplied in the Route definition
   * or dynamically via the message header CamelSignatureKeyStoreAlias. If no alias is supplied and
   * there is only a single entry in the Keystore, then this single entry will be used.
   */
  public fun keystore(keystore: String) {
    it.property("keystore", keystore)
  }

  /**
   * Sets the reference name for a Keystore that can be found in the registry.
   */
  public fun keystoreName(keystoreName: String) {
    it.property("keystoreName", keystoreName)
  }

  /**
   * Set the PrivateKey that should be used to sign the exchange
   */
  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  /**
   * Sets the reference name for a PrivateKey that can be found in the registry.
   */
  public fun privateKeyName(privateKeyName: String) {
    it.property("privateKeyName", privateKeyName)
  }

  /**
   * Set the id of the security provider that provides the configured Signature algorithm.
   */
  public fun provider(provider: String) {
    it.property("provider", provider)
  }

  /**
   * references that should be resolved when the context changes
   */
  public fun publicKeyName(publicKeyName: String) {
    it.property("publicKeyName", publicKeyName)
  }

  /**
   * Sets the reference name for a SecureRandom that can be found in the registry.
   */
  public fun secureRandomName(secureRandomName: String) {
    it.property("secureRandomName", secureRandomName)
  }

  /**
   * Set the name of the message header that should be used to store the base64 encoded signature.
   * This defaults to 'CamelDigitalSignature'
   */
  public fun signatureHeaderName(signatureHeaderName: String) {
    it.property("signatureHeaderName", signatureHeaderName)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * Set the size of the buffer used to read in the Exchange payload data.
   */
  public fun bufferSize(bufferSize: String) {
    it.property("bufferSize", bufferSize)
  }

  /**
   * Set the size of the buffer used to read in the Exchange payload data.
   */
  public fun bufferSize(bufferSize: Int) {
    it.property("bufferSize", bufferSize.toString())
  }

  /**
   * Set the Certificate that should be used to verify the signature in the exchange based on its
   * payload.
   */
  public fun certificate(certificate: String) {
    it.property("certificate", certificate)
  }

  /**
   * Determines if the Signature specific headers be cleared after signing and verification.
   * Defaults to true, and should only be made otherwise at your extreme peril as vital private
   * information such as Keys and passwords may escape if unset.
   */
  public fun clearHeaders(clearHeaders: String) {
    it.property("clearHeaders", clearHeaders)
  }

  /**
   * Determines if the Signature specific headers be cleared after signing and verification.
   * Defaults to true, and should only be made otherwise at your extreme peril as vital private
   * information such as Keys and passwords may escape if unset.
   */
  public fun clearHeaders(clearHeaders: Boolean) {
    it.property("clearHeaders", clearHeaders.toString())
  }

  /**
   * Sets the KeyStore that can contain keys and Certficates for use in signing and verifying
   * exchanges based on the given KeyStoreParameters. A KeyStore is typically used with an alias,
   * either one supplied in the Route definition or dynamically via the message header
   * CamelSignatureKeyStoreAlias. If no alias is supplied and there is only a single entry in the
   * Keystore, then this single entry will be used.
   */
  public fun keyStoreParameters(keyStoreParameters: String) {
    it.property("keyStoreParameters", keyStoreParameters)
  }

  /**
   * Set the PublicKey that should be used to verify the signature in the exchange.
   */
  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }

  /**
   * Set the SecureRandom used to initialize the Signature service
   */
  public fun secureRandom(secureRandom: String) {
    it.property("secureRandom", secureRandom)
  }

  /**
   * Sets the password used to access an aliased PrivateKey in the KeyStore.
   */
  public fun password(password: String) {
    it.property("password", password)
  }
}
