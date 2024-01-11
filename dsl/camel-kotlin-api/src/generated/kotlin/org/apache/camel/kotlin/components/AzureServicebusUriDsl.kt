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

public fun UriDsl.`azure-servicebus`(i: AzureServicebusUriDsl.() -> Unit) {
  AzureServicebusUriDsl(this).apply(i)
}

@CamelDslMarker
public class AzureServicebusUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("azure-servicebus")
  }

  private var topicOrQueueName: String = ""

  public fun topicOrQueueName(topicOrQueueName: String) {
    this.topicOrQueueName = topicOrQueueName
    it.url("$topicOrQueueName")
  }

  public fun amqpRetryOptions(amqpRetryOptions: String) {
    it.property("amqpRetryOptions", amqpRetryOptions)
  }

  public fun amqpTransportType(amqpTransportType: String) {
    it.property("amqpTransportType", amqpTransportType)
  }

  public fun clientOptions(clientOptions: String) {
    it.property("clientOptions", clientOptions)
  }

  public fun proxyOptions(proxyOptions: String) {
    it.property("proxyOptions", proxyOptions)
  }

  public fun serviceBusType(serviceBusType: String) {
    it.property("serviceBusType", serviceBusType)
  }

  public fun consumerOperation(consumerOperation: String) {
    it.property("consumerOperation", consumerOperation)
  }

  public fun disableAutoComplete(disableAutoComplete: String) {
    it.property("disableAutoComplete", disableAutoComplete)
  }

  public fun disableAutoComplete(disableAutoComplete: Boolean) {
    it.property("disableAutoComplete", disableAutoComplete.toString())
  }

  public fun maxAutoLockRenewDuration(maxAutoLockRenewDuration: String) {
    it.property("maxAutoLockRenewDuration", maxAutoLockRenewDuration)
  }

  public fun peekNumMaxMessages(peekNumMaxMessages: String) {
    it.property("peekNumMaxMessages", peekNumMaxMessages)
  }

  public fun peekNumMaxMessages(peekNumMaxMessages: Int) {
    it.property("peekNumMaxMessages", peekNumMaxMessages.toString())
  }

  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
  }

  public fun receiverAsyncClient(receiverAsyncClient: String) {
    it.property("receiverAsyncClient", receiverAsyncClient)
  }

  public fun serviceBusReceiveMode(serviceBusReceiveMode: String) {
    it.property("serviceBusReceiveMode", serviceBusReceiveMode)
  }

  public fun subQueue(subQueue: String) {
    it.property("subQueue", subQueue)
  }

  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
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

  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  public fun reconnectDelay(reconnectDelay: Int) {
    it.property("reconnectDelay", reconnectDelay.toString())
  }

  public fun binary(binary: String) {
    it.property("binary", binary)
  }

  public fun binary(binary: Boolean) {
    it.property("binary", binary.toString())
  }

  public fun producerOperation(producerOperation: String) {
    it.property("producerOperation", producerOperation)
  }

  public fun scheduledEnqueueTime(scheduledEnqueueTime: String) {
    it.property("scheduledEnqueueTime", scheduledEnqueueTime)
  }

  public fun senderAsyncClient(senderAsyncClient: String) {
    it.property("senderAsyncClient", senderAsyncClient)
  }

  public fun serviceBusTransactionContext(serviceBusTransactionContext: String) {
    it.property("serviceBusTransactionContext", serviceBusTransactionContext)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun connectionString(connectionString: String) {
    it.property("connectionString", connectionString)
  }

  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  public fun fullyQualifiedNamespace(fullyQualifiedNamespace: String) {
    it.property("fullyQualifiedNamespace", fullyQualifiedNamespace)
  }

  public fun tokenCredential(tokenCredential: String) {
    it.property("tokenCredential", tokenCredential)
  }
}
