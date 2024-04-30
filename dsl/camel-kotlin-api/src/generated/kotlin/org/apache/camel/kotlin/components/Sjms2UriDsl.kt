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
 * Send and receive messages to/from a JMS Queue or Topic using plain JMS 2.x API.
 */
public fun UriDsl.sjms2(i: Sjms2UriDsl.() -> Unit) {
  Sjms2UriDsl(this).apply(i)
}

@CamelDslMarker
public class Sjms2UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sjms2")
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
   * DestinationName is a JMS queue or topic name. By default, the destinationName is interpreted as
   * a queue name.
   */
  public fun destinationName(destinationName: String) {
    this.destinationName = destinationName
    it.url("$destinationType:$destinationName")
  }

  /**
   * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE,
   * AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
   */
  public fun acknowledgementMode(acknowledgementMode: String) {
    it.property("acknowledgementMode", acknowledgementMode)
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
   * Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only
   * be used by a single JMS connection instance. It is typically only required for durable topic
   * subscriptions. If using Apache ActiveMQ you may prefer to use Virtual Topics instead.
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
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
   * Sets the topic to be durable
   */
  public fun durable(durable: String) {
    it.property("durable", durable)
  }

  /**
   * Sets the topic to be durable
   */
  public fun durable(durable: Boolean) {
    it.property("durable", durable.toString())
  }

  /**
   * The durable subscriber name for specifying durable topic subscriptions. The clientId option
   * must be configured as well.
   */
  public fun durableSubscriptionName(durableSubscriptionName: String) {
    it.property("durableSubscriptionName", durableSubscriptionName)
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
   * Sets the topic to be shared
   */
  public fun shared(shared: String) {
    it.property("shared", shared)
  }

  /**
   * Sets the topic to be shared
   */
  public fun shared(shared: Boolean) {
    it.property("shared", shared.toString())
  }

  /**
   * Sets the topic subscription id, required for durable or shared topics.
   */
  public fun subscriptionId(subscriptionId: String) {
    it.property("subscriptionId", subscriptionId)
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
   * Sets the JMS Message selector syntax.
   */
  public fun messageSelector(messageSelector: String) {
    it.property("messageSelector", messageSelector)
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
   * Provides an explicit ReplyTo destination in the JMS message, which overrides the setting of
   * replyTo. It is useful if you want to forward the message to a remote Queue and receive the reply
   * message from the ReplyTo destination.
   */
  public fun replyToOverride(replyToOverride: String) {
    it.property("replyToOverride", replyToOverride)
  }

  /**
   * Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing
   * request/reply over JMS. Possible values are: Temporary or Exclusive. By default Camel will use
   * temporary queues. However if replyTo has been configured, then Exclusive is used.
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
   * Whether to startup the consumer message listener asynchronously, when starting a route. For
   * example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while
   * retrying and/or fail over. This will cause Camel to block while starting routes. By setting this
   * option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker
   * using a dedicated thread in asynchronous mode. If this option is used, then beware that if the
   * connection could not be established, then an exception is logged at WARN level, and the consumer
   * will not be able to receive messages; You can then restart the route to retry.
   */
  public fun asyncStartListener(asyncStartListener: String) {
    it.property("asyncStartListener", asyncStartListener)
  }

  /**
   * Whether to startup the consumer message listener asynchronously, when starting a route. For
   * example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while
   * retrying and/or fail over. This will cause Camel to block while starting routes. By setting this
   * option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker
   * using a dedicated thread in asynchronous mode. If this option is used, then beware that if the
   * connection could not be established, then an exception is logged at WARN level, and the consumer
   * will not be able to receive messages; You can then restart the route to retry.
   */
  public fun asyncStartListener(asyncStartListener: Boolean) {
    it.property("asyncStartListener", asyncStartListener.toString())
  }

  /**
   * Whether to stop the consumer message listener asynchronously, when stopping a route.
   */
  public fun asyncStopListener(asyncStopListener: String) {
    it.property("asyncStopListener", asyncStopListener)
  }

  /**
   * Whether to stop the consumer message listener asynchronously, when stopping a route.
   */
  public fun asyncStopListener(asyncStopListener: Boolean) {
    it.property("asyncStopListener", asyncStopListener.toString())
  }

  /**
   * To use a custom DestinationCreationStrategy.
   */
  public fun destinationCreationStrategy(destinationCreationStrategy: String) {
    it.property("destinationCreationStrategy", destinationCreationStrategy)
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
   * Whether to include all JMSXxxx properties when mapping from JMS to Camel Message. Setting this
   * to true will include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using a
   * custom headerFilterStrategy then this option does not apply.
   */
  public fun includeAllJMSXProperties(includeAllJMSXProperties: String) {
    it.property("includeAllJMSXProperties", includeAllJMSXProperties)
  }

  /**
   * Whether to include all JMSXxxx properties when mapping from JMS to Camel Message. Setting this
   * to true will include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using a
   * custom headerFilterStrategy then this option does not apply.
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
   * as jakarta.jms.TextMessage to a String etc. See section about how mapping works below for more
   * details.
   */
  public fun mapJmsMessage(mapJmsMessage: String) {
    it.property("mapJmsMessage", mapJmsMessage)
  }

  /**
   * Specifies whether Camel should auto map the received JMS message to a suited payload type, such
   * as jakarta.jms.TextMessage to a String etc. See section about how mapping works below for more
   * details.
   */
  public fun mapJmsMessage(mapJmsMessage: Boolean) {
    it.property("mapJmsMessage", mapJmsMessage.toString())
  }

  /**
   * To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of
   * jakarta.jms.Message objects when Camel is sending a JMS message.
   */
  public fun messageCreatedStrategy(messageCreatedStrategy: String) {
    it.property("messageCreatedStrategy", messageCreatedStrategy)
  }

  /**
   * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in
   * milliseconds. The default is 5000 ms, that is, 5 seconds.
   */
  public fun recoveryInterval(recoveryInterval: String) {
    it.property("recoveryInterval", recoveryInterval)
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
}
