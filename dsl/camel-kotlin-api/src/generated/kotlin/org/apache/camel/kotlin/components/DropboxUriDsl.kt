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

public fun UriDsl.dropbox(i: DropboxUriDsl.() -> Unit) {
  DropboxUriDsl(this).apply(i)
}

@CamelDslMarker
public class DropboxUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dropbox")
  }

  private var operation: String = ""

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun clientIdentifier(clientIdentifier: String) {
    it.property("clientIdentifier", clientIdentifier)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun remotePath(remotePath: String) {
    it.property("remotePath", remotePath)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun localPath(localPath: String) {
    it.property("localPath", localPath)
  }

  public fun newRemotePath(newRemotePath: String) {
    it.property("newRemotePath", newRemotePath)
  }

  public fun uploadMode(uploadMode: String) {
    it.property("uploadMode", uploadMode)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun client(client: String) {
    it.property("client", client)
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun apiKey(apiKey: String) {
    it.property("apiKey", apiKey)
  }

  public fun apiSecret(apiSecret: String) {
    it.property("apiSecret", apiSecret)
  }

  public fun expireIn(expireIn: String) {
    it.property("expireIn", expireIn)
  }

  public fun expireIn(expireIn: Int) {
    it.property("expireIn", expireIn.toString())
  }

  public fun refreshToken(refreshToken: String) {
    it.property("refreshToken", refreshToken)
  }
}
