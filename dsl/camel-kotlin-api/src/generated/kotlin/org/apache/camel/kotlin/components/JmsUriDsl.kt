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
 * Sent and receive messages to/from a JMS Queue or Topic.
 */
public fun UriDsl.jms(i: JmsUriDsl.() -> Unit) {
  JmsUriDsl(this).apply(i)
}

@CamelDslMarker
public class JmsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jms")
  }

  private var destinationType: String = ""

  private var destinationName: String = ""

  /**
   * The kind of destination to use
   */
  public fun destinationType(destinationType: String) {
    this.destinationType = destinationType
    it.url("$destinationType:$destinationName")
  }

  /**
   * Name of the queue or topic to use as destination
   */
  public fun destinationName(destinationName: String) {
    this.destinationName = destinationName
    it.url("$destinationType:$destinationName")
  }

  /**
   * Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only
   * be used by a single JMS connection instance. It is typically only required for durable topic
   * subscriptions with JMS 1.1.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * The connection factory to be use. A connection factory must be configured either on the
   * component or endpoint.
   */
  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  /**
   * Specifies whether Camel ignores the JMSReplyTo header in messages. If true, Camel does not send
   * a reply back to the destination specified in the JMSReplyTo header. You can use this option if you
   * want Camel to consume from a route and you do not want Camel to automatically send back a reply
   * message because another component in your code handles the reply message. You can also use this
   * option if you want to use Camel as a proxy between different message brokers and you want to route
   * message from one system to another.
   */
  public fun disableReplyTo(disableReplyTo: String) {
    it.property("disableReplyTo", disableReplyTo)
  }

  /**
   * Specifies whether Camel ignores the JMSReplyTo header in messages. If true, Camel does not send
   * a reply back to the destination specified in the JMSReplyTo header. You can use this option if you
   * want Camel to consume from a route and you do not want Camel to automatically send back a reply
   * message because another component in your code handles the reply message. You can also use this
   * option if you want to use Camel as a proxy between different message brokers and you want to route
   * message from one system to another.
   */
  public fun disableReplyTo(disableReplyTo: Boolean) {
    it.property("disableReplyTo", disableReplyTo.toString())
  }

  /**
   * The durable subscriber name for specifying durable topic subscriptions. The clientId option
   * must be configured as well.
   */
  public fun durableSubscriptionName(durableSubscriptionName: String) {
    it.property("durableSubscriptionName", durableSubscriptionName)
  }

  /**
   * Allows you to force the use of a specific jakarta.jms.Message implementation for sending JMS
   * messages. Possible values are: Bytes, Map, Object, Stream, Text. By default, Camel would determine
   * which JMS message type to use from the In body type. This option allows you to specify it.
   */
  public fun jmsMessageType(jmsMessageType: String) {
    it.property("jmsMessageType", jmsMessageType)
  }

  /**
   * Provides an explicit ReplyTo destination (overrides any incoming value of
   * Message.getJMSReplyTo() in consumer).
   */
  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  /**
   * Specifies whether to test the connection on startup. This ensures that when Camel starts that
   * all the JMS consumers have a valid connection to the JMS broker. If a connection cannot be granted
   * then Camel throws an exception on startup. This ensures that Camel is not started with failed
   * connections. The JMS producers is tested as well.
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  /**
   * Specifies whether to test the connection on startup. This ensures that when Camel starts that
   * all the JMS consumers have a valid connection to the JMS broker. If a connection cannot be granted
   * then Camel throws an exception on startup. This ensures that Camel is not started with failed
   * connections. The JMS producers is tested as well.
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  /**
   * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE,
   * AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
   */
  public fun acknowledgementModeName(acknowledgementModeName: String) {
    it.property("acknowledgementModeName", acknowledgementModeName)
  }

  /**
   * Consumer priorities allow you to ensure that high priority consumers receive messages while
   * they are active. Normally, active consumers connected to a queue receive messages from it in a
   * round-robin fashion. When consumer priorities are in use, messages are delivered round-robin if
   * multiple active consumers exist with the same high priority. Messages will only going to lower
   * priority consumers when the high priority consumers do not have credit available to consume the
   * message, or those high priority consumers have declined to accept the message (for instance
   * because it does not meet the criteria of any selectors associated with the consumer).
   */
  public fun artemisConsumerPriority(artemisConsumerPriority: String) {
    it.property("artemisConsumerPriority", artemisConsumerPriority)
  }

  /**
   * Consumer priorities allow you to ensure that high priority consumers receive messages while
   * they are active. Normally, active consumers connected to a queue receive messages from it in a
   * round-robin fashion. When consumer priorities are in use, messages are delivered round-robin if
   * multiple active consumers exist with the same high priority. Messages will only going to lower
   * priority consumers when the high priority consumers do not have credit available to consume the
   * message, or those high priority consumers have declined to accept the message (for instance
   * because it does not meet the criteria of any selectors associated with the consumer).
   */
  public fun artemisConsumerPriority(artemisConsumerPriority: Int) {
    it.property("artemisConsumerPriority", artemisConsumerPriority.toString())
  }

  /**
   * Whether the JmsConsumer processes the Exchange asynchronously. If enabled then the JmsConsumer
   * may pickup the next message from the JMS queue, while the previous message is being processed
   * asynchronously (by the Asynchronous Routing Engine). This means that messages may be processed not
   * 100% strictly in order. If disabled (as default) then the Exchange is fully processed before the
   * JmsConsumer will pickup the next message from the JMS queue. Note if transacted has been enabled,
   * then asyncConsumer=true does not run asynchronously, as transaction must be executed synchronously
   * (Camel 3.0 may support async transactions).
   */
  public fun asyncConsumer(asyncConsumer: String) {
    it.property("asyncConsumer", asyncConsumer)
  }

  /**
   * Whether the JmsConsumer processes the Exchange asynchronously. If enabled then the JmsConsumer
   * may pickup the next message from the JMS queue, while the previous message is being processed
   * asynchronously (by the Asynchronous Routing Engine). This means that messages may be processed not
   * 100% strictly in order. If disabled (as default) then the Exchange is fully processed before the
   * JmsConsumer will pickup the next message from the JMS queue. Note if transacted has been enabled,
   * then asyncConsumer=true does not run asynchronously, as transaction must be executed synchronously
   * (Camel 3.0 may support async transactions).
   */
  public fun asyncConsumer(asyncConsumer: Boolean) {
    it.property("asyncConsumer", asyncConsumer.toString())
  }

  /**
   * Specifies whether the consumer container should auto-startup.
   */
  public fun autoStartup(autoStartup: String) {
    it.property("autoStartup", autoStartup)
  }

  /**
   * Specifies whether the consumer container should auto-startup.
   */
  public fun autoStartup(autoStartup: Boolean) {
    it.property("autoStartup", autoStartup.toString())
  }

  /**
   * Sets the cache level by ID for the underlying JMS resources. See cacheLevelName option for more
   * details.
   */
  public fun cacheLevel(cacheLevel: String) {
    it.property("cacheLevel", cacheLevel)
  }

  /**
   * Sets the cache level by ID for the underlying JMS resources. See cacheLevelName option for more
   * details.
   */
  public fun cacheLevel(cacheLevel: Int) {
    it.property("cacheLevel", cacheLevel.toString())
  }

  /**
   * Sets the cache level by name for the underlying JMS resources. Possible values are: CACHE_AUTO,
   * CACHE_CONNECTION, CACHE_CONSUMER, CACHE_NONE, and CACHE_SESSION. The default setting is
   * CACHE_AUTO. See the Spring documentation and Transactions Cache Levels for more information.
   */
  public fun cacheLevelName(cacheLevelName: String) {
    it.property("cacheLevelName", cacheLevelName)
  }

  /**
   * Specifies the default number of concurrent consumers when consuming from JMS (not for
   * request/reply over JMS). See also the maxMessagesPerTask option to control dynamic scaling up/down
   * of threads. When doing request/reply over JMS then the option replyToConcurrentConsumers is used
   * to control number of concurrent consumers on the reply message listener.
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * Specifies the default number of concurrent consumers when consuming from JMS (not for
   * request/reply over JMS). See also the maxMessagesPerTask option to control dynamic scaling up/down
   * of threads. When doing request/reply over JMS then the option replyToConcurrentConsumers is used
   * to control number of concurrent consumers on the reply message listener.
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Specifies the maximum number of concurrent consumers when consuming from JMS (not for
   * request/reply over JMS). See also the maxMessagesPerTask option to control dynamic scaling up/down
   * of threads. When doing request/reply over JMS then the option replyToMaxConcurrentConsumers is
   * used to control number of concurrent consumers on the reply message listener.
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  /**
   * Specifies the maximum number of concurrent consumers when consuming from JMS (not for
   * request/reply over JMS). See also the maxMessagesPerTask option to control dynamic scaling up/down
   * of threads. When doing request/reply over JMS then the option replyToMaxConcurrentConsumers is
   * used to control number of concurrent consumers on the reply message listener.
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  /**
   * Specifies whether to use persistent delivery by default for replies.
   */
  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: String) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent)
  }

  /**
   * Specifies whether to use persistent delivery by default for replies.
   */
  public fun replyToDeliveryPersistent(replyToDeliveryPersistent: Boolean) {
    it.property("replyToDeliveryPersistent", replyToDeliveryPersistent.toString())
  }

  /**
   * Sets the JMS selector to use
   */
  public fun selector(selector: String) {
    it.property("selector", selector)
  }

  /**
   * Set whether to make the subscription durable. The durable subscription name to be used can be
   * specified through the subscriptionName property. Default is false. Set this to true to register a
   * durable subscription, typically in combination with a subscriptionName value (unless your message
   * listener class name is good enough as subscription name). Only makes sense when listening to a
   * topic (pub-sub domain), therefore this method switches the pubSubDomain flag as well.
   */
  public fun subscriptionDurable(subscriptionDurable: String) {
    it.property("subscriptionDurable", subscriptionDurable)
  }

  /**
   * Set whether to make the subscription durable. The durable subscription name to be used can be
   * specified through the subscriptionName property. Default is false. Set this to true to register a
   * durable subscription, typically in combination with a subscriptionName value (unless your message
   * listener class name is good enough as subscription name). Only makes sense when listening to a
   * topic (pub-sub domain), therefore this method switches the pubSubDomain flag as well.
   */
  public fun subscriptionDurable(subscriptionDurable: Boolean) {
    it.property("subscriptionDurable", subscriptionDurable.toString())
  }

  /**
   * Set the name of a subscription to create. To be applied in case of a topic (pub-sub domain)
   * with a shared or durable subscription. The subscription name needs to be unique within this
   * client's JMS client id. Default is the class name of the specified message listener. Note: Only 1
   * concurrent consumer (which is the default of this message listener container) is allowed for each
   * subscription, except for a shared subscription (which requires JMS 2.0).
   */
  public fun subscriptionName(subscriptionName: String) {
    it.property("subscriptionName", subscriptionName)
  }

  /**
   * Set whether to make the subscription shared. The shared subscription name to be used can be
   * specified through the subscriptionName property. Default is false. Set this to true to register a
   * shared subscription, typically in combination with a subscriptionName value (unless your message
   * listener class name is good enough as subscription name). Note that shared subscriptions may also
   * be durable, so this flag can (and often will) be combined with subscriptionDurable as well. Only
   * makes sense when listening to a topic (pub-sub domain), therefore this method switches the
   * pubSubDomain flag as well. Requires a JMS 2.0 compatible message broker.
   */
  public fun subscriptionShared(subscriptionShared: String) {
    it.property("subscriptionShared", subscriptionShared)
  }

  /**
   * Set whether to make the subscription shared. The shared subscription name to be used can be
   * specified through the subscriptionName property. Default is false. Set this to true to register a
   * shared subscription, typically in combination with a subscriptionName value (unless your message
   * listener class name is good enough as subscription name). Note that shared subscriptions may also
   * be durable, so this flag can (and often will) be combined with subscriptionDurable as well. Only
   * makes sense when listening to a topic (pub-sub domain), therefore this method switches the
   * pubSubDomain flag as well. Requires a JMS 2.0 compatible message broker.
   */
  public fun subscriptionShared(subscriptionShared: Boolean) {
    it.property("subscriptionShared", subscriptionShared.toString())
  }

  /**
   * Specifies whether the consumer accept messages while it is stopping. You may consider enabling
   * this option, if you start and stop JMS routes at runtime, while there are still messages enqueued
   * on the queue. If this option is false, and you stop the JMS route, then messages may be rejected,
   * and the JMS broker would have to attempt redeliveries, which yet again may be rejected, and
   * eventually the message may be moved at a dead letter queue on the JMS broker. To avoid this its
   * recommended to enable this option.
   */
  public fun acceptMessagesWhileStopping(acceptMessagesWhileStopping: String) {
    it.property("acceptMessagesWhileStopping", acceptMessagesWhileStopping)
  }

  /**
   * Specifies whether the consumer accept messages while it is stopping. You may consider enabling
   * this option, if you start and stop JMS routes at runtime, while there are still messages enqueued
   * on the queue. If this option is false, and you stop the JMS route, then messages may be rejected,
   * and the JMS broker would have to attempt redeliveries, which yet again may be rejected, and
   * eventually the message may be moved at a dead letter queue on the JMS broker. To avoid this its
   * recommended to enable this option.
   */
  public fun acceptMessagesWhileStopping(acceptMessagesWhileStopping: Boolean) {
    it.property("acceptMessagesWhileStopping", acceptMessagesWhileStopping.toString())
  }

  /**
   * Whether the DefaultMessageListenerContainer used in the reply managers for request-reply
   * messaging allow the DefaultMessageListenerContainer.runningAllowed flag to quick stop in case
   * JmsConfiguration#isAcceptMessagesWhileStopping is enabled, and org.apache.camel.CamelContext is
   * currently being stopped. This quick stop ability is enabled by default in the regular JMS
   * consumers but to enable for reply managers you must enable this flag.
   */
  public fun allowReplyManagerQuickStop(allowReplyManagerQuickStop: String) {
    it.property("allowReplyManagerQuickStop", allowReplyManagerQuickStop)
  }

  /**
   * Whether the DefaultMessageListenerContainer used in the reply managers for request-reply
   * messaging allow the DefaultMessageListenerContainer.runningAllowed flag to quick stop in case
   * JmsConfiguration#isAcceptMessagesWhileStopping is enabled, and org.apache.camel.CamelContext is
   * currently being stopped. This quick stop ability is enabled by default in the regular JMS
   * consumers but to enable for reply managers you must enable this flag.
   */
  public fun allowReplyManagerQuickStop(allowReplyManagerQuickStop: Boolean) {
    it.property("allowReplyManagerQuickStop", allowReplyManagerQuickStop.toString())
  }

  /**
   * The consumer type to use, which can be one of: Simple, Default, or Custom. The consumer type
   * determines which Spring JMS listener to use. Default will use
   * org.springframework.jms.listener.DefaultMessageListenerContainer, Simple will use
   * org.springframework.jms.listener.SimpleMessageListenerContainer. When Custom is specified, the
   * MessageListenerContainerFactory defined by the messageListenerContainerFactory option will
   * determine what org.springframework.jms.listener.AbstractMessageListenerContainer to use.
   */
  public fun consumerType(consumerType: String) {
    it.property("consumerType", consumerType)
  }

  /**
   * Specifies what default TaskExecutor type to use in the DefaultMessageListenerContainer, for
   * both consumer endpoints and the ReplyTo consumer of producer endpoints. Possible values:
   * SimpleAsync (uses Spring's SimpleAsyncTaskExecutor) or ThreadPool (uses Spring's
   * ThreadPoolTaskExecutor with optimal values - cached thread-pool-like). If not set, it defaults to
   * the previous behaviour, which uses a cached thread pool for consumer endpoints and SimpleAsync for
   * reply consumers. The use of ThreadPool is recommended to reduce thread trash in elastic
   * configurations with dynamically increasing and decreasing concurrent consumers.
   */
  public fun defaultTaskExecutorType(defaultTaskExecutorType: String) {
    it.property("defaultTaskExecutorType", defaultTaskExecutorType)
  }

  /**
   * Enables eager loading of JMS properties and payload as soon as a message is loaded which
   * generally is inefficient as the JMS properties may not be required but sometimes can catch early
   * any issues with the underlying JMS provider and the use of JMS properties. See also the option
   * eagerPoisonBody.
   */
  public fun eagerLoadingOfProperties(eagerLoadingOfProperties: String) {
    it.property("eagerLoadingOfProperties", eagerLoadingOfProperties)
  }

  /**
   * Enables eager loading of JMS properties and payload as soon as a message is loaded which
   * generally is inefficient as the JMS properties may not be required but sometimes can catch early
   * any issues with the underlying JMS provider and the use of JMS properties. See also the option
   * eagerPoisonBody.
   */
  public fun eagerLoadingOfProperties(eagerLoadingOfProperties: Boolean) {
    it.property("eagerLoadingOfProperties", eagerLoadingOfProperties.toString())
  }

  /**
   * If eagerLoadingOfProperties is enabled and the JMS message payload (JMS body or JMS properties)
   * is poison (cannot be read/mapped), then set this text as the message body instead so the message
   * can be processed (the cause of the poison are already stored as exception on the Exchange). This
   * can be turned off by setting eagerPoisonBody=false. See also the option eagerLoadingOfProperties.
   */
  public fun eagerPoisonBody(eagerPoisonBody: String) {
    it.property("eagerPoisonBody", eagerPoisonBody)
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
   * Specifies whether the listener session should be exposed when consuming messages.
   */
  public fun exposeListenerSession(exposeListenerSession: String) {
    it.property("exposeListenerSession", exposeListenerSession)
  }

  /**
   * Specifies whether the listener session should be exposed when consuming messages.
   */
  public fun exposeListenerSession(exposeListenerSession: Boolean) {
    it.property("exposeListenerSession", exposeListenerSession.toString())
  }

  /**
   * The consumer type of the reply consumer (when doing request/reply), which can be one of:
   * Simple, Default, or Custom. The consumer type determines which Spring JMS listener to use. Default
   * will use org.springframework.jms.listener.DefaultMessageListenerContainer, Simple will use
   * org.springframework.jms.listener.SimpleMessageListenerContainer. When Custom is specified, the
   * MessageListenerContainerFactory defined by the messageListenerContainerFactory option will
   * determine what org.springframework.jms.listener.AbstractMessageListenerContainer to use.
   */
  public fun replyToConsumerType(replyToConsumerType: String) {
    it.property("replyToConsumerType", replyToConsumerType)
  }

  /**
   * Whether a JMS consumer is allowed to send a reply message to the same destination that the
   * consumer is using to consume from. This prevents an endless loop by consuming and sending back the
   * same message to itself.
   */
  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: String) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed)
  }

  /**
   * Whether a JMS consumer is allowed to send a reply message to the same destination that the
   * consumer is using to consume from. This prevents an endless loop by consuming and sending back the
   * same message to itself.
   */
  public fun replyToSameDestinationAllowed(replyToSameDestinationAllowed: Boolean) {
    it.property("replyToSameDestinationAllowed", replyToSameDestinationAllowed.toString())
  }

  /**
   * Allows you to specify a custom task executor for consuming messages.
   */
  public fun taskExecutor(taskExecutor: String) {
    it.property("taskExecutor", taskExecutor)
  }

  /**
   * Sets delivery delay to use for send calls for JMS. This option requires JMS 2.0 compliant
   * broker.
   */
  public fun deliveryDelay(deliveryDelay: String) {
    it.property("deliveryDelay", deliveryDelay)
  }

  /**
   * Sets delivery delay to use for send calls for JMS. This option requires JMS 2.0 compliant
   * broker.
   */
  public fun deliveryDelay(deliveryDelay: Int) {
    it.property("deliveryDelay", deliveryDelay.toString())
  }

  /**
   * Specifies the delivery mode to be used. Possible values are those defined by
   * jakarta.jms.DeliveryMode. NON_PERSISTENT = 1 and PERSISTENT = 2.
   */
  public fun deliveryMode(deliveryMode: String) {
    it.property("deliveryMode", deliveryMode)
  }

  /**
   * Specifies the delivery mode to be used. Possible values are those defined by
   * jakarta.jms.DeliveryMode. NON_PERSISTENT = 1 and PERSISTENT = 2.
   */
  public fun deliveryMode(deliveryMode: Int) {
    it.property("deliveryMode", deliveryMode.toString())
  }

  /**
   * Specifies whether persistent delivery is used by default.
   */
  public fun deliveryPersistent(deliveryPersistent: String) {
    it.property("deliveryPersistent", deliveryPersistent)
  }

  /**
   * Specifies whether persistent delivery is used by default.
   */
  public fun deliveryPersistent(deliveryPersistent: Boolean) {
    it.property("deliveryPersistent", deliveryPersistent.toString())
  }

  /**
   * Set if the deliveryMode, priority or timeToLive qualities of service should be used when
   * sending messages. This option is based on Spring's JmsTemplate. The deliveryMode, priority and
   * timeToLive options are applied to the current endpoint. This contrasts with the preserveMessageQos
   * option, which operates at message granularity, reading QoS properties exclusively from the Camel
   * In message headers.
   */
  public fun explicitQosEnabled(explicitQosEnabled: String) {
    it.property("explicitQosEnabled", explicitQosEnabled)
  }

  /**
   * Set if the deliveryMode, priority or timeToLive qualities of service should be used when
   * sending messages. This option is based on Spring's JmsTemplate. The deliveryMode, priority and
   * timeToLive options are applied to the current endpoint. This contrasts with the preserveMessageQos
   * option, which operates at message granularity, reading QoS properties exclusively from the Camel
   * In message headers.
   */
  public fun explicitQosEnabled(explicitQosEnabled: Boolean) {
    it.property("explicitQosEnabled", explicitQosEnabled.toString())
  }

  /**
   * Sets whether JMS date properties should be formatted according to the ISO 8601 standard.
   */
  public fun formatDateHeadersToIso8601(formatDateHeadersToIso8601: String) {
    it.property("formatDateHeadersToIso8601", formatDateHeadersToIso8601)
  }

  /**
   * Sets whether JMS date properties should be formatted according to the ISO 8601 standard.
   */
  public fun formatDateHeadersToIso8601(formatDateHeadersToIso8601: Boolean) {
    it.property("formatDateHeadersToIso8601", formatDateHeadersToIso8601.toString())
  }

  /**
   * Set to true, if you want to send message using the QoS settings specified on the message,
   * instead of the QoS settings on the JMS endpoint. The following three headers are considered
   * JMSPriority, JMSDeliveryMode, and JMSExpiration. You can provide all or only some of them. If not
   * provided, Camel will fall back to use the values from the endpoint instead. So, when using this
   * option, the headers override the values from the endpoint. The explicitQosEnabled option, by
   * contrast, will only use options set on the endpoint, and not values from the message header.
   */
  public fun preserveMessageQos(preserveMessageQos: String) {
    it.property("preserveMessageQos", preserveMessageQos)
  }

  /**
   * Set to true, if you want to send message using the QoS settings specified on the message,
   * instead of the QoS settings on the JMS endpoint. The following three headers are considered
   * JMSPriority, JMSDeliveryMode, and JMSExpiration. You can provide all or only some of them. If not
   * provided, Camel will fall back to use the values from the endpoint instead. So, when using this
   * option, the headers override the values from the endpoint. The explicitQosEnabled option, by
   * contrast, will only use options set on the endpoint, and not values from the message header.
   */
  public fun preserveMessageQos(preserveMessageQos: Boolean) {
    it.property("preserveMessageQos", preserveMessageQos.toString())
  }

  /**
   * Values greater than 1 specify the message priority when sending (where 1 is the lowest priority
   * and 9 is the highest). The explicitQosEnabled option must also be enabled in order for this option
   * to have any effect.
   */
  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  /**
   * Values greater than 1 specify the message priority when sending (where 1 is the lowest priority
   * and 9 is the highest). The explicitQosEnabled option must also be enabled in order for this option
   * to have any effect.
   */
  public fun priority(priority: Int) {
    it.property("priority", priority.toString())
  }

  /**
   * Specifies the default number of concurrent consumers when doing request/reply over JMS. See
   * also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
   */
  public fun replyToConcurrentConsumers(replyToConcurrentConsumers: String) {
    it.property("replyToConcurrentConsumers", replyToConcurrentConsumers)
  }

  /**
   * Specifies the default number of concurrent consumers when doing request/reply over JMS. See
   * also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
   */
  public fun replyToConcurrentConsumers(replyToConcurrentConsumers: Int) {
    it.property("replyToConcurrentConsumers", replyToConcurrentConsumers.toString())
  }

  /**
   * Specifies the maximum number of concurrent consumers when using request/reply over JMS. See
   * also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
   */
  public fun replyToMaxConcurrentConsumers(replyToMaxConcurrentConsumers: String) {
    it.property("replyToMaxConcurrentConsumers", replyToMaxConcurrentConsumers)
  }

  /**
   * Specifies the maximum number of concurrent consumers when using request/reply over JMS. See
   * also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
   */
  public fun replyToMaxConcurrentConsumers(replyToMaxConcurrentConsumers: Int) {
    it.property("replyToMaxConcurrentConsumers", replyToMaxConcurrentConsumers.toString())
  }

  /**
   * Specifies the maximum number of concurrent consumers for continue routing when timeout occurred
   * when using request/reply over JMS.
   */
  public
      fun replyToOnTimeoutMaxConcurrentConsumers(replyToOnTimeoutMaxConcurrentConsumers: String) {
    it.property("replyToOnTimeoutMaxConcurrentConsumers", replyToOnTimeoutMaxConcurrentConsumers)
  }

  /**
   * Specifies the maximum number of concurrent consumers for continue routing when timeout occurred
   * when using request/reply over JMS.
   */
  public fun replyToOnTimeoutMaxConcurrentConsumers(replyToOnTimeoutMaxConcurrentConsumers: Int) {
    it.property("replyToOnTimeoutMaxConcurrentConsumers",
        replyToOnTimeoutMaxConcurrentConsumers.toString())
  }

  /**
   * Provides an explicit ReplyTo destination in the JMS message, which overrides the setting of
   * replyTo. It is useful if you want to forward the message to a remote Queue and receive the reply
   * message from the ReplyTo destination.
   */
  public fun replyToOverride(replyToOverride: String) {
    it.property("replyToOverride", replyToOverride)
  }

  /**
   * Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing
   * request/reply over JMS. Possible values are: Temporary, Shared, or Exclusive. By default Camel
   * will use temporary queues. However if replyTo has been configured, then Shared is used by default.
   * This option allows you to use exclusive queues instead of shared ones. See Camel JMS documentation
   * for more details, and especially the notes about the implications if running in a clustered
   * environment, and the fact that Shared reply queues has lower performance than its alternatives
   * Temporary and Exclusive.
   */
  public fun replyToType(replyToType: String) {
    it.property("replyToType", replyToType)
  }

  /**
   * The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds).
   * The default is 20 seconds. You can include the header CamelJmsRequestTimeout to override this
   * endpoint configured timeout value, and thus have per message individual timeout values. See also
   * the requestTimeoutCheckerInterval option.
   */
  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  /**
   * When sending messages, specifies the time-to-live of the message (in milliseconds).
   */
  public fun timeToLive(timeToLive: String) {
    it.property("timeToLive", timeToLive)
  }

  /**
   * When sending messages, specifies the time-to-live of the message (in milliseconds).
   */
  public fun timeToLive(timeToLive: Int) {
    it.property("timeToLive", timeToLive.toString())
  }

  /**
   * This option is used to allow additional headers which may have values that are invalid
   * according to JMS specification. For example, some message systems, such as WMQ, do this with
   * header names using prefix JMS_IBM_MQMD_ containing values with byte array or other invalid types.
   * You can specify multiple header names separated by comma, and use as suffix for wildcard matching.
   */
  public fun allowAdditionalHeaders(allowAdditionalHeaders: String) {
    it.property("allowAdditionalHeaders", allowAdditionalHeaders)
  }

  /**
   * Whether to allow sending messages with no body. If this option is false and the message body is
   * null, then an JMSException is thrown.
   */
  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  /**
   * Whether to allow sending messages with no body. If this option is false and the message body is
   * null, then an JMSException is thrown.
   */
  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  /**
   * If true, Camel will always make a JMS message copy of the message when it is passed to the
   * producer for sending. Copying the message is needed in some situations, such as when a
   * replyToDestinationSelectorName is set (incidentally, Camel will set the alwaysCopyMessage option
   * to true, if a replyToDestinationSelectorName is set)
   */
  public fun alwaysCopyMessage(alwaysCopyMessage: String) {
    it.property("alwaysCopyMessage", alwaysCopyMessage)
  }

  /**
   * If true, Camel will always make a JMS message copy of the message when it is passed to the
   * producer for sending. Copying the message is needed in some situations, such as when a
   * replyToDestinationSelectorName is set (incidentally, Camel will set the alwaysCopyMessage option
   * to true, if a replyToDestinationSelectorName is set)
   */
  public fun alwaysCopyMessage(alwaysCopyMessage: Boolean) {
    it.property("alwaysCopyMessage", alwaysCopyMessage.toString())
  }

  /**
   * When using InOut exchange pattern use this JMS property instead of JMSCorrelationID JMS
   * property to correlate messages. If set messages will be correlated solely on the value of this
   * property JMSCorrelationID property will be ignored and not set by Camel.
   */
  public fun correlationProperty(correlationProperty: String) {
    it.property("correlationProperty", correlationProperty)
  }

  /**
   * Use this option to force disabling time to live. For example when you do request/reply over
   * JMS, then Camel will by default use the requestTimeout value as time to live on the message being
   * sent. The problem is that the sender and receiver systems have to have their clocks synchronized,
   * so they are in sync. This is not always so easy to archive. So you can use disableTimeToLive=true
   * to not set a time to live value on the sent message. Then the message will not expire on the
   * receiver system. See below in section About time to live for more details.
   */
  public fun disableTimeToLive(disableTimeToLive: String) {
    it.property("disableTimeToLive", disableTimeToLive)
  }

  /**
   * Use this option to force disabling time to live. For example when you do request/reply over
   * JMS, then Camel will by default use the requestTimeout value as time to live on the message being
   * sent. The problem is that the sender and receiver systems have to have their clocks synchronized,
   * so they are in sync. This is not always so easy to archive. So you can use disableTimeToLive=true
   * to not set a time to live value on the sent message. Then the message will not expire on the
   * receiver system. See below in section About time to live for more details.
   */
  public fun disableTimeToLive(disableTimeToLive: Boolean) {
    it.property("disableTimeToLive", disableTimeToLive.toString())
  }

  /**
   * When using mapJmsMessage=false Camel will create a new JMS message to send to a new JMS
   * destination if you touch the headers (get or set) during the route. Set this option to true to
   * force Camel to send the original JMS message that was received.
   */
  public fun forceSendOriginalMessage(forceSendOriginalMessage: String) {
    it.property("forceSendOriginalMessage", forceSendOriginalMessage)
  }

  /**
   * When using mapJmsMessage=false Camel will create a new JMS message to send to a new JMS
   * destination if you touch the headers (get or set) during the route. Set this option to true to
   * force Camel to send the original JMS message that was received.
   */
  public fun forceSendOriginalMessage(forceSendOriginalMessage: Boolean) {
    it.property("forceSendOriginalMessage", forceSendOriginalMessage.toString())
  }

  /**
   * Only applicable when sending to JMS destination using InOnly (eg fire and forget). Enabling
   * this option will enrich the Camel Exchange with the actual JMSMessageID that was used by the JMS
   * client when the message was sent to the JMS destination.
   */
  public fun includeSentJMSMessageID(includeSentJMSMessageID: String) {
    it.property("includeSentJMSMessageID", includeSentJMSMessageID)
  }

  /**
   * Only applicable when sending to JMS destination using InOnly (eg fire and forget). Enabling
   * this option will enrich the Camel Exchange with the actual JMSMessageID that was used by the JMS
   * client when the message was sent to the JMS destination.
   */
  public fun includeSentJMSMessageID(includeSentJMSMessageID: Boolean) {
    it.property("includeSentJMSMessageID", includeSentJMSMessageID.toString())
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
   * Sets the cache level by name for the reply consumer when doing request/reply over JMS. This
   * option only applies when using fixed reply queues (not temporary). Camel will by default use:
   * CACHE_CONSUMER for exclusive or shared w/ replyToSelectorName. And CACHE_SESSION for shared
   * without replyToSelectorName. Some JMS brokers such as IBM WebSphere may require to set the
   * replyToCacheLevelName=CACHE_NONE to work. Note: If using temporary queues then CACHE_NONE is not
   * allowed, and you must use a higher value such as CACHE_CONSUMER or CACHE_SESSION.
   */
  public fun replyToCacheLevelName(replyToCacheLevelName: String) {
    it.property("replyToCacheLevelName", replyToCacheLevelName)
  }

  /**
   * Sets the JMS Selector using the fixed name to be used so you can filter out your own replies
   * from the others when using a shared queue (that is, if you are not using a temporary reply queue).
   */
  public fun replyToDestinationSelectorName(replyToDestinationSelectorName: String) {
    it.property("replyToDestinationSelectorName", replyToDestinationSelectorName)
  }

  /**
   * Sets whether StreamMessage type is enabled or not. Message payloads of streaming kind such as
   * files, InputStream, etc will either by sent as BytesMessage or StreamMessage. This option controls
   * which kind will be used. By default BytesMessage is used which enforces the entire message payload
   * to be read into memory. By enabling this option the message payload is read into memory in chunks
   * and each chunk is then written to the StreamMessage until no more data.
   */
  public fun streamMessageTypeEnabled(streamMessageTypeEnabled: String) {
    it.property("streamMessageTypeEnabled", streamMessageTypeEnabled)
  }

  /**
   * Sets whether StreamMessage type is enabled or not. Message payloads of streaming kind such as
   * files, InputStream, etc will either by sent as BytesMessage or StreamMessage. This option controls
   * which kind will be used. By default BytesMessage is used which enforces the entire message payload
   * to be read into memory. By enabling this option the message payload is read into memory in chunks
   * and each chunk is then written to the StreamMessage until no more data.
   */
  public fun streamMessageTypeEnabled(streamMessageTypeEnabled: Boolean) {
    it.property("streamMessageTypeEnabled", streamMessageTypeEnabled.toString())
  }

  /**
   * Controls whether or not to include serialized headers. Applies only when transferExchange is
   * true. This requires that the objects are serializable. Camel will exclude any non-serializable
   * objects and log it at WARN level.
   */
  public fun allowSerializedHeaders(allowSerializedHeaders: String) {
    it.property("allowSerializedHeaders", allowSerializedHeaders)
  }

  /**
   * Controls whether or not to include serialized headers. Applies only when transferExchange is
   * true. This requires that the objects are serializable. Camel will exclude any non-serializable
   * objects and log it at WARN level.
   */
  public fun allowSerializedHeaders(allowSerializedHeaders: Boolean) {
    it.property("allowSerializedHeaders", allowSerializedHeaders.toString())
  }

  /**
   * Whether optimizing for Apache Artemis streaming mode. This can reduce memory overhead when
   * using Artemis with JMS StreamMessage types. This option must only be enabled if Apache Artemis is
   * being used.
   */
  public fun artemisStreamingEnabled(artemisStreamingEnabled: String) {
    it.property("artemisStreamingEnabled", artemisStreamingEnabled)
  }

  /**
   * Whether optimizing for Apache Artemis streaming mode. This can reduce memory overhead when
   * using Artemis with JMS StreamMessage types. This option must only be enabled if Apache Artemis is
   * being used.
   */
  public fun artemisStreamingEnabled(artemisStreamingEnabled: Boolean) {
    it.property("artemisStreamingEnabled", artemisStreamingEnabled.toString())
  }

  /**
   * Whether to startup the JmsConsumer message listener asynchronously, when starting a route. For
   * example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while
   * retrying and/or fail-over. This will cause Camel to block while starting routes. By setting this
   * option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker
   * using a dedicated thread in asynchronous mode. If this option is used, then beware that if the
   * connection could not be established, then an exception is logged at WARN level, and the consumer
   * will not be able to receive messages; You can then restart the route to retry.
   */
  public fun asyncStartListener(asyncStartListener: String) {
    it.property("asyncStartListener", asyncStartListener)
  }

  /**
   * Whether to startup the JmsConsumer message listener asynchronously, when starting a route. For
   * example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while
   * retrying and/or fail-over. This will cause Camel to block while starting routes. By setting this
   * option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker
   * using a dedicated thread in asynchronous mode. If this option is used, then beware that if the
   * connection could not be established, then an exception is logged at WARN level, and the consumer
   * will not be able to receive messages; You can then restart the route to retry.
   */
  public fun asyncStartListener(asyncStartListener: Boolean) {
    it.property("asyncStartListener", asyncStartListener.toString())
  }

  /**
   * Whether to stop the JmsConsumer message listener asynchronously, when stopping a route.
   */
  public fun asyncStopListener(asyncStopListener: String) {
    it.property("asyncStopListener", asyncStopListener)
  }

  /**
   * Whether to stop the JmsConsumer message listener asynchronously, when stopping a route.
   */
  public fun asyncStopListener(asyncStopListener: Boolean) {
    it.property("asyncStopListener", asyncStopListener.toString())
  }

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: String) {
    it.property("browseLimit", browseLimit)
  }

  /**
   * Maximum number of messages to keep in memory available for browsing. Use 0 for unlimited.
   */
  public fun browseLimit(browseLimit: Int) {
    it.property("browseLimit", browseLimit.toString())
  }

  /**
   * A pluggable org.springframework.jms.support.destination.DestinationResolver that allows you to
   * use your own resolver (for example, to lookup the real destination in a JNDI registry).
   */
  public fun destinationResolver(destinationResolver: String) {
    it.property("destinationResolver", destinationResolver)
  }

  /**
   * Specifies a org.springframework.util.ErrorHandler to be invoked in case of any uncaught
   * exceptions thrown while processing a Message. By default these exceptions will be logged at the
   * WARN level, if no errorHandler has been configured. You can configure logging level and whether
   * stack traces should be logged using errorHandlerLoggingLevel and errorHandlerLogStackTrace
   * options. This makes it much easier to configure, than having to code a custom errorHandler.
   */
  public fun errorHandler(errorHandler: String) {
    it.property("errorHandler", errorHandler)
  }

  /**
   * Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.
   */
  public fun exceptionListener(exceptionListener: String) {
    it.property("exceptionListener", exceptionListener)
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Specify the limit for the number of consumers that are allowed to be idle at any given time.
   */
  public fun idleConsumerLimit(idleConsumerLimit: String) {
    it.property("idleConsumerLimit", idleConsumerLimit)
  }

  /**
   * Specify the limit for the number of consumers that are allowed to be idle at any given time.
   */
  public fun idleConsumerLimit(idleConsumerLimit: Int) {
    it.property("idleConsumerLimit", idleConsumerLimit.toString())
  }

  /**
   * Specifies the limit for idle executions of a receive task, not having received any message
   * within its execution. If this limit is reached, the task will shut down and leave receiving to
   * other executing tasks (in the case of dynamic scheduling; see the maxConcurrentConsumers setting).
   * There is additional doc available from Spring.
   */
  public fun idleTaskExecutionLimit(idleTaskExecutionLimit: String) {
    it.property("idleTaskExecutionLimit", idleTaskExecutionLimit)
  }

  /**
   * Specifies the limit for idle executions of a receive task, not having received any message
   * within its execution. If this limit is reached, the task will shut down and leave receiving to
   * other executing tasks (in the case of dynamic scheduling; see the maxConcurrentConsumers setting).
   * There is additional doc available from Spring.
   */
  public fun idleTaskExecutionLimit(idleTaskExecutionLimit: Int) {
    it.property("idleTaskExecutionLimit", idleTaskExecutionLimit.toString())
  }

  /**
   * Whether to include all JMSX prefixed properties when mapping from JMS to Camel Message. Setting
   * this to true will include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using
   * a custom headerFilterStrategy then this option does not apply.
   */
  public fun includeAllJMSXProperties(includeAllJMSXProperties: String) {
    it.property("includeAllJMSXProperties", includeAllJMSXProperties)
  }

  /**
   * Whether to include all JMSX prefixed properties when mapping from JMS to Camel Message. Setting
   * this to true will include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using
   * a custom headerFilterStrategy then this option does not apply.
   */
  public fun includeAllJMSXProperties(includeAllJMSXProperties: Boolean) {
    it.property("includeAllJMSXProperties", includeAllJMSXProperties.toString())
  }

  /**
   * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS
   * specification. Camel provides two implementations out of the box: default and passthrough. The
   * default strategy will safely marshal dots and hyphens (. and -). The passthrough strategy leaves
   * the key as is. Can be used for JMS brokers which do not care whether JMS header keys contain
   * illegal characters. You can provide your own implementation of the
   * org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.
   */
  public fun jmsKeyFormatStrategy(jmsKeyFormatStrategy: String) {
    it.property("jmsKeyFormatStrategy", jmsKeyFormatStrategy)
  }

  /**
   * Specifies whether Camel should auto map the received JMS message to a suited payload type, such
   * as jakarta.jms.TextMessage to a String etc.
   */
  public fun mapJmsMessage(mapJmsMessage: String) {
    it.property("mapJmsMessage", mapJmsMessage)
  }

  /**
   * Specifies whether Camel should auto map the received JMS message to a suited payload type, such
   * as jakarta.jms.TextMessage to a String etc.
   */
  public fun mapJmsMessage(mapJmsMessage: Boolean) {
    it.property("mapJmsMessage", mapJmsMessage.toString())
  }

  /**
   * The number of messages per task. -1 is unlimited. If you use a range for concurrent consumers
   * (eg min max), then this option can be used to set a value to eg 100 to control how fast the
   * consumers will shrink when less work is required.
   */
  public fun maxMessagesPerTask(maxMessagesPerTask: String) {
    it.property("maxMessagesPerTask", maxMessagesPerTask)
  }

  /**
   * The number of messages per task. -1 is unlimited. If you use a range for concurrent consumers
   * (eg min max), then this option can be used to set a value to eg 100 to control how fast the
   * consumers will shrink when less work is required.
   */
  public fun maxMessagesPerTask(maxMessagesPerTask: Int) {
    it.property("maxMessagesPerTask", maxMessagesPerTask.toString())
  }

  /**
   * To use a custom Spring org.springframework.jms.support.converter.MessageConverter so you can be
   * in control how to map to/from a jakarta.jms.Message.
   */
  public fun messageConverter(messageConverter: String) {
    it.property("messageConverter", messageConverter)
  }

  /**
   * To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of
   * jakarta.jms.Message objects when Camel is sending a JMS message.
   */
  public fun messageCreatedStrategy(messageCreatedStrategy: String) {
    it.property("messageCreatedStrategy", messageCreatedStrategy)
  }

  /**
   * When sending, specifies whether message IDs should be added. This is just an hint to the JMS
   * broker. If the JMS provider accepts this hint, these messages must have the message ID set to
   * null; if the provider ignores the hint, the message ID must be set to its normal unique value.
   */
  public fun messageIdEnabled(messageIdEnabled: String) {
    it.property("messageIdEnabled", messageIdEnabled)
  }

  /**
   * When sending, specifies whether message IDs should be added. This is just an hint to the JMS
   * broker. If the JMS provider accepts this hint, these messages must have the message ID set to
   * null; if the provider ignores the hint, the message ID must be set to its normal unique value.
   */
  public fun messageIdEnabled(messageIdEnabled: Boolean) {
    it.property("messageIdEnabled", messageIdEnabled.toString())
  }

  /**
   * Registry ID of the MessageListenerContainerFactory used to determine what
   * org.springframework.jms.listener.AbstractMessageListenerContainer to use to consume messages.
   * Setting this will automatically set consumerType to Custom.
   */
  public fun messageListenerContainerFactory(messageListenerContainerFactory: String) {
    it.property("messageListenerContainerFactory", messageListenerContainerFactory)
  }

  /**
   * Specifies whether timestamps should be enabled by default on sending messages. This is just an
   * hint to the JMS broker. If the JMS provider accepts this hint, these messages must have the
   * timestamp set to zero; if the provider ignores the hint the timestamp must be set to its normal
   * value.
   */
  public fun messageTimestampEnabled(messageTimestampEnabled: String) {
    it.property("messageTimestampEnabled", messageTimestampEnabled)
  }

  /**
   * Specifies whether timestamps should be enabled by default on sending messages. This is just an
   * hint to the JMS broker. If the JMS provider accepts this hint, these messages must have the
   * timestamp set to zero; if the provider ignores the hint the timestamp must be set to its normal
   * value.
   */
  public fun messageTimestampEnabled(messageTimestampEnabled: Boolean) {
    it.property("messageTimestampEnabled", messageTimestampEnabled.toString())
  }

  /**
   * Specifies whether to inhibit the delivery of messages published by its own connection.
   */
  public fun pubSubNoLocal(pubSubNoLocal: String) {
    it.property("pubSubNoLocal", pubSubNoLocal)
  }

  /**
   * Specifies whether to inhibit the delivery of messages published by its own connection.
   */
  public fun pubSubNoLocal(pubSubNoLocal: Boolean) {
    it.property("pubSubNoLocal", pubSubNoLocal.toString())
  }

  /**
   * The timeout for receiving messages (in milliseconds).
   */
  public fun receiveTimeout(receiveTimeout: String) {
    it.property("receiveTimeout", receiveTimeout)
  }

  /**
   * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in
   * milliseconds. The default is 5000 ms, that is, 5 seconds.
   */
  public fun recoveryInterval(recoveryInterval: String) {
    it.property("recoveryInterval", recoveryInterval)
  }

  /**
   * Configures how often Camel should check for timed out Exchanges when doing request/reply over
   * JMS. By default Camel checks once per second. But if you must react faster when a timeout occurs,
   * then you can lower this interval, to check more frequently. The timeout is determined by the
   * option requestTimeout.
   */
  public fun requestTimeoutCheckerInterval(requestTimeoutCheckerInterval: String) {
    it.property("requestTimeoutCheckerInterval", requestTimeoutCheckerInterval)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * A pluggable TemporaryQueueResolver that allows you to use your own resolver for creating
   * temporary queues (some messaging systems has special requirements for creating temporary queues).
   */
  public fun temporaryQueueResolver(temporaryQueueResolver: String) {
    it.property("temporaryQueueResolver", temporaryQueueResolver)
  }

  /**
   * If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the
   * consumer side, then the caused Exception will be send back in response as a
   * jakarta.jms.ObjectMessage. If the client is Camel, the returned Exception is rethrown. This allows
   * you to use Camel JMS as a bridge in your routing - for example, using persistent queues to enable
   * robust routing. Notice that if you also have transferExchange enabled, this option takes
   * precedence. The caught exception is required to be serializable. The original Exception on the
   * consumer side can be wrapped in an outer exception such as org.apache.camel.RuntimeCamelException
   * when returned to the producer. Use this with caution as the data is using Java Object
   * serialization and requires the received to be able to deserialize the data at Class level, which
   * forces a strong coupling between the producers and consumer!
   */
  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  /**
   * If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the
   * consumer side, then the caused Exception will be send back in response as a
   * jakarta.jms.ObjectMessage. If the client is Camel, the returned Exception is rethrown. This allows
   * you to use Camel JMS as a bridge in your routing - for example, using persistent queues to enable
   * robust routing. Notice that if you also have transferExchange enabled, this option takes
   * precedence. The caught exception is required to be serializable. The original Exception on the
   * consumer side can be wrapped in an outer exception such as org.apache.camel.RuntimeCamelException
   * when returned to the producer. Use this with caution as the data is using Java Object
   * serialization and requires the received to be able to deserialize the data at Class level, which
   * forces a strong coupling between the producers and consumer!
   */
  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  /**
   * You can transfer the exchange over the wire instead of just the body and headers. The following
   * fields are transferred: In body, Out body, Fault body, In headers, Out headers, Fault headers,
   * exchange properties, exchange exception. This requires that the objects are serializable. Camel
   * will exclude any non-serializable objects and log it at WARN level. You must enable this option on
   * both the producer and consumer side, so Camel knows the payloads is an Exchange and not a regular
   * payload. Use this with caution as the data is using Java Object serialization and requires the
   * receiver to be able to deserialize the data at Class level, which forces a strong coupling between
   * the producers and consumers having to use compatible Camel versions!
   */
  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  /**
   * You can transfer the exchange over the wire instead of just the body and headers. The following
   * fields are transferred: In body, Out body, Fault body, In headers, Out headers, Fault headers,
   * exchange properties, exchange exception. This requires that the objects are serializable. Camel
   * will exclude any non-serializable objects and log it at WARN level. You must enable this option on
   * both the producer and consumer side, so Camel knows the payloads is an Exchange and not a regular
   * payload. Use this with caution as the data is using Java Object serialization and requires the
   * receiver to be able to deserialize the data at Class level, which forces a strong coupling between
   * the producers and consumers having to use compatible Camel versions!
   */
  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  /**
   * Specifies whether JMSMessageID should always be used as JMSCorrelationID for InOut messages.
   */
  public fun useMessageIDAsCorrelationID(useMessageIDAsCorrelationID: String) {
    it.property("useMessageIDAsCorrelationID", useMessageIDAsCorrelationID)
  }

  /**
   * Specifies whether JMSMessageID should always be used as JMSCorrelationID for InOut messages.
   */
  public fun useMessageIDAsCorrelationID(useMessageIDAsCorrelationID: Boolean) {
    it.property("useMessageIDAsCorrelationID", useMessageIDAsCorrelationID.toString())
  }

  /**
   * Number of times to wait for provisional correlation id to be updated to the actual correlation
   * id when doing request/reply over JMS and when the option useMessageIDAsCorrelationID is enabled.
   */
  public
      fun waitForProvisionCorrelationToBeUpdatedCounter(waitForProvisionCorrelationToBeUpdatedCounter: String) {
    it.property("waitForProvisionCorrelationToBeUpdatedCounter",
        waitForProvisionCorrelationToBeUpdatedCounter)
  }

  /**
   * Number of times to wait for provisional correlation id to be updated to the actual correlation
   * id when doing request/reply over JMS and when the option useMessageIDAsCorrelationID is enabled.
   */
  public
      fun waitForProvisionCorrelationToBeUpdatedCounter(waitForProvisionCorrelationToBeUpdatedCounter: Int) {
    it.property("waitForProvisionCorrelationToBeUpdatedCounter",
        waitForProvisionCorrelationToBeUpdatedCounter.toString())
  }

  /**
   * Interval in millis to sleep each time while waiting for provisional correlation id to be
   * updated.
   */
  public
      fun waitForProvisionCorrelationToBeUpdatedThreadSleepingTime(waitForProvisionCorrelationToBeUpdatedThreadSleepingTime: String) {
    it.property("waitForProvisionCorrelationToBeUpdatedThreadSleepingTime",
        waitForProvisionCorrelationToBeUpdatedThreadSleepingTime)
  }

  /**
   * Number of times to wait for temporary replyTo queue to be created and ready when doing
   * request/reply over JMS.
   */
  public
      fun waitForTemporaryReplyToToBeUpdatedCounter(waitForTemporaryReplyToToBeUpdatedCounter: String) {
    it.property("waitForTemporaryReplyToToBeUpdatedCounter",
        waitForTemporaryReplyToToBeUpdatedCounter)
  }

  /**
   * Number of times to wait for temporary replyTo queue to be created and ready when doing
   * request/reply over JMS.
   */
  public
      fun waitForTemporaryReplyToToBeUpdatedCounter(waitForTemporaryReplyToToBeUpdatedCounter: Int) {
    it.property("waitForTemporaryReplyToToBeUpdatedCounter",
        waitForTemporaryReplyToToBeUpdatedCounter.toString())
  }

  /**
   * Interval in millis to sleep each time while waiting for temporary replyTo queue to be ready.
   */
  public
      fun waitForTemporaryReplyToToBeUpdatedThreadSleepingTime(waitForTemporaryReplyToToBeUpdatedThreadSleepingTime: String) {
    it.property("waitForTemporaryReplyToToBeUpdatedThreadSleepingTime",
        waitForTemporaryReplyToToBeUpdatedThreadSleepingTime)
  }

  /**
   * Allows to configure the default errorHandler logging level for logging uncaught exceptions.
   */
  public fun errorHandlerLoggingLevel(errorHandlerLoggingLevel: String) {
    it.property("errorHandlerLoggingLevel", errorHandlerLoggingLevel)
  }

  /**
   * Allows to control whether stack-traces should be logged or not, by the default errorHandler.
   */
  public fun errorHandlerLogStackTrace(errorHandlerLogStackTrace: String) {
    it.property("errorHandlerLogStackTrace", errorHandlerLogStackTrace)
  }

  /**
   * Allows to control whether stack-traces should be logged or not, by the default errorHandler.
   */
  public fun errorHandlerLogStackTrace(errorHandlerLogStackTrace: Boolean) {
    it.property("errorHandlerLogStackTrace", errorHandlerLogStackTrace.toString())
  }

  /**
   * Password to use with the ConnectionFactory. You can also configure username/password directly
   * on the ConnectionFactory.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Username to use with the ConnectionFactory. You can also configure username/password directly
   * on the ConnectionFactory.
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * Specifies whether to use transacted mode
   */
  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  /**
   * Specifies whether to use transacted mode
   */
  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  /**
   * Specifies whether InOut operations (request reply) default to using transacted mode If this
   * flag is set to true, then Spring JmsTemplate will have sessionTransacted set to true, and the
   * acknowledgeMode as transacted on the JmsTemplate used for InOut operations. Note from Spring JMS:
   * that within a JTA transaction, the parameters passed to createQueue, createTopic methods are not
   * taken into account. Depending on the Java EE transaction context, the container makes its own
   * decisions on these values. Analogously, these parameters are not taken into account within a
   * locally managed transaction either, since Spring JMS operates on an existing JMS Session in this
   * case. Setting this flag to true will use a short local JMS transaction when running outside of a
   * managed transaction, and a synchronized local JMS transaction in case of a managed transaction
   * (other than an XA transaction) being present. This has the effect of a local JMS transaction being
   * managed alongside the main transaction (which might be a native JDBC transaction), with the JMS
   * transaction committing right after the main transaction.
   */
  public fun transactedInOut(transactedInOut: String) {
    it.property("transactedInOut", transactedInOut)
  }

  /**
   * Specifies whether InOut operations (request reply) default to using transacted mode If this
   * flag is set to true, then Spring JmsTemplate will have sessionTransacted set to true, and the
   * acknowledgeMode as transacted on the JmsTemplate used for InOut operations. Note from Spring JMS:
   * that within a JTA transaction, the parameters passed to createQueue, createTopic methods are not
   * taken into account. Depending on the Java EE transaction context, the container makes its own
   * decisions on these values. Analogously, these parameters are not taken into account within a
   * locally managed transaction either, since Spring JMS operates on an existing JMS Session in this
   * case. Setting this flag to true will use a short local JMS transaction when running outside of a
   * managed transaction, and a synchronized local JMS transaction in case of a managed transaction
   * (other than an XA transaction) being present. This has the effect of a local JMS transaction being
   * managed alongside the main transaction (which might be a native JDBC transaction), with the JMS
   * transaction committing right after the main transaction.
   */
  public fun transactedInOut(transactedInOut: Boolean) {
    it.property("transactedInOut", transactedInOut.toString())
  }

  /**
   * If true, Camel will create a JmsTransactionManager, if there is no transactionManager injected
   * when option transacted=true.
   */
  public fun lazyCreateTransactionManager(lazyCreateTransactionManager: String) {
    it.property("lazyCreateTransactionManager", lazyCreateTransactionManager)
  }

  /**
   * If true, Camel will create a JmsTransactionManager, if there is no transactionManager injected
   * when option transacted=true.
   */
  public fun lazyCreateTransactionManager(lazyCreateTransactionManager: Boolean) {
    it.property("lazyCreateTransactionManager", lazyCreateTransactionManager.toString())
  }

  /**
   * The Spring transaction manager to use.
   */
  public fun transactionManager(transactionManager: String) {
    it.property("transactionManager", transactionManager)
  }

  /**
   * The name of the transaction to use.
   */
  public fun transactionName(transactionName: String) {
    it.property("transactionName", transactionName)
  }

  /**
   * The timeout value of the transaction (in seconds), if using transacted mode.
   */
  public fun transactionTimeout(transactionTimeout: String) {
    it.property("transactionTimeout", transactionTimeout)
  }

  /**
   * The timeout value of the transaction (in seconds), if using transacted mode.
   */
  public fun transactionTimeout(transactionTimeout: Int) {
    it.property("transactionTimeout", transactionTimeout.toString())
  }
}
