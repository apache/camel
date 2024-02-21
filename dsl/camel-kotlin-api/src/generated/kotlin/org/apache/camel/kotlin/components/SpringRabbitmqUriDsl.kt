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

public fun UriDsl.`spring-rabbitmq`(i: SpringRabbitmqUriDsl.() -> Unit) {
  SpringRabbitmqUriDsl(this).apply(i)
}

@CamelDslMarker
public class SpringRabbitmqUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("spring-rabbitmq")
  }

  private var exchangeName: String = ""

  public fun exchangeName(exchangeName: String) {
    this.exchangeName = exchangeName
    it.url("$exchangeName")
  }

  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  public fun disableReplyTo(disableReplyTo: String) {
    it.property("disableReplyTo", disableReplyTo)
  }

  public fun disableReplyTo(disableReplyTo: Boolean) {
    it.property("disableReplyTo", disableReplyTo.toString())
  }

  public fun routingKey(routingKey: String) {
    it.property("routingKey", routingKey)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  public fun acknowledgeMode(acknowledgeMode: String) {
    it.property("acknowledgeMode", acknowledgeMode)
  }

  public fun asyncConsumer(asyncConsumer: String) {
    it.property("asyncConsumer", asyncConsumer)
  }

  public fun asyncConsumer(asyncConsumer: Boolean) {
    it.property("asyncConsumer", asyncConsumer.toString())
  }

  public fun autoDeclare(autoDeclare: String) {
    it.property("autoDeclare", autoDeclare)
  }

  public fun autoDeclare(autoDeclare: Boolean) {
    it.property("autoDeclare", autoDeclare.toString())
  }

  public fun autoStartup(autoStartup: String) {
    it.property("autoStartup", autoStartup)
  }

  public fun autoStartup(autoStartup: Boolean) {
    it.property("autoStartup", autoStartup.toString())
  }

  public fun deadLetterExchange(deadLetterExchange: String) {
    it.property("deadLetterExchange", deadLetterExchange)
  }

  public fun deadLetterExchangeType(deadLetterExchangeType: String) {
    it.property("deadLetterExchangeType", deadLetterExchangeType)
  }

  public fun deadLetterQueue(deadLetterQueue: String) {
    it.property("deadLetterQueue", deadLetterQueue)
  }

  public fun deadLetterRoutingKey(deadLetterRoutingKey: String) {
    it.property("deadLetterRoutingKey", deadLetterRoutingKey)
  }

  public fun exchangeType(exchangeType: String) {
    it.property("exchangeType", exchangeType)
  }

  public fun exclusive(exclusive: String) {
    it.property("exclusive", exclusive)
  }

  public fun exclusive(exclusive: Boolean) {
    it.property("exclusive", exclusive.toString())
  }

  public fun maximumRetryAttempts(maximumRetryAttempts: String) {
    it.property("maximumRetryAttempts", maximumRetryAttempts)
  }

  public fun maximumRetryAttempts(maximumRetryAttempts: Int) {
    it.property("maximumRetryAttempts", maximumRetryAttempts.toString())
  }

  public fun noLocal(noLocal: String) {
    it.property("noLocal", noLocal)
  }

  public fun noLocal(noLocal: Boolean) {
    it.property("noLocal", noLocal.toString())
  }

  public fun queues(queues: String) {
    it.property("queues", queues)
  }

  public fun rejectAndDontRequeue(rejectAndDontRequeue: String) {
    it.property("rejectAndDontRequeue", rejectAndDontRequeue)
  }

  public fun rejectAndDontRequeue(rejectAndDontRequeue: Boolean) {
    it.property("rejectAndDontRequeue", rejectAndDontRequeue.toString())
  }

  public fun retryDelay(retryDelay: String) {
    it.property("retryDelay", retryDelay)
  }

  public fun retryDelay(retryDelay: Int) {
    it.property("retryDelay", retryDelay.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  public fun messageListenerContainerType(messageListenerContainerType: String) {
    it.property("messageListenerContainerType", messageListenerContainerType)
  }

  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
  }

  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  public fun confirm(confirm: String) {
    it.property("confirm", confirm)
  }

  public fun confirmTimeout(confirmTimeout: String) {
    it.property("confirmTimeout", confirmTimeout)
  }

  public fun replyTimeout(replyTimeout: String) {
    it.property("replyTimeout", replyTimeout)
  }

  public fun usePublisherConnection(usePublisherConnection: String) {
    it.property("usePublisherConnection", usePublisherConnection)
  }

  public fun usePublisherConnection(usePublisherConnection: Boolean) {
    it.property("usePublisherConnection", usePublisherConnection.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun args(args: String) {
    it.property("args", args)
  }

  public fun messageConverter(messageConverter: String) {
    it.property("messageConverter", messageConverter)
  }

  public fun messagePropertiesConverter(messagePropertiesConverter: String) {
    it.property("messagePropertiesConverter", messagePropertiesConverter)
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }
}
