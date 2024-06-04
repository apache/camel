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
 * Socket level networking using TCP or UDP with Netty 4.x.
 */
public fun UriDsl.netty(i: NettyUriDsl.() -> Unit) {
  NettyUriDsl(this).apply(i)
}

@CamelDslMarker
public class NettyUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("netty")
  }

  private var protocol: String = ""

  private var host: String = ""

  private var port: String = ""

  /**
   * The protocol to use which can be tcp or udp.
   */
  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol://$host:$port")
  }

  /**
   * The hostname. For the consumer the hostname is localhost or 0.0.0.0. For the producer the
   * hostname is the remote host to connect to
   */
  public fun host(host: String) {
    this.host = host
    it.url("$protocol://$host:$port")
  }

  /**
   * The host port number
   */
  public fun port(port: String) {
    this.port = port
    it.url("$protocol://$host:$port")
  }

  /**
   * The host port number
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol://$host:$port")
  }

  /**
   * Whether or not to disconnect(close) from Netty Channel right after use. Can be used for both
   * consumer and producer.
   */
  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  /**
   * Whether or not to disconnect(close) from Netty Channel right after use. Can be used for both
   * consumer and producer.
   */
  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  /**
   * Setting to ensure socket is not closed due to inactivity
   */
  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  /**
   * Setting to ensure socket is not closed due to inactivity
   */
  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  /**
   * Setting to facilitate socket multiplexing
   */
  public fun reuseAddress(reuseAddress: String) {
    it.property("reuseAddress", reuseAddress)
  }

  /**
   * Setting to facilitate socket multiplexing
   */
  public fun reuseAddress(reuseAddress: Boolean) {
    it.property("reuseAddress", reuseAddress.toString())
  }

  /**
   * This option allows producers and consumers (in client mode) to reuse the same Netty Channel for
   * the lifecycle of processing the Exchange. This is useful if you need to call a server multiple
   * times in a Camel route and want to use the same network connection. When using this, the channel
   * is not returned to the connection pool until the Exchange is done; or disconnected if the
   * disconnect option is set to true. The reused Channel is stored on the Exchange as an exchange
   * property with the key NettyConstants#NETTY_CHANNEL which allows you to obtain the channel during
   * routing and use it as well.
   */
  public fun reuseChannel(reuseChannel: String) {
    it.property("reuseChannel", reuseChannel)
  }

  /**
   * This option allows producers and consumers (in client mode) to reuse the same Netty Channel for
   * the lifecycle of processing the Exchange. This is useful if you need to call a server multiple
   * times in a Camel route and want to use the same network connection. When using this, the channel
   * is not returned to the connection pool until the Exchange is done; or disconnected if the
   * disconnect option is set to true. The reused Channel is stored on the Exchange as an exchange
   * property with the key NettyConstants#NETTY_CHANNEL which allows you to obtain the channel during
   * routing and use it as well.
   */
  public fun reuseChannel(reuseChannel: Boolean) {
    it.property("reuseChannel", reuseChannel.toString())
  }

  /**
   * Setting to set endpoint as one-way or request-response
   */
  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  /**
   * Setting to set endpoint as one-way or request-response
   */
  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
  }

  /**
   * Setting to improve TCP protocol performance
   */
  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  /**
   * Setting to improve TCP protocol performance
   */
  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
  }

  /**
   * Setting to choose Multicast over UDP
   */
  public fun broadcast(broadcast: String) {
    it.property("broadcast", broadcast)
  }

  /**
   * Setting to choose Multicast over UDP
   */
  public fun broadcast(broadcast: Boolean) {
    it.property("broadcast", broadcast.toString())
  }

  /**
   * If the clientMode is true, netty consumer will connect the address as a TCP client.
   */
  public fun clientMode(clientMode: String) {
    it.property("clientMode", clientMode)
  }

  /**
   * If the clientMode is true, netty consumer will connect the address as a TCP client.
   */
  public fun clientMode(clientMode: Boolean) {
    it.property("clientMode", clientMode.toString())
  }

  /**
   * Used only in clientMode in consumer, the consumer will attempt to reconnect on disconnection if
   * this is enabled
   */
  public fun reconnect(reconnect: String) {
    it.property("reconnect", reconnect)
  }

  /**
   * Used only in clientMode in consumer, the consumer will attempt to reconnect on disconnection if
   * this is enabled
   */
  public fun reconnect(reconnect: Boolean) {
    it.property("reconnect", reconnect.toString())
  }

  /**
   * Used if reconnect and clientMode is enabled. The interval in milli seconds to attempt
   * reconnection
   */
  public fun reconnectInterval(reconnectInterval: String) {
    it.property("reconnectInterval", reconnectInterval)
  }

  /**
   * Used if reconnect and clientMode is enabled. The interval in milli seconds to attempt
   * reconnection
   */
  public fun reconnectInterval(reconnectInterval: Int) {
    it.property("reconnectInterval", reconnectInterval.toString())
  }

  /**
   * Allows to configure a backlog for netty consumer (server). Note the backlog is just a best
   * effort depending on the OS. Setting this option to a value such as 200, 500 or 1000, tells the TCP
   * stack how long the accept queue can be If this option is not configured, then the backlog depends
   * on OS setting.
   */
  public fun backlog(backlog: String) {
    it.property("backlog", backlog)
  }

  /**
   * Allows to configure a backlog for netty consumer (server). Note the backlog is just a best
   * effort depending on the OS. Setting this option to a value such as 200, 500 or 1000, tells the TCP
   * stack how long the accept queue can be If this option is not configured, then the backlog depends
   * on OS setting.
   */
  public fun backlog(backlog: Int) {
    it.property("backlog", backlog.toString())
  }

  /**
   * When netty works on nio mode, it uses default bossCount parameter from Netty, which is 1. User
   * can use this option to override the default bossCount from Netty
   */
  public fun bossCount(bossCount: String) {
    it.property("bossCount", bossCount)
  }

  /**
   * When netty works on nio mode, it uses default bossCount parameter from Netty, which is 1. User
   * can use this option to override the default bossCount from Netty
   */
  public fun bossCount(bossCount: Int) {
    it.property("bossCount", bossCount.toString())
  }

  /**
   * Set the BossGroup which could be used for handling the new connection of the server side across
   * the NettyEndpoint
   */
  public fun bossGroup(bossGroup: String) {
    it.property("bossGroup", bossGroup)
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
   * If sync is enabled then this option dictates NettyConsumer if it should disconnect where there
   * is no reply to send back.
   */
  public fun disconnectOnNoReply(disconnectOnNoReply: String) {
    it.property("disconnectOnNoReply", disconnectOnNoReply)
  }

  /**
   * If sync is enabled then this option dictates NettyConsumer if it should disconnect where there
   * is no reply to send back.
   */
  public fun disconnectOnNoReply(disconnectOnNoReply: Boolean) {
    it.property("disconnectOnNoReply", disconnectOnNoReply.toString())
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
   * To use a custom NettyServerBootstrapFactory
   */
  public fun nettyServerBootstrapFactory(nettyServerBootstrapFactory: String) {
    it.property("nettyServerBootstrapFactory", nettyServerBootstrapFactory)
  }

  /**
   * When using UDP then this option can be used to specify a network interface by its name, such as
   * eth0 to join a multicast group.
   */
  public fun networkInterface(networkInterface: String) {
    it.property("networkInterface", networkInterface)
  }

  /**
   * If sync is enabled this option dictates NettyConsumer which logging level to use when logging a
   * there is no reply to send back.
   */
  public fun noReplyLogLevel(noReplyLogLevel: String) {
    it.property("noReplyLogLevel", noReplyLogLevel)
  }

  /**
   * If the server (NettyConsumer) catches an java.nio.channels.ClosedChannelException then its
   * logged using this logging level. This is used to avoid logging the closed channel exceptions, as
   * clients can disconnect abruptly and then cause a flood of closed exceptions in the Netty server.
   */
  public
      fun serverClosedChannelExceptionCaughtLogLevel(serverClosedChannelExceptionCaughtLogLevel: String) {
    it.property("serverClosedChannelExceptionCaughtLogLevel",
        serverClosedChannelExceptionCaughtLogLevel)
  }

  /**
   * If the server (NettyConsumer) catches an exception then its logged using this logging level.
   */
  public fun serverExceptionCaughtLogLevel(serverExceptionCaughtLogLevel: String) {
    it.property("serverExceptionCaughtLogLevel", serverExceptionCaughtLogLevel)
  }

  /**
   * To use a custom ServerInitializerFactory
   */
  public fun serverInitializerFactory(serverInitializerFactory: String) {
    it.property("serverInitializerFactory", serverInitializerFactory)
  }

  /**
   * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
   */
  public fun usingExecutorService(usingExecutorService: String) {
    it.property("usingExecutorService", usingExecutorService)
  }

  /**
   * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
   */
  public fun usingExecutorService(usingExecutorService: Boolean) {
    it.property("usingExecutorService", usingExecutorService.toString())
  }

  /**
   * Time to wait for a socket connection to be available. Value is in milliseconds.
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * Time to wait for a socket connection to be available. Value is in milliseconds.
   */
  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  /**
   * Allows to use a timeout for the Netty producer when calling a remote server. By default no
   * timeout is in use. The value is in milli seconds, so eg 30000 is 30 seconds. The requestTimeout is
   * using Netty's ReadTimeoutHandler to trigger the timeout.
   */
  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  /**
   * Allows to use a timeout for the Netty producer when calling a remote server. By default no
   * timeout is in use. The value is in milli seconds, so eg 30000 is 30 seconds. The requestTimeout is
   * using Netty's ReadTimeoutHandler to trigger the timeout.
   */
  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
  }

  /**
   * To use a custom ClientInitializerFactory
   */
  public fun clientInitializerFactory(clientInitializerFactory: String) {
    it.property("clientInitializerFactory", clientInitializerFactory)
  }

  /**
   * To use a custom correlation manager to manage how request and reply messages are mapped when
   * using request/reply with the netty producer. This should only be used if you have a way to map
   * requests together with replies such as if there is correlation ids in both the request and reply
   * messages. This can be used if you want to multiplex concurrent messages on the same channel (aka
   * connection) in netty. When doing this you must have a way to correlate the request and reply
   * messages so you can store the right reply on the inflight Camel Exchange before its continued
   * routed. We recommend extending the TimeoutCorrelationManagerSupport when you build custom
   * correlation managers. This provides support for timeout and other complexities you otherwise would
   * need to implement as well. See also the producerPoolEnabled option for more details.
   */
  public fun correlationManager(correlationManager: String) {
    it.property("correlationManager", correlationManager)
  }

  /**
   * Channels can be lazily created to avoid exceptions, if the remote server is not up and running
   * when the Camel producer is started.
   */
  public fun lazyChannelCreation(lazyChannelCreation: String) {
    it.property("lazyChannelCreation", lazyChannelCreation)
  }

  /**
   * Channels can be lazily created to avoid exceptions, if the remote server is not up and running
   * when the Camel producer is started.
   */
  public fun lazyChannelCreation(lazyChannelCreation: Boolean) {
    it.property("lazyChannelCreation", lazyChannelCreation.toString())
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
   * Sets the value for the blockWhenExhausted configuration attribute. It determines whether to
   * block when the borrowObject() method is invoked when the pool is exhausted (the maximum number of
   * active objects has been reached).
   */
  public fun producerPoolBlockWhenExhausted(producerPoolBlockWhenExhausted: String) {
    it.property("producerPoolBlockWhenExhausted", producerPoolBlockWhenExhausted)
  }

  /**
   * Sets the value for the blockWhenExhausted configuration attribute. It determines whether to
   * block when the borrowObject() method is invoked when the pool is exhausted (the maximum number of
   * active objects has been reached).
   */
  public fun producerPoolBlockWhenExhausted(producerPoolBlockWhenExhausted: Boolean) {
    it.property("producerPoolBlockWhenExhausted", producerPoolBlockWhenExhausted.toString())
  }

  /**
   * Whether producer pool is enabled or not. Important: If you turn this off then a single shared
   * connection is used for the producer, also if you are doing request/reply. That means there is a
   * potential issue with interleaved responses if replies comes back out-of-order. Therefore you need
   * to have a correlation id in both the request and reply messages so you can properly correlate the
   * replies to the Camel callback that is responsible for continue processing the message in Camel. To
   * do this you need to implement NettyCamelStateCorrelationManager as correlation manager and
   * configure it via the correlationManager option. See also the correlationManager option for more
   * details.
   */
  public fun producerPoolEnabled(producerPoolEnabled: String) {
    it.property("producerPoolEnabled", producerPoolEnabled)
  }

  /**
   * Whether producer pool is enabled or not. Important: If you turn this off then a single shared
   * connection is used for the producer, also if you are doing request/reply. That means there is a
   * potential issue with interleaved responses if replies comes back out-of-order. Therefore you need
   * to have a correlation id in both the request and reply messages so you can properly correlate the
   * replies to the Camel callback that is responsible for continue processing the message in Camel. To
   * do this you need to implement NettyCamelStateCorrelationManager as correlation manager and
   * configure it via the correlationManager option. See also the correlationManager option for more
   * details.
   */
  public fun producerPoolEnabled(producerPoolEnabled: Boolean) {
    it.property("producerPoolEnabled", producerPoolEnabled.toString())
  }

  /**
   * Sets the cap on the number of idle instances in the pool.
   */
  public fun producerPoolMaxIdle(producerPoolMaxIdle: String) {
    it.property("producerPoolMaxIdle", producerPoolMaxIdle)
  }

  /**
   * Sets the cap on the number of idle instances in the pool.
   */
  public fun producerPoolMaxIdle(producerPoolMaxIdle: Int) {
    it.property("producerPoolMaxIdle", producerPoolMaxIdle.toString())
  }

  /**
   * Sets the cap on the number of objects that can be allocated by the pool (checked out to
   * clients, or idle awaiting checkout) at a given time. Use a negative value for no limit.
   */
  public fun producerPoolMaxTotal(producerPoolMaxTotal: String) {
    it.property("producerPoolMaxTotal", producerPoolMaxTotal)
  }

  /**
   * Sets the cap on the number of objects that can be allocated by the pool (checked out to
   * clients, or idle awaiting checkout) at a given time. Use a negative value for no limit.
   */
  public fun producerPoolMaxTotal(producerPoolMaxTotal: Int) {
    it.property("producerPoolMaxTotal", producerPoolMaxTotal.toString())
  }

  /**
   * Sets the maximum duration (value in millis) the borrowObject() method should block before
   * throwing an exception when the pool is exhausted and producerPoolBlockWhenExhausted is true. When
   * less than 0, the borrowObject() method may block indefinitely.
   */
  public fun producerPoolMaxWait(producerPoolMaxWait: String) {
    it.property("producerPoolMaxWait", producerPoolMaxWait)
  }

  /**
   * Sets the maximum duration (value in millis) the borrowObject() method should block before
   * throwing an exception when the pool is exhausted and producerPoolBlockWhenExhausted is true. When
   * less than 0, the borrowObject() method may block indefinitely.
   */
  public fun producerPoolMaxWait(producerPoolMaxWait: Int) {
    it.property("producerPoolMaxWait", producerPoolMaxWait.toString())
  }

  /**
   * Sets the minimum amount of time (value in millis) an object may sit idle in the pool before it
   * is eligible for eviction by the idle object evictor.
   */
  public fun producerPoolMinEvictableIdle(producerPoolMinEvictableIdle: String) {
    it.property("producerPoolMinEvictableIdle", producerPoolMinEvictableIdle)
  }

  /**
   * Sets the minimum amount of time (value in millis) an object may sit idle in the pool before it
   * is eligible for eviction by the idle object evictor.
   */
  public fun producerPoolMinEvictableIdle(producerPoolMinEvictableIdle: Int) {
    it.property("producerPoolMinEvictableIdle", producerPoolMinEvictableIdle.toString())
  }

  /**
   * Sets the minimum number of instances allowed in the producer pool before the evictor thread (if
   * active) spawns new objects.
   */
  public fun producerPoolMinIdle(producerPoolMinIdle: String) {
    it.property("producerPoolMinIdle", producerPoolMinIdle)
  }

  /**
   * Sets the minimum number of instances allowed in the producer pool before the evictor thread (if
   * active) spawns new objects.
   */
  public fun producerPoolMinIdle(producerPoolMinIdle: Int) {
    it.property("producerPoolMinIdle", producerPoolMinIdle.toString())
  }

  /**
   * This option supports connection less udp sending which is a real fire and forget. A connected
   * udp send receive the PortUnreachableException if no one is listen on the receiving port.
   */
  public fun udpConnectionlessSending(udpConnectionlessSending: String) {
    it.property("udpConnectionlessSending", udpConnectionlessSending)
  }

  /**
   * This option supports connection less udp sending which is a real fire and forget. A connected
   * udp send receive the PortUnreachableException if no one is listen on the receiving port.
   */
  public fun udpConnectionlessSending(udpConnectionlessSending: Boolean) {
    it.property("udpConnectionlessSending", udpConnectionlessSending.toString())
  }

  /**
   * If the useByteBuf is true, netty producer will turn the message body into ByteBuf before
   * sending it out.
   */
  public fun useByteBuf(useByteBuf: String) {
    it.property("useByteBuf", useByteBuf)
  }

  /**
   * If the useByteBuf is true, netty producer will turn the message body into ByteBuf before
   * sending it out.
   */
  public fun useByteBuf(useByteBuf: Boolean) {
    it.property("useByteBuf", useByteBuf.toString())
  }

  /**
   * Only used for TCP when transferExchange is true. When set to true, serializable objects in
   * headers and properties will be added to the exchange. Otherwise Camel will exclude any
   * non-serializable objects and log it at WARN level.
   */
  public fun allowSerializedHeaders(allowSerializedHeaders: String) {
    it.property("allowSerializedHeaders", allowSerializedHeaders)
  }

  /**
   * Only used for TCP when transferExchange is true. When set to true, serializable objects in
   * headers and properties will be added to the exchange. Otherwise Camel will exclude any
   * non-serializable objects and log it at WARN level.
   */
  public fun allowSerializedHeaders(allowSerializedHeaders: Boolean) {
    it.property("allowSerializedHeaders", allowSerializedHeaders.toString())
  }

  /**
   * To use a explicit ChannelGroup.
   */
  public fun channelGroup(channelGroup: String) {
    it.property("channelGroup", channelGroup)
  }

  /**
   * Whether to use native transport instead of NIO. Native transport takes advantage of the host
   * operating system and is only supported on some platforms. You need to add the netty JAR for the
   * host operating system you are using. See more details at:
   * http://netty.io/wiki/native-transports.html
   */
  public fun nativeTransport(nativeTransport: String) {
    it.property("nativeTransport", nativeTransport)
  }

  /**
   * Whether to use native transport instead of NIO. Native transport takes advantage of the host
   * operating system and is only supported on some platforms. You need to add the netty JAR for the
   * host operating system you are using. See more details at:
   * http://netty.io/wiki/native-transports.html
   */
  public fun nativeTransport(nativeTransport: Boolean) {
    it.property("nativeTransport", nativeTransport.toString())
  }

  /**
   * Allows to configure additional netty options using option. as prefix. For example
   * option.child.keepAlive=false to set the netty option child.keepAlive=false. See the Netty
   * documentation for possible options that can be used.
   */
  public fun options(options: String) {
    it.property("options", options)
  }

  /**
   * The TCP/UDP buffer sizes to be used during inbound communication. Size is bytes.
   */
  public fun receiveBufferSize(receiveBufferSize: String) {
    it.property("receiveBufferSize", receiveBufferSize)
  }

  /**
   * The TCP/UDP buffer sizes to be used during inbound communication. Size is bytes.
   */
  public fun receiveBufferSize(receiveBufferSize: Int) {
    it.property("receiveBufferSize", receiveBufferSize.toString())
  }

  /**
   * Configures the buffer size predictor. See details at Jetty documentation and this mail thread.
   */
  public fun receiveBufferSizePredictor(receiveBufferSizePredictor: String) {
    it.property("receiveBufferSizePredictor", receiveBufferSizePredictor)
  }

  /**
   * Configures the buffer size predictor. See details at Jetty documentation and this mail thread.
   */
  public fun receiveBufferSizePredictor(receiveBufferSizePredictor: Int) {
    it.property("receiveBufferSizePredictor", receiveBufferSizePredictor.toString())
  }

  /**
   * The TCP/UDP buffer sizes to be used during outbound communication. Size is bytes.
   */
  public fun sendBufferSize(sendBufferSize: String) {
    it.property("sendBufferSize", sendBufferSize)
  }

  /**
   * The TCP/UDP buffer sizes to be used during outbound communication. Size is bytes.
   */
  public fun sendBufferSize(sendBufferSize: Int) {
    it.property("sendBufferSize", sendBufferSize.toString())
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
   * Only used for TCP. You can transfer the exchange over the wire instead of just the body. The
   * following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault
   * headers, exchange properties, exchange exception. This requires that the objects are serializable.
   * Camel will exclude any non-serializable objects and log it at WARN level.
   */
  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  /**
   * Only used for TCP. You can transfer the exchange over the wire instead of just the body. The
   * following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault
   * headers, exchange properties, exchange exception. This requires that the objects are serializable.
   * Camel will exclude any non-serializable objects and log it at WARN level.
   */
  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  /**
   * For UDP only. If enabled the using byte array codec instead of Java serialization protocol.
   */
  public fun udpByteArrayCodec(udpByteArrayCodec: String) {
    it.property("udpByteArrayCodec", udpByteArrayCodec)
  }

  /**
   * For UDP only. If enabled the using byte array codec instead of Java serialization protocol.
   */
  public fun udpByteArrayCodec(udpByteArrayCodec: Boolean) {
    it.property("udpByteArrayCodec", udpByteArrayCodec.toString())
  }

  /**
   * Path to unix domain socket to use instead of inet socket. Host and port parameters will not be
   * used, however required. It is ok to set dummy values for them. Must be used with
   * nativeTransport=true and clientMode=false.
   */
  public fun unixDomainSocketPath(unixDomainSocketPath: String) {
    it.property("unixDomainSocketPath", unixDomainSocketPath)
  }

  /**
   * When netty works on nio mode, it uses default workerCount parameter from Netty (which is
   * cpu_core_threads x 2). User can use this option to override the default workerCount from Netty.
   */
  public fun workerCount(workerCount: String) {
    it.property("workerCount", workerCount)
  }

  /**
   * When netty works on nio mode, it uses default workerCount parameter from Netty (which is
   * cpu_core_threads x 2). User can use this option to override the default workerCount from Netty.
   */
  public fun workerCount(workerCount: Int) {
    it.property("workerCount", workerCount.toString())
  }

  /**
   * To use a explicit EventLoopGroup as the boss thread pool. For example to share a thread pool
   * with multiple consumers or producers. By default each consumer or producer has their own worker
   * pool with 2 x cpu count core threads.
   */
  public fun workerGroup(workerGroup: String) {
    it.property("workerGroup", workerGroup)
  }

  /**
   * The netty component installs a default codec if both, encoder/decoder is null and textline is
   * false. Setting allowDefaultCodec to false prevents the netty component from installing a default
   * codec as the first element in the filter chain.
   */
  public fun allowDefaultCodec(allowDefaultCodec: String) {
    it.property("allowDefaultCodec", allowDefaultCodec)
  }

  /**
   * The netty component installs a default codec if both, encoder/decoder is null and textline is
   * false. Setting allowDefaultCodec to false prevents the netty component from installing a default
   * codec as the first element in the filter chain.
   */
  public fun allowDefaultCodec(allowDefaultCodec: Boolean) {
    it.property("allowDefaultCodec", allowDefaultCodec.toString())
  }

  /**
   * Whether or not to auto append missing end delimiter when sending using the textline codec.
   */
  public fun autoAppendDelimiter(autoAppendDelimiter: String) {
    it.property("autoAppendDelimiter", autoAppendDelimiter)
  }

  /**
   * Whether or not to auto append missing end delimiter when sending using the textline codec.
   */
  public fun autoAppendDelimiter(autoAppendDelimiter: Boolean) {
    it.property("autoAppendDelimiter", autoAppendDelimiter.toString())
  }

  /**
   * The max line length to use for the textline codec.
   */
  public fun decoderMaxLineLength(decoderMaxLineLength: String) {
    it.property("decoderMaxLineLength", decoderMaxLineLength)
  }

  /**
   * The max line length to use for the textline codec.
   */
  public fun decoderMaxLineLength(decoderMaxLineLength: Int) {
    it.property("decoderMaxLineLength", decoderMaxLineLength.toString())
  }

  /**
   * A list of decoders to be used. You can use a String which have values separated by comma, and
   * have the values be looked up in the Registry. Just remember to prefix the value with # so Camel
   * knows it should lookup.
   */
  public fun decoders(decoders: String) {
    it.property("decoders", decoders)
  }

  /**
   * The delimiter to use for the textline codec. Possible values are LINE and NULL.
   */
  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  /**
   * A list of encoders to be used. You can use a String which have values separated by comma, and
   * have the values be looked up in the Registry. Just remember to prefix the value with # so Camel
   * knows it should lookup.
   */
  public fun encoders(encoders: String) {
    it.property("encoders", encoders)
  }

  /**
   * The encoding (a charset name) to use for the textline codec. If not provided, Camel will use
   * the JVM default Charset.
   */
  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  /**
   * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line
   * based codec; if not specified or the value is false, then Object Serialization is assumed over
   * TCP - however only Strings are allowed to be serialized by default.
   */
  public fun textline(textline: String) {
    it.property("textline", textline)
  }

  /**
   * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line
   * based codec; if not specified or the value is false, then Object Serialization is assumed over
   * TCP - however only Strings are allowed to be serialized by default.
   */
  public fun textline(textline: Boolean) {
    it.property("textline", textline.toString())
  }

  /**
   * Which protocols to enable when using SSL
   */
  public fun enabledProtocols(enabledProtocols: String) {
    it.property("enabledProtocols", enabledProtocols)
  }

  /**
   * To enable/disable hostname verification on SSLEngine
   */
  public fun hostnameVerification(hostnameVerification: String) {
    it.property("hostnameVerification", hostnameVerification)
  }

  /**
   * To enable/disable hostname verification on SSLEngine
   */
  public fun hostnameVerification(hostnameVerification: Boolean) {
    it.property("hostnameVerification", hostnameVerification.toString())
  }

  /**
   * Client side certificate keystore to be used for encryption
   */
  public fun keyStoreFile(keyStoreFile: String) {
    it.property("keyStoreFile", keyStoreFile)
  }

  /**
   * Keystore format to be used for payload encryption. Defaults to JKS if not set
   */
  public fun keyStoreFormat(keyStoreFormat: String) {
    it.property("keyStoreFormat", keyStoreFormat)
  }

  /**
   * Client side certificate keystore to be used for encryption. Is loaded by default from
   * classpath, but you can prefix with classpath:, file:, or http: to load the resource from different
   * systems.
   */
  public fun keyStoreResource(keyStoreResource: String) {
    it.property("keyStoreResource", keyStoreResource)
  }

  /**
   * Configures whether the server needs client authentication when using SSL.
   */
  public fun needClientAuth(needClientAuth: String) {
    it.property("needClientAuth", needClientAuth)
  }

  /**
   * Configures whether the server needs client authentication when using SSL.
   */
  public fun needClientAuth(needClientAuth: Boolean) {
    it.property("needClientAuth", needClientAuth.toString())
  }

  /**
   * Password setting to use in order to encrypt/decrypt payloads sent using SSH
   */
  public fun passphrase(passphrase: String) {
    it.property("passphrase", passphrase)
  }

  /**
   * Security provider to be used for payload encryption. Defaults to SunX509 if not set.
   */
  public fun securityProvider(securityProvider: String) {
    it.property("securityProvider", securityProvider)
  }

  /**
   * Setting to specify whether SSL encryption is applied to this endpoint
   */
  public fun ssl(ssl: String) {
    it.property("ssl", ssl)
  }

  /**
   * Setting to specify whether SSL encryption is applied to this endpoint
   */
  public fun ssl(ssl: Boolean) {
    it.property("ssl", ssl.toString())
  }

  /**
   * When enabled and in SSL mode, then the Netty consumer will enrich the Camel Message with
   * headers having information about the client certificate such as subject name, issuer name, serial
   * number, and the valid date range.
   */
  public fun sslClientCertHeaders(sslClientCertHeaders: String) {
    it.property("sslClientCertHeaders", sslClientCertHeaders)
  }

  /**
   * When enabled and in SSL mode, then the Netty consumer will enrich the Camel Message with
   * headers having information about the client certificate such as subject name, issuer name, serial
   * number, and the valid date range.
   */
  public fun sslClientCertHeaders(sslClientCertHeaders: Boolean) {
    it.property("sslClientCertHeaders", sslClientCertHeaders.toString())
  }

  /**
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * Reference to a class that could be used to return an SSL Handler
   */
  public fun sslHandler(sslHandler: String) {
    it.property("sslHandler", sslHandler)
  }

  /**
   * Server side certificate keystore to be used for encryption
   */
  public fun trustStoreFile(trustStoreFile: String) {
    it.property("trustStoreFile", trustStoreFile)
  }

  /**
   * Server side certificate keystore to be used for encryption. Is loaded by default from
   * classpath, but you can prefix with classpath:, file:, or http: to load the resource from different
   * systems.
   */
  public fun trustStoreResource(trustStoreResource: String) {
    it.property("trustStoreResource", trustStoreResource)
  }
}
