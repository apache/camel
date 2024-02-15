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

  public fun phoneNumberId(phoneNumberId: String) {
    this.phoneNumberId = phoneNumberId
    it.url("$phoneNumberId")
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun baseUri(baseUri: String) {
    it.property("baseUri", baseUri)
  }

  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  public fun webhookPath(webhookPath: String) {
    it.property("webhookPath", webhookPath)
  }

  public fun webhookVerifyToken(webhookVerifyToken: String) {
    it.property("webhookVerifyToken", webhookVerifyToken)
  }

  public fun whatsappService(whatsappService: String) {
    it.property("whatsappService", whatsappService)
  }

  public fun authorizationToken(authorizationToken: String) {
    it.property("authorizationToken", authorizationToken)
  }
}
