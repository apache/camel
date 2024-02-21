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

public fun UriDsl.`google-pubsub`(i: GooglePubsubUriDsl.() -> Unit) {
  GooglePubsubUriDsl(this).apply(i)
}

@CamelDslMarker
public class GooglePubsubUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("google-pubsub")
  }

  private var projectId: String = ""

  private var destinationName: String = ""

  public fun projectId(projectId: String) {
    this.projectId = projectId
    it.url("$projectId:$destinationName")
  }

  public fun destinationName(destinationName: String) {
    this.destinationName = destinationName
    it.url("$projectId:$destinationName")
  }

  public fun authenticate(authenticate: String) {
    it.property("authenticate", authenticate)
  }

  public fun authenticate(authenticate: Boolean) {
    it.property("authenticate", authenticate.toString())
  }

  public fun loggerId(loggerId: String) {
    it.property("loggerId", loggerId)
  }

  public fun serviceAccountKey(serviceAccountKey: String) {
    it.property("serviceAccountKey", serviceAccountKey)
  }

  public fun ackMode(ackMode: String) {
    it.property("ackMode", ackMode)
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun maxAckExtensionPeriod(maxAckExtensionPeriod: String) {
    it.property("maxAckExtensionPeriod", maxAckExtensionPeriod)
  }

  public fun maxAckExtensionPeriod(maxAckExtensionPeriod: Int) {
    it.property("maxAckExtensionPeriod", maxAckExtensionPeriod.toString())
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun synchronousPull(synchronousPull: String) {
    it.property("synchronousPull", synchronousPull)
  }

  public fun synchronousPull(synchronousPull: Boolean) {
    it.property("synchronousPull", synchronousPull.toString())
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

  public fun messageOrderingEnabled(messageOrderingEnabled: String) {
    it.property("messageOrderingEnabled", messageOrderingEnabled)
  }

  public fun messageOrderingEnabled(messageOrderingEnabled: Boolean) {
    it.property("messageOrderingEnabled", messageOrderingEnabled.toString())
  }

  public fun pubsubEndpoint(pubsubEndpoint: String) {
    it.property("pubsubEndpoint", pubsubEndpoint)
  }

  public fun serializer(serializer: String) {
    it.property("serializer", serializer)
  }
}
