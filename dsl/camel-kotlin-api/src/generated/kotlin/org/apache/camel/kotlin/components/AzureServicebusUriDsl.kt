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

/**
 * Send and receive messages to/from Azure Service Bus.
 */
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

  /**
   * Selected topic name or the queue name, that is depending on serviceBusType config. For example
   * if serviceBusType=queue, then this will be the queue name and if serviceBusType=topic, this will
   * be the topic name.
   */
  public fun topicOrQueueName(topicOrQueueName: String) {
    this.topicOrQueueName = topicOrQueueName
    it.url("$topicOrQueueName")
  }

  /**
   * Sets the retry options for Service Bus clients. If not specified, the default retry options are
   * used.
   */
  public fun amqpRetryOptions(amqpRetryOptions: String) {
    it.property("amqpRetryOptions", amqpRetryOptions)
  }

  /**
   * Sets the transport type by which all the communication with Azure Service Bus occurs. Default
   * value is AMQP.
   */
  public fun amqpTransportType(amqpTransportType: String) {
    it.property("amqpTransportType", amqpTransportType)
  }

  /**
   * Sets the ClientOptions to be sent from the client built from this builder, enabling
   * customization of certain properties, as well as support the addition of custom header information.
   */
  public fun clientOptions(clientOptions: String) {
    it.property("clientOptions", clientOptions)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter Service Bus application properties to and from
   * Camel message headers.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Sets the proxy configuration to use for ServiceBusSenderClient. When a proxy is configured,
   * AMQP_WEB_SOCKETS must be used for the transport type.
   */
  public fun proxyOptions(proxyOptions: String) {
    it.property("proxyOptions", proxyOptions)
  }

  /**
   * The service bus type of connection to execute. Queue is for typical queue option and topic for
   * subscription based model.
   */
  public fun serviceBusType(serviceBusType: String) {
    it.property("serviceBusType", serviceBusType)
  }

  /**
   * Enable application level deadlettering to the subscription deadletter subqueue if deadletter
   * related headers are set.
   */
  public fun enableDeadLettering(enableDeadLettering: String) {
    it.property("enableDeadLettering", enableDeadLettering)
  }

  /**
   * Enable application level deadlettering to the subscription deadletter subqueue if deadletter
   * related headers are set.
   */
  public fun enableDeadLettering(enableDeadLettering: Boolean) {
    it.property("enableDeadLettering", enableDeadLettering.toString())
  }

  /**
   * Sets the amount of time to continue auto-renewing the lock. Setting ZERO disables auto-renewal.
   * For ServiceBus receive mode (RECEIVE_AND_DELETE RECEIVE_AND_DELETE), auto-renewal is disabled.
   */
  public fun maxAutoLockRenewDuration(maxAutoLockRenewDuration: String) {
    it.property("maxAutoLockRenewDuration", maxAutoLockRenewDuration)
  }

  /**
   * Sets maximum number of concurrent calls
   */
  public fun maxConcurrentCalls(maxConcurrentCalls: String) {
    it.property("maxConcurrentCalls", maxConcurrentCalls)
  }

  /**
   * Sets maximum number of concurrent calls
   */
  public fun maxConcurrentCalls(maxConcurrentCalls: Int) {
    it.property("maxConcurrentCalls", maxConcurrentCalls.toString())
  }

  /**
   * Sets the prefetch count of the receiver. For both PEEK_LOCK PEEK_LOCK and RECEIVE_AND_DELETE
   * RECEIVE_AND_DELETE receive modes the default value is 1. Prefetch speeds up the message flow by
   * aiming to have a message readily available for local retrieval when and before the application
   * asks for one using receive message. Setting a non-zero value will prefetch that number of
   * messages. Setting the value to zero turns prefetch off.
   */
  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  /**
   * Sets the prefetch count of the receiver. For both PEEK_LOCK PEEK_LOCK and RECEIVE_AND_DELETE
   * RECEIVE_AND_DELETE receive modes the default value is 1. Prefetch speeds up the message flow by
   * aiming to have a message readily available for local retrieval when and before the application
   * asks for one using receive message. Setting a non-zero value will prefetch that number of
   * messages. Setting the value to zero turns prefetch off.
   */
  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
  }

  /**
   * Sets the processorClient in order to consume messages by the consumer
   */
  public fun processorClient(processorClient: String) {
    it.property("processorClient", processorClient)
  }

  /**
   * Sets the receive mode for the receiver.
   */
  public fun serviceBusReceiveMode(serviceBusReceiveMode: String) {
    it.property("serviceBusReceiveMode", serviceBusReceiveMode)
  }

  /**
   * Sets the type of the SubQueue to connect to.
   */
  public fun subQueue(subQueue: String) {
    it.property("subQueue", subQueue)
  }

  /**
   * Sets the name of the subscription in the topic to listen to. topicOrQueueName and
   * serviceBusType=topic must also be set. This property is required if serviceBusType=topic and the
   * consumer is in use.
   */
  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
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

  /**
   * Set binary mode. If true, message body will be sent as byte. By default, it is false.
   */
  public fun binary(binary: String) {
    it.property("binary", binary)
  }

  /**
   * Set binary mode. If true, message body will be sent as byte. By default, it is false.
   */
  public fun binary(binary: Boolean) {
    it.property("binary", binary.toString())
  }

  /**
   * Sets the desired operation to be used in the producer
   */
  public fun producerOperation(producerOperation: String) {
    it.property("producerOperation", producerOperation)
  }

  /**
   * Sets OffsetDateTime at which the message should appear in the Service Bus queue or topic.
   */
  public fun scheduledEnqueueTime(scheduledEnqueueTime: String) {
    it.property("scheduledEnqueueTime", scheduledEnqueueTime)
  }

  /**
   * Sets senderClient to be used in the producer.
   */
  public fun senderClient(senderClient: String) {
    it.property("senderClient", senderClient)
  }

  /**
   * Represents transaction in service. This object just contains transaction id.
   */
  public fun serviceBusTransactionContext(serviceBusTransactionContext: String) {
    it.property("serviceBusTransactionContext", serviceBusTransactionContext)
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
   * Sets the connection string for a Service Bus namespace or a specific Service Bus resource.
   */
  public fun connectionString(connectionString: String) {
    it.property("connectionString", connectionString)
  }

  /**
   * Determines the credential strategy to adopt
   */
  public fun credentialType(credentialType: String) {
    it.property("credentialType", credentialType)
  }

  /**
   * Fully Qualified Namespace of the service bus
   */
  public fun fullyQualifiedNamespace(fullyQualifiedNamespace: String) {
    it.property("fullyQualifiedNamespace", fullyQualifiedNamespace)
  }

  /**
   * A TokenCredential for Azure AD authentication.
   */
  public fun tokenCredential(tokenCredential: String) {
    it.property("tokenCredential", tokenCredential)
  }
}
