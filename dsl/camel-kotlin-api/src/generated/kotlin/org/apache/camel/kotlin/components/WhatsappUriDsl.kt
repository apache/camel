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
 * Send messages to WhatsApp.
 */
public fun UriDsl.whatsapp(i: WhatsappUriDsl.() -> Unit) {
  WhatsappUriDsl(this).apply(i)
}

@CamelDslMarker
public class WhatsappUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("whatsapp")
  }

  private var phoneNumberId: String = ""

  /**
   * The phone number ID taken from whatsapp-business dashboard.
   */
  public fun phoneNumberId(phoneNumberId: String) {
    this.phoneNumberId = phoneNumberId
    it.url("$phoneNumberId")
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
   * Facebook graph api version.
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * Can be used to set an alternative base URI, e.g. when you want to test the component against a
   * mock WhatsApp API
   */
  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
  }

  /**
   * HttpClient implementation
   */
  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  /**
   * Webhook path
   */
  public fun webhookPath(webhookPath: String) {
    it.property("webhookPath", webhookPath)
  }

  /**
   * Webhook verify token
   */
  public fun webhookVerifyToken(webhookVerifyToken: String) {
    it.property("webhookVerifyToken", webhookVerifyToken)
  }

  /**
   * WhatsApp service implementation
   */
  public fun whatsappService(whatsappService: String) {
    it.property("whatsappService", whatsappService)
  }

  /**
   * The authorization access token taken from whatsapp-business dashboard.
   */
  public fun authorizationToken(authorizationToken: String) {
    it.property("authorizationToken", authorizationToken)
  }
}
