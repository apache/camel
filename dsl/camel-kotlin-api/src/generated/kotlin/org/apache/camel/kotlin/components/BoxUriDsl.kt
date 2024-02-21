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

  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun enterpriseId(enterpriseId: String) {
    it.property("enterpriseId", enterpriseId)
  }

  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  public fun userId(userId: String) {
    it.property("userId", userId)
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun httpParams(httpParams: String) {
    it.property("httpParams", httpParams)
  }

  public fun authenticationType(authenticationType: String) {
    it.property("authenticationType", authenticationType)
  }

  public fun accessTokenCache(accessTokenCache: String) {
    it.property("accessTokenCache", accessTokenCache)
  }

  public fun clientSecret(clientSecret: String) {
    it.property("clientSecret", clientSecret)
  }

  public fun encryptionAlgorithm(encryptionAlgorithm: String) {
    it.property("encryptionAlgorithm", encryptionAlgorithm)
  }

  public fun maxCacheEntries(maxCacheEntries: String) {
    it.property("maxCacheEntries", maxCacheEntries)
  }

  public fun maxCacheEntries(maxCacheEntries: Int) {
    it.property("maxCacheEntries", maxCacheEntries.toString())
  }

  public fun privateKeyFile(privateKeyFile: String) {
    it.property("privateKeyFile", privateKeyFile)
  }

  public fun privateKeyPassword(privateKeyPassword: String) {
    it.property("privateKeyPassword", privateKeyPassword)
  }

  public fun publicKeyId(publicKeyId: String) {
    it.property("publicKeyId", publicKeyId)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun userName(userName: String) {
    it.property("userName", userName)
  }

  public fun userPassword(userPassword: String) {
    it.property("userPassword", userPassword)
  }
}
