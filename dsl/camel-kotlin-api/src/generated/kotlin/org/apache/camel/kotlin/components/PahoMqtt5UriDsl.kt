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

public fun UriDsl.`paho-mqtt5`(i: PahoMqtt5UriDsl.() -> Unit) {
  PahoMqtt5UriDsl(this).apply(i)
}

@CamelDslMarker
public class PahoMqtt5UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("paho-mqtt5")
  }

  private var topic: String = ""

  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  public fun automaticReconnect(automaticReconnect: String) {
    it.property("automaticReconnect", automaticReconnect)
  }

  public fun automaticReconnect(automaticReconnect: Boolean) {
    it.property("automaticReconnect", automaticReconnect.toString())
  }

  public fun brokerUrl(brokerUrl: String) {
    it.property("brokerUrl", brokerUrl)
  }

  public fun cleanStart(cleanStart: String) {
    it.property("cleanStart", cleanStart)
  }

  public fun cleanStart(cleanStart: Boolean) {
    it.property("cleanStart", cleanStart.toString())
  }

  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun filePersistenceDirectory(filePersistenceDirectory: String) {
    it.property("filePersistenceDirectory", filePersistenceDirectory)
  }

  public fun keepAliveInterval(keepAliveInterval: String) {
    it.property("keepAliveInterval", keepAliveInterval)
  }

  public fun keepAliveInterval(keepAliveInterval: Int) {
    it.property("keepAliveInterval", keepAliveInterval.toString())
  }

  public fun maxReconnectDelay(maxReconnectDelay: String) {
    it.property("maxReconnectDelay", maxReconnectDelay)
  }

  public fun maxReconnectDelay(maxReconnectDelay: Int) {
    it.property("maxReconnectDelay", maxReconnectDelay.toString())
  }

  public fun persistence(persistence: String) {
    it.property("persistence", persistence)
  }

  public fun qos(qos: String) {
    it.property("qos", qos)
  }

  public fun qos(qos: Int) {
    it.property("qos", qos.toString())
  }

  public fun receiveMaximum(receiveMaximum: String) {
    it.property("receiveMaximum", receiveMaximum)
  }

  public fun receiveMaximum(receiveMaximum: Int) {
    it.property("receiveMaximum", receiveMaximum.toString())
  }

  public fun retained(retained: String) {
    it.property("retained", retained)
  }

  public fun retained(retained: Boolean) {
    it.property("retained", retained.toString())
  }

  public fun serverURIs(serverURIs: String) {
    it.property("serverURIs", serverURIs)
  }

  public fun sessionExpiryInterval(sessionExpiryInterval: String) {
    it.property("sessionExpiryInterval", sessionExpiryInterval)
  }

  public fun sessionExpiryInterval(sessionExpiryInterval: Int) {
    it.property("sessionExpiryInterval", sessionExpiryInterval.toString())
  }

  public fun willMqttProperties(willMqttProperties: String) {
    it.property("willMqttProperties", willMqttProperties)
  }

  public fun willPayload(willPayload: String) {
    it.property("willPayload", willPayload)
  }

  public fun willQos(willQos: String) {
    it.property("willQos", willQos)
  }

  public fun willQos(willQos: Int) {
    it.property("willQos", willQos.toString())
  }

  public fun willRetained(willRetained: String) {
    it.property("willRetained", willRetained)
  }

  public fun willRetained(willRetained: Boolean) {
    it.property("willRetained", willRetained.toString())
  }

  public fun willTopic(willTopic: String) {
    it.property("willTopic", willTopic)
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun client(client: String) {
    it.property("client", client)
  }

  public fun customWebSocketHeaders(customWebSocketHeaders: String) {
    it.property("customWebSocketHeaders", customWebSocketHeaders)
  }

  public fun executorServiceTimeout(executorServiceTimeout: String) {
    it.property("executorServiceTimeout", executorServiceTimeout)
  }

  public fun executorServiceTimeout(executorServiceTimeout: Int) {
    it.property("executorServiceTimeout", executorServiceTimeout.toString())
  }

  public fun httpsHostnameVerificationEnabled(httpsHostnameVerificationEnabled: String) {
    it.property("httpsHostnameVerificationEnabled", httpsHostnameVerificationEnabled)
  }

  public fun httpsHostnameVerificationEnabled(httpsHostnameVerificationEnabled: Boolean) {
    it.property("httpsHostnameVerificationEnabled", httpsHostnameVerificationEnabled.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun socketFactory(socketFactory: String) {
    it.property("socketFactory", socketFactory)
  }

  public fun sslClientProps(sslClientProps: String) {
    it.property("sslClientProps", sslClientProps)
  }

  public fun sslHostnameVerifier(sslHostnameVerifier: String) {
    it.property("sslHostnameVerifier", sslHostnameVerifier)
  }

  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
