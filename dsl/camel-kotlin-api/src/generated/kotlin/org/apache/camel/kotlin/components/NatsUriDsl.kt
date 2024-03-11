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
 * Send and receive messages from NATS messaging system.
 */
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

  /**
   * The name of topic we want to use
   */
  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  /**
   * Timeout for connection attempts. (in milliseconds)
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Timeout for connection attempts. (in milliseconds)
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * Define if we want to flush connection when stopping or not
   */
  public fun flushConnection(flushConnection: String) {
    it.property("flushConnection", flushConnection)
  }

  /**
   * Define if we want to flush connection when stopping or not
   */
  public fun flushConnection(flushConnection: Boolean) {
    it.property("flushConnection", flushConnection.toString())
  }

  /**
   * Set the flush timeout (in milliseconds)
   */
  public fun flushTimeout(flushTimeout: String) {
    it.property("flushTimeout", flushTimeout)
  }

  /**
   * Set the flush timeout (in milliseconds)
   */
  public fun flushTimeout(flushTimeout: Int) {
    it.property("flushTimeout", flushTimeout.toString())
  }

  /**
   * maximum number of pings have not received a response allowed by the client
   */
  public fun maxPingsOut(maxPingsOut: String) {
    it.property("maxPingsOut", maxPingsOut)
  }

  /**
   * maximum number of pings have not received a response allowed by the client
   */
  public fun maxPingsOut(maxPingsOut: Int) {
    it.property("maxPingsOut", maxPingsOut.toString())
  }

  /**
   * Max reconnection attempts
   */
  public fun maxReconnectAttempts(maxReconnectAttempts: String) {
    it.property("maxReconnectAttempts", maxReconnectAttempts)
  }

  /**
   * Max reconnection attempts
   */
  public fun maxReconnectAttempts(maxReconnectAttempts: Int) {
    it.property("maxReconnectAttempts", maxReconnectAttempts.toString())
  }

  /**
   * Turn off echo. If supported by the gnatsd version you are connecting to this flag will prevent
   * the server from echoing messages back to the connection if it has subscriptions on the subject
   * being published to.
   */
  public fun noEcho(noEcho: String) {
    it.property("noEcho", noEcho)
  }

  /**
   * Turn off echo. If supported by the gnatsd version you are connecting to this flag will prevent
   * the server from echoing messages back to the connection if it has subscriptions on the subject
   * being published to.
   */
  public fun noEcho(noEcho: Boolean) {
    it.property("noEcho", noEcho.toString())
  }

  /**
   * Whether or not randomizing the order of servers for the connection attempts
   */
  public fun noRandomizeServers(noRandomizeServers: String) {
    it.property("noRandomizeServers", noRandomizeServers)
  }

  /**
   * Whether or not randomizing the order of servers for the connection attempts
   */
  public fun noRandomizeServers(noRandomizeServers: Boolean) {
    it.property("noRandomizeServers", noRandomizeServers.toString())
  }

  /**
   * Whether or not running in pedantic mode (this affects performance)
   */
  public fun pedantic(pedantic: String) {
    it.property("pedantic", pedantic)
  }

  /**
   * Whether or not running in pedantic mode (this affects performance)
   */
  public fun pedantic(pedantic: Boolean) {
    it.property("pedantic", pedantic.toString())
  }

  /**
   * Ping interval to be aware if connection is still alive (in milliseconds)
   */
  public fun pingInterval(pingInterval: String) {
    it.property("pingInterval", pingInterval)
  }

  /**
   * Ping interval to be aware if connection is still alive (in milliseconds)
   */
  public fun pingInterval(pingInterval: Int) {
    it.property("pingInterval", pingInterval.toString())
  }

  /**
   * Whether or not using reconnection feature
   */
  public fun reconnect(reconnect: String) {
    it.property("reconnect", reconnect)
  }

  /**
   * Whether or not using reconnection feature
   */
  public fun reconnect(reconnect: Boolean) {
    it.property("reconnect", reconnect.toString())
  }

  /**
   * Waiting time before attempts reconnection (in milliseconds)
   */
  public fun reconnectTimeWait(reconnectTimeWait: String) {
    it.property("reconnectTimeWait", reconnectTimeWait)
  }

  /**
   * Waiting time before attempts reconnection (in milliseconds)
   */
  public fun reconnectTimeWait(reconnectTimeWait: Int) {
    it.property("reconnectTimeWait", reconnectTimeWait.toString())
  }

  /**
   * Interval to clean up cancelled/timed out requests.
   */
  public fun requestCleanupInterval(requestCleanupInterval: String) {
    it.property("requestCleanupInterval", requestCleanupInterval)
  }

  /**
   * Interval to clean up cancelled/timed out requests.
   */
  public fun requestCleanupInterval(requestCleanupInterval: Int) {
    it.property("requestCleanupInterval", requestCleanupInterval.toString())
  }

  /**
   * URLs to one or more NAT servers. Use comma to separate URLs when specifying multiple servers.
   */
  public fun servers(servers: String) {
    it.property("servers", servers)
  }

  /**
   * Whether or not running in verbose mode
   */
  public fun verbose(verbose: String) {
    it.property("verbose", verbose)
  }

  /**
   * Whether or not running in verbose mode
   */
  public fun verbose(verbose: Boolean) {
    it.property("verbose", verbose.toString())
  }

  /**
   * Stop receiving messages from a topic we are subscribing to after maxMessages
   */
  public fun maxMessages(maxMessages: String) {
    it.property("maxMessages", maxMessages)
  }

  /**
   * Consumer thread pool size (default is 10)
   */
  public fun poolSize(poolSize: String) {
    it.property("poolSize", poolSize)
  }

  /**
   * Consumer thread pool size (default is 10)
   */
  public fun poolSize(poolSize: Int) {
    it.property("poolSize", poolSize.toString())
  }

  /**
   * The Queue name if we are using nats for a queue configuration
   */
  public fun queueName(queueName: String) {
    it.property("queueName", queueName)
  }

  /**
   * Can be used to turn off sending back reply message in the consumer.
   */
  public fun replyToDisabled(replyToDisabled: String) {
    it.property("replyToDisabled", replyToDisabled)
  }

  /**
   * Can be used to turn off sending back reply message in the consumer.
   */
  public fun replyToDisabled(replyToDisabled: Boolean) {
    it.property("replyToDisabled", replyToDisabled.toString())
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
   * the subject to which subscribers should send response
   */
  public fun replySubject(replySubject: String) {
    it.property("replySubject", replySubject)
  }

  /**
   * Request timeout in milliseconds
   */
  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  /**
   * Request timeout in milliseconds
   */
  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
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
   * Reference an already instantiated connection to Nats server
   */
  public fun connection(connection: String) {
    it.property("connection", connection)
  }

  /**
   * Define the header filtering strategy
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Whether or not connection trace messages should be printed to standard out for fine grained
   * debugging of connection issues.
   */
  public fun traceConnection(traceConnection: String) {
    it.property("traceConnection", traceConnection)
  }

  /**
   * Whether or not connection trace messages should be printed to standard out for fine grained
   * debugging of connection issues.
   */
  public fun traceConnection(traceConnection: Boolean) {
    it.property("traceConnection", traceConnection.toString())
  }

  /**
   * Set secure option indicating TLS is required
   */
  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  /**
   * Set secure option indicating TLS is required
   */
  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  /**
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
