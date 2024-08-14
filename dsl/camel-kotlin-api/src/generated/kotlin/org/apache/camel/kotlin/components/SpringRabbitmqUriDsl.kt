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
 * Send and receive messages from RabbitMQ using the Spring RabbitMQ client.
 */
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

  /**
   * The exchange name determines the exchange to which the produced messages will be sent to. In
   * the case of consumers, the exchange name determines the exchange the queue will be bound to. Note:
   * to use default exchange then do not use empty name, but use default instead.
   */
  public fun exchangeName(exchangeName: String) {
    this.exchangeName = exchangeName
    it.url("$exchangeName")
  }

  /**
   * The connection factory to be use. A connection factory must be configured either on the
   * component or endpoint.
   */
  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  /**
   * The name of the dead letter exchange
   */
  public fun deadLetterExchange(deadLetterExchange: String) {
    it.property("deadLetterExchange", deadLetterExchange)
  }

  /**
   * The type of the dead letter exchange
   */
  public fun deadLetterExchangeType(deadLetterExchangeType: String) {
    it.property("deadLetterExchangeType", deadLetterExchangeType)
  }

  /**
   * The name of the dead letter queue
   */
  public fun deadLetterQueue(deadLetterQueue: String) {
    it.property("deadLetterQueue", deadLetterQueue)
  }

  /**
   * The routing key for the dead letter exchange
   */
  public fun deadLetterRoutingKey(deadLetterRoutingKey: String) {
    it.property("deadLetterRoutingKey", deadLetterRoutingKey)
  }

  /**
   * Specifies whether Camel ignores the ReplyTo header in messages. If true, Camel does not send a
   * reply back to the destination specified in the ReplyTo header. You can use this option if you want
   * Camel to consume from a route and you do not want Camel to automatically send back a reply message
   * because another component in your code handles the reply message. You can also use this option if
   * you want to use Camel as a proxy between different message brokers and you want to route message
   * from one system to another.
   */
  public fun disableReplyTo(disableReplyTo: String) {
    it.property("disableReplyTo", disableReplyTo)
  }

  /**
   * Specifies whether Camel ignores the ReplyTo header in messages. If true, Camel does not send a
   * reply back to the destination specified in the ReplyTo header. You can use this option if you want
   * Camel to consume from a route and you do not want Camel to automatically send back a reply message
   * because another component in your code handles the reply message. You can also use this option if
   * you want to use Camel as a proxy between different message brokers and you want to route message
   * from one system to another.
   */
  public fun disableReplyTo(disableReplyTo: Boolean) {
    it.property("disableReplyTo", disableReplyTo.toString())
  }

  /**
   * The queue(s) to use for consuming or producing messages. Multiple queue names can be separated
   * by comma. If none has been configured then Camel will generate an unique id as the queue name.
   */
  public fun queues(queues: String) {
    it.property("queues", queues)
  }

  /**
   * The value of a routing key to use. Default is empty which is not helpful when using the default
   * (or any direct) exchange, but fine if the exchange is a headers exchange for instance.
   */
  public fun routingKey(routingKey: String) {
    it.property("routingKey", routingKey)
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
   * Flag controlling the behaviour of the container with respect to message acknowledgement. The
   * most common usage is to let the container handle the acknowledgements (so the listener doesn't
   * need to know about the channel or the message). Set to AcknowledgeMode.MANUAL if the listener will
   * send the acknowledgements itself using Channel.basicAck(long, boolean). Manual acks are consistent
   * with either a transactional or non-transactional channel, but if you are doing no other work on
   * the channel at the same other than receiving a single message then the transaction is probably
   * unnecessary. Set to AcknowledgeMode.NONE to tell the broker not to expect any acknowledgements,
   * and it will assume all messages are acknowledged as soon as they are sent (this is autoack in
   * native Rabbit broker terms). If AcknowledgeMode.NONE then the channel cannot be transactional (so
   * the container will fail on start up if that flag is accidentally set).
   */
  public fun acknowledgeMode(acknowledgeMode: String) {
    it.property("acknowledgeMode", acknowledgeMode)
  }

  /**
   * Whether the consumer processes the Exchange asynchronously. If enabled then the consumer may
   * pickup the next message from the queue, while the previous message is being processed
   * asynchronously (by the Asynchronous Routing Engine). This means that messages may be processed not
   * 100% strictly in order. If disabled (as default) then the Exchange is fully processed before the
   * consumer will pickup the next message from the queue.
   */
  public fun asyncConsumer(asyncConsumer: String) {
    it.property("asyncConsumer", asyncConsumer)
  }

  /**
   * Whether the consumer processes the Exchange asynchronously. If enabled then the consumer may
   * pickup the next message from the queue, while the previous message is being processed
   * asynchronously (by the Asynchronous Routing Engine). This means that messages may be processed not
   * 100% strictly in order. If disabled (as default) then the Exchange is fully processed before the
   * consumer will pickup the next message from the queue.
   */
  public fun asyncConsumer(asyncConsumer: Boolean) {
    it.property("asyncConsumer", asyncConsumer.toString())
  }

  /**
   * Specifies whether the consumer should auto declare binding between exchange, queue and routing
   * key when starting.
   */
  public fun autoDeclare(autoDeclare: String) {
    it.property("autoDeclare", autoDeclare)
  }

  /**
   * Specifies whether the consumer should auto declare binding between exchange, queue and routing
   * key when starting.
   */
  public fun autoDeclare(autoDeclare: Boolean) {
    it.property("autoDeclare", autoDeclare.toString())
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
   * The type of the exchange
   */
  public fun exchangeType(exchangeType: String) {
    it.property("exchangeType", exchangeType)
  }

  /**
   * Set to true for an exclusive consumer
   */
  public fun exclusive(exclusive: String) {
    it.property("exclusive", exclusive)
  }

  /**
   * Set to true for an exclusive consumer
   */
  public fun exclusive(exclusive: Boolean) {
    it.property("exclusive", exclusive.toString())
  }

  /**
   * How many times a Rabbitmq consumer will try the same message if Camel failed to process the
   * message (The number of attempts includes the initial try)
   */
  public fun maximumRetryAttempts(maximumRetryAttempts: String) {
    it.property("maximumRetryAttempts", maximumRetryAttempts)
  }

  /**
   * How many times a Rabbitmq consumer will try the same message if Camel failed to process the
   * message (The number of attempts includes the initial try)
   */
  public fun maximumRetryAttempts(maximumRetryAttempts: Int) {
    it.property("maximumRetryAttempts", maximumRetryAttempts.toString())
  }

  /**
   * Set to true for an no-local consumer
   */
  public fun noLocal(noLocal: String) {
    it.property("noLocal", noLocal)
  }

  /**
   * Set to true for an no-local consumer
   */
  public fun noLocal(noLocal: Boolean) {
    it.property("noLocal", noLocal.toString())
  }

  /**
   * Whether a Rabbitmq consumer should reject the message without requeuing. This enables failed
   * messages to be sent to a Dead Letter Exchange/Queue, if the broker is so configured.
   */
  public fun rejectAndDontRequeue(rejectAndDontRequeue: String) {
    it.property("rejectAndDontRequeue", rejectAndDontRequeue)
  }

  /**
   * Whether a Rabbitmq consumer should reject the message without requeuing. This enables failed
   * messages to be sent to a Dead Letter Exchange/Queue, if the broker is so configured.
   */
  public fun rejectAndDontRequeue(rejectAndDontRequeue: Boolean) {
    it.property("rejectAndDontRequeue", rejectAndDontRequeue.toString())
  }

  /**
   * Delay in millis a Rabbitmq consumer will wait before redelivering a message that Camel failed
   * to process
   */
  public fun retryDelay(retryDelay: String) {
    it.property("retryDelay", retryDelay)
  }

  /**
   * Delay in millis a Rabbitmq consumer will wait before redelivering a message that Camel failed
   * to process
   */
  public fun retryDelay(retryDelay: Int) {
    it.property("retryDelay", retryDelay.toString())
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
   * The number of consumers
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * The number of consumers
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
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
   * The maximum number of consumers (available only with SMLC)
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  /**
   * The maximum number of consumers (available only with SMLC)
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  /**
   * The type of the MessageListenerContainer
   */
  public fun messageListenerContainerType(messageListenerContainerType: String) {
    it.property("messageListenerContainerType", messageListenerContainerType)
  }

  /**
   * Tell the broker how many messages to send in a single request. Often this can be set quite high
   * to improve throughput.
   */
  public fun prefetchCount(prefetchCount: String) {
    it.property("prefetchCount", prefetchCount)
  }

  /**
   * Tell the broker how many messages to send in a single request. Often this can be set quite high
   * to improve throughput.
   */
  public fun prefetchCount(prefetchCount: Int) {
    it.property("prefetchCount", prefetchCount.toString())
  }

  /**
   * Custom retry configuration to use. If this is configured then the other settings such as
   * maximumRetryAttempts for retry are not in use.
   */
  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  /**
   * Whether to allow sending messages with no body. If this option is false and the message body is
   * null, then an MessageConversionException is thrown.
   */
  public fun allowNullBody(allowNullBody: String) {
    it.property("allowNullBody", allowNullBody)
  }

  /**
   * Whether to allow sending messages with no body. If this option is false and the message body is
   * null, then an MessageConversionException is thrown.
   */
  public fun allowNullBody(allowNullBody: Boolean) {
    it.property("allowNullBody", allowNullBody.toString())
  }

  /**
   * Specifies whether the producer should auto declare binding between exchange, queue and routing
   * key when starting.
   */
  public fun autoDeclareProducer(autoDeclareProducer: String) {
    it.property("autoDeclareProducer", autoDeclareProducer)
  }

  /**
   * Specifies whether the producer should auto declare binding between exchange, queue and routing
   * key when starting.
   */
  public fun autoDeclareProducer(autoDeclareProducer: Boolean) {
    it.property("autoDeclareProducer", autoDeclareProducer.toString())
  }

  /**
   * Controls whether to wait for confirms. The connection factory must be configured for publisher
   * confirms and this method. auto = Camel detects if the connection factory uses confirms or not.
   * disabled = Confirms is disabled. enabled = Confirms is enabled.
   */
  public fun confirm(confirm: String) {
    it.property("confirm", confirm)
  }

  /**
   * Specify the timeout in milliseconds to be used when waiting for a message sent to be confirmed
   * by RabbitMQ when doing send only messaging (InOnly). The default value is 5 seconds. A negative
   * value indicates an indefinite timeout.
   */
  public fun confirmTimeout(confirmTimeout: String) {
    it.property("confirmTimeout", confirmTimeout)
  }

  /**
   * Specify the timeout in milliseconds to be used when waiting for a reply message when doing
   * request/reply (InOut) messaging. The default value is 30 seconds. A negative value indicates an
   * indefinite timeout (Beware that this will cause a memory leak if a reply is not received).
   */
  public fun replyTimeout(replyTimeout: String) {
    it.property("replyTimeout", replyTimeout)
  }

  /**
   * If true the queue will not be bound to the exchange after declaring it.
   */
  public fun skipBindQueue(skipBindQueue: String) {
    it.property("skipBindQueue", skipBindQueue)
  }

  /**
   * If true the queue will not be bound to the exchange after declaring it.
   */
  public fun skipBindQueue(skipBindQueue: Boolean) {
    it.property("skipBindQueue", skipBindQueue.toString())
  }

  /**
   * This can be used if we need to declare the queue but not the exchange.
   */
  public fun skipDeclareExchange(skipDeclareExchange: String) {
    it.property("skipDeclareExchange", skipDeclareExchange)
  }

  /**
   * This can be used if we need to declare the queue but not the exchange.
   */
  public fun skipDeclareExchange(skipDeclareExchange: Boolean) {
    it.property("skipDeclareExchange", skipDeclareExchange.toString())
  }

  /**
   * If true the producer will not declare and bind a queue. This can be used for directing messages
   * via an existing routing key.
   */
  public fun skipDeclareQueue(skipDeclareQueue: String) {
    it.property("skipDeclareQueue", skipDeclareQueue)
  }

  /**
   * If true the producer will not declare and bind a queue. This can be used for directing messages
   * via an existing routing key.
   */
  public fun skipDeclareQueue(skipDeclareQueue: Boolean) {
    it.property("skipDeclareQueue", skipDeclareQueue.toString())
  }

  /**
   * Use a separate connection for publishers and consumers
   */
  public fun usePublisherConnection(usePublisherConnection: String) {
    it.property("usePublisherConnection", usePublisherConnection)
  }

  /**
   * Use a separate connection for publishers and consumers
   */
  public fun usePublisherConnection(usePublisherConnection: Boolean) {
    it.property("usePublisherConnection", usePublisherConnection.toString())
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
   * Specify arguments for configuring the different RabbitMQ concepts, a different prefix is
   * required for each element: arg.consumer. arg.exchange. arg.queue. arg.binding. arg.dlq.exchange.
   * arg.dlq.queue. arg.dlq.binding. For example to declare a queue with message ttl argument:
   * args=arg.queue.x-message-ttl=60000
   */
  public fun args(args: String) {
    it.property("args", args)
  }

  /**
   * To use a custom MessageConverter so you can be in control how to map to/from a
   * org.springframework.amqp.core.Message.
   */
  public fun messageConverter(messageConverter: String) {
    it.property("messageConverter", messageConverter)
  }

  /**
   * To use a custom MessagePropertiesConverter so you can be in control how to map to/from a
   * org.springframework.amqp.core.MessageProperties.
   */
  public fun messagePropertiesConverter(messagePropertiesConverter: String) {
    it.property("messagePropertiesConverter", messagePropertiesConverter)
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
}
