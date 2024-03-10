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
 * Upload, download and manage files, folders, groups, collaborations, etc. on box.com.
 */
public fun UriDsl.box(i: BoxUriDsl.() -> Unit) {
  BoxUriDsl(this).apply(i)
}

@CamelDslMarker
public class BoxUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("box")
  }

  private var apiName: String = ""

  private var methodName: String = ""

  /**
   * What kind of operation to perform
   */
  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  /**
   * What sub operation to use for the selected operation
   */
  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  /**
   * Box application client ID
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * The enterprise ID to use for an App Enterprise.
   */
  public fun enterpriseId(enterpriseId: String) {
    it.property("enterpriseId", enterpriseId)
  }

  /**
   * Sets the name of a parameter to be passed in the exchange In Body
   */
  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  /**
   * The user ID to use for an App User.
   */
  public fun userId(userId: String) {
    it.property("userId", userId)
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
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
   * Custom HTTP params for settings like proxy host
   */
  public fun httpParams(httpParams: String) {
    it.property("httpParams", httpParams)
  }

  /**
   * The type of authentication for connection. Types of Authentication: STANDARD_AUTHENTICATION -
   * OAuth 2.0 (3-legged) SERVER_AUTHENTICATION - OAuth 2.0 with JSON Web Tokens
   */
  public fun authenticationType(authenticationType: String) {
    it.property("authenticationType", authenticationType)
  }

  /**
   * Custom Access Token Cache for storing and retrieving access tokens.
   */
  public fun accessTokenCache(accessTokenCache: String) {
    it.property("accessTokenCache", accessTokenCache)
  }

  /**
   * Box application client secret
   */
  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  /**
   * The type of encryption algorithm for JWT. Supported Algorithms: RSA_SHA_256 RSA_SHA_384
   * RSA_SHA_512
   */
  public fun encryptionAlgorithm(encryptionAlgorithm: String) {
    it.property("encryptionAlgorithm", encryptionAlgorithm)
  }

  /**
   * The maximum number of access tokens in cache.
   */
  public fun maxCacheEntries(maxCacheEntries: String) {
    it.property("maxCacheEntries", maxCacheEntries)
  }

  /**
   * The maximum number of access tokens in cache.
   */
  public fun maxCacheEntries(maxCacheEntries: Int) {
    it.property("maxCacheEntries", maxCacheEntries.toString())
  }

  /**
   * The private key for generating the JWT signature.
   */
  public fun privateKeyFile(privateKeyFile: String) {
    it.property("privateKeyFile", privateKeyFile)
  }

  /**
   * The password for the private key.
   */
  public fun privateKeyPassword(privateKeyPassword: String) {
    it.property("privateKeyPassword", privateKeyPassword)
  }

  /**
   * The ID for public key for validating the JWT signature.
   */
  public fun publicKeyId(publicKeyId: String) {
    it.property("publicKeyId", publicKeyId)
  }

  /**
   * To configure security using SSLContextParameters.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * Box user name, MUST be provided
   */
  public fun userName(userName: String) {
    it.property("userName", userName)
  }

  /**
   * Box user password, MUST be provided if authSecureStorage is not set, or returns null on first
   * call
   */
  public fun userPassword(userPassword: String) {
    it.property("userPassword", userPassword)
  }
}
