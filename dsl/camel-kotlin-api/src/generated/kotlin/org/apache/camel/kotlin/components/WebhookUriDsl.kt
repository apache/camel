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

/**
 * Expose webhook endpoints to receive push notifications for other Camel components.
 */
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

  /**
   * The delegate uri. Must belong to a component that supports webhooks.
   */
  public fun endpointUri(endpointUri: String) {
    this.endpointUri = endpointUri
    it.url("$endpointUri")
  }

  /**
   * Automatically register the webhook at startup and unregister it on shutdown.
   */
  public fun webhookAutoRegister(webhookAutoRegister: String) {
    it.property("webhookAutoRegister", webhookAutoRegister)
  }

  /**
   * Automatically register the webhook at startup and unregister it on shutdown.
   */
  public fun webhookAutoRegister(webhookAutoRegister: Boolean) {
    it.property("webhookAutoRegister", webhookAutoRegister.toString())
  }

  /**
   * The first (base) path element where the webhook will be exposed. It's a good practice to set it
   * to a random string, so that it cannot be guessed by unauthorized parties.
   */
  public fun webhookBasePath(webhookBasePath: String) {
    it.property("webhookBasePath", webhookBasePath)
  }

  /**
   * The Camel Rest component to use for the REST transport, such as netty-http.
   */
  public fun webhookComponentName(webhookComponentName: String) {
    it.property("webhookComponentName", webhookComponentName)
  }

  /**
   * The URL of the current service as seen by the webhook provider
   */
  public fun webhookExternalUrl(webhookExternalUrl: String) {
    it.property("webhookExternalUrl", webhookExternalUrl)
  }

  /**
   * The path where the webhook endpoint will be exposed (relative to basePath, if any)
   */
  public fun webhookPath(webhookPath: String) {
    it.property("webhookPath", webhookPath)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
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
}
