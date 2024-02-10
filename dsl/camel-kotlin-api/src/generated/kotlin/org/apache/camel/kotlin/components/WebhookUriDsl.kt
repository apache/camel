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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.webhook(i: WebhookUriDsl.() -> Unit) {
  WebhookUriDsl(this).apply(i)
}

@CamelDslMarker
public class WebhookUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("webhook")
  }

  private var endpointUri: String = ""

  public fun endpointUri(endpointUri: String) {
    this.endpointUri = endpointUri
    it.url("$endpointUri")
  }

  public fun webhookAutoRegister(webhookAutoRegister: String) {
    it.property("webhookAutoRegister", webhookAutoRegister)
  }

  public fun webhookAutoRegister(webhookAutoRegister: Boolean) {
    it.property("webhookAutoRegister", webhookAutoRegister.toString())
  }

  public fun webhookBasePath(webhookBasePath: String) {
    it.property("webhookBasePath", webhookBasePath)
  }

  public fun webhookComponentName(webhookComponentName: String) {
    it.property("webhookComponentName", webhookComponentName)
  }

  public fun webhookExternalUrl(webhookExternalUrl: String) {
    it.property("webhookExternalUrl", webhookExternalUrl)
  }

  public fun webhookPath(webhookPath: String) {
    it.property("webhookPath", webhookPath)
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
}
