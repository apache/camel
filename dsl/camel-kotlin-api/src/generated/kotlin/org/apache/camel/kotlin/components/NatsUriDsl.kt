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

public fun UriDsl.nats(i: NatsUriDsl.() -> Unit) {
  NatsUriDsl(this).apply(i)
}

@CamelDslMarker
public class NatsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("nats")
  }

  private var topic: String = ""

  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun flushConnection(flushConnection: String) {
    it.property("flushConnection", flushConnection)
  }

  public fun flushConnection(flushConnection: Boolean) {
    it.property("flushConnection", flushConnection.toString())
  }

  public fun flushTimeout(flushTimeout: String) {
    it.property("flushTimeout", flushTimeout)
  }

  public fun flushTimeout(flushTimeout: Int) {
    it.property("flushTimeout", flushTimeout.toString())
  }

  public fun maxPingsOut(maxPingsOut: String) {
    it.property("maxPingsOut", maxPingsOut)
  }

  public fun maxPingsOut(maxPingsOut: Int) {
    it.property("maxPingsOut", maxPingsOut.toString())
  }

  public fun maxReconnectAttempts(maxReconnectAttempts: String) {
    it.property("maxReconnectAttempts", maxReconnectAttempts)
  }

  public fun maxReconnectAttempts(maxReconnectAttempts: Int) {
    it.property("maxReconnectAttempts", maxReconnectAttempts.toString())
  }

  public fun noEcho(noEcho: String) {
    it.property("noEcho", noEcho)
  }

  public fun noEcho(noEcho: Boolean) {
    it.property("noEcho", noEcho.toString())
  }

  public fun noRandomizeServers(noRandomizeServers: String) {
    it.property("noRandomizeServers", noRandomizeServers)
  }

  public fun noRandomizeServers(noRandomizeServers: Boolean) {
    it.property("noRandomizeServers", noRandomizeServers.toString())
  }

  public fun pedantic(pedantic: String) {
    it.property("pedantic", pedantic)
  }

  public fun pedantic(pedantic: Boolean) {
    it.property("pedantic", pedantic.toString())
  }

  public fun pingInterval(pingInterval: String) {
    it.property("pingInterval", pingInterval)
  }

  public fun pingInterval(pingInterval: Int) {
    it.property("pingInterval", pingInterval.toString())
  }

  public fun reconnect(reconnect: String) {
    it.property("reconnect", reconnect)
  }

  public fun reconnect(reconnect: Boolean) {
    it.property("reconnect", reconnect.toString())
  }

  public fun reconnectTimeWait(reconnectTimeWait: String) {
    it.property("reconnectTimeWait", reconnectTimeWait)
  }

  public fun reconnectTimeWait(reconnectTimeWait: Int) {
    it.property("reconnectTimeWait", reconnectTimeWait.toString())
  }

  public fun requestCleanupInterval(requestCleanupInterval: String) {
    it.property("requestCleanupInterval", requestCleanupInterval)
  }

  public fun requestCleanupInterval(requestCleanupInterval: Int) {
    it.property("requestCleanupInterval", requestCleanupInterval.toString())
  }

  public fun servers(servers: String) {
    it.property("servers", servers)
  }

  public fun verbose(verbose: String) {
    it.property("verbose", verbose)
  }

  public fun verbose(verbose: Boolean) {
    it.property("verbose", verbose.toString())
  }

  public fun maxMessages(maxMessages: String) {
    it.property("maxMessages", maxMessages)
  }

  public fun poolSize(poolSize: String) {
    it.property("poolSize", poolSize)
  }

  public fun poolSize(poolSize: Int) {
    it.property("poolSize", poolSize.toString())
  }

  public fun queueName(queueName: String) {
    it.property("queueName", queueName)
  }

  public fun replyToDisabled(replyToDisabled: String) {
    it.property("replyToDisabled", replyToDisabled)
  }

  public fun replyToDisabled(replyToDisabled: Boolean) {
    it.property("replyToDisabled", replyToDisabled.toString())
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

  public fun replySubject(replySubject: String) {
    it.property("replySubject", replySubject)
  }

  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun connection(connection: String) {
    it.property("connection", connection)
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun traceConnection(traceConnection: String) {
    it.property("traceConnection", traceConnection)
  }

  public fun traceConnection(traceConnection: Boolean) {
    it.property("traceConnection", traceConnection.toString())
  }

  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
