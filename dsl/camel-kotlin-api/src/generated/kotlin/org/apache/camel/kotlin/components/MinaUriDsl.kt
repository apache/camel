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
 * Socket level networking using TCP or UDP with Apache Mina 2.x.
 */
public fun UriDsl.mina(i: MinaUriDsl.() -> Unit) {
  MinaUriDsl(this).apply(i)
}

@CamelDslMarker
public class MinaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mina")
  }

  private var protocol: String = ""

  private var host: String = ""

  private var port: String = ""

  /**
   * Protocol to use
   */
  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol:$host:$port")
  }

  /**
   * Hostname to use. Use localhost or 0.0.0.0 for local server as consumer. For producer use the
   * hostname or ip address of the remote server.
   */
  public fun host(host: String) {
    this.host = host
    it.url("$protocol:$host:$port")
  }

  /**
   * Port number
   */
  public fun port(port: String) {
    this.port = port
    it.url("$protocol:$host:$port")
  }

  /**
   * Port number
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol:$host:$port")
  }

  /**
   * Whether to disconnect(close) from Mina session right after use. Can be used for both consumer
   * and producer.
   */
  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  /**
   * Whether to disconnect(close) from Mina session right after use. Can be used for both consumer
   * and producer.
   */
  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  /**
   * You can enable the Apache MINA logging filter. Apache MINA uses slf4j logging at INFO level to
   * log all input and output.
   */
  public fun minaLogger(minaLogger: String) {
    it.property("minaLogger", minaLogger)
  }

  /**
   * You can enable the Apache MINA logging filter. Apache MINA uses slf4j logging at INFO level to
   * log all input and output.
   */
  public fun minaLogger(minaLogger: Boolean) {
    it.property("minaLogger", minaLogger.toString())
  }

  /**
   * Setting to set endpoint as one-way or request-response.
   */
  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  /**
   * Setting to set endpoint as one-way or request-response.
   */
  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
  }

  /**
   * You can configure the timeout that specifies how long to wait for a response from a remote
   * server. The timeout unit is in milliseconds, so 60000 is 60 seconds.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * You can configure the timeout that specifies how long to wait for a response from a remote
   * server. The timeout unit is in milliseconds, so 60000 is 60 seconds.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Maximum amount of time it should take to send data to the MINA session. Default is 10000
   * milliseconds.
   */
  public fun writeTimeout(writeTimeout: String) {
    it.property("writeTimeout", writeTimeout)
  }

  /**
   * Maximum amount of time it should take to send data to the MINA session. Default is 10000
   * milliseconds.
   */
  public fun writeTimeout(writeTimeout: Int) {
    it.property("writeTimeout", writeTimeout.toString())
  }

  /**
   * If the clientMode is true, mina consumer will connect the address as a TCP client.
   */
  public fun clientMode(clientMode: String) {
    it.property("clientMode", clientMode)
  }

  /**
   * If the clientMode is true, mina consumer will connect the address as a TCP client.
   */
  public fun clientMode(clientMode: Boolean) {
    it.property("clientMode", clientMode.toString())
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
   * If sync is enabled this option dictates MinaConsumer which logging level to use when logging a
   * there is no reply to send back.
   */
  public fun noReplyLogLevel(noReplyLogLevel: String) {
    it.property("noReplyLogLevel", noReplyLogLevel)
  }

  /**
   * Whether to create the InetAddress once and reuse. Setting this to false allows to pickup DNS
   * changes in the network.
   */
  public fun cachedAddress(cachedAddress: String) {
    it.property("cachedAddress", cachedAddress)
  }

  /**
   * Whether to create the InetAddress once and reuse. Setting this to false allows to pickup DNS
   * changes in the network.
   */
  public fun cachedAddress(cachedAddress: Boolean) {
    it.property("cachedAddress", cachedAddress.toString())
  }

  /**
   * Sessions can be lazily created to avoid exceptions, if the remote server is not up and running
   * when the Camel producer is started.
   */
  public fun lazySessionCreation(lazySessionCreation: String) {
    it.property("lazySessionCreation", lazySessionCreation)
  }

  /**
   * Sessions can be lazily created to avoid exceptions, if the remote server is not up and running
   * when the Camel producer is started.
   */
  public fun lazySessionCreation(lazySessionCreation: Boolean) {
    it.property("lazySessionCreation", lazySessionCreation.toString())
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
   * If sync is enabled then this option dictates MinaConsumer if it should disconnect where there
   * is no reply to send back.
   */
  public fun disconnectOnNoReply(disconnectOnNoReply: String) {
    it.property("disconnectOnNoReply", disconnectOnNoReply)
  }

  /**
   * If sync is enabled then this option dictates MinaConsumer if it should disconnect where there
   * is no reply to send back.
   */
  public fun disconnectOnNoReply(disconnectOnNoReply: Boolean) {
    it.property("disconnectOnNoReply", disconnectOnNoReply.toString())
  }

  /**
   * Number of worker threads in the worker pool for TCP and UDP
   */
  public fun maximumPoolSize(maximumPoolSize: String) {
    it.property("maximumPoolSize", maximumPoolSize)
  }

  /**
   * Number of worker threads in the worker pool for TCP and UDP
   */
  public fun maximumPoolSize(maximumPoolSize: Int) {
    it.property("maximumPoolSize", maximumPoolSize.toString())
  }

  /**
   * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
   */
  public fun orderedThreadPoolExecutor(orderedThreadPoolExecutor: String) {
    it.property("orderedThreadPoolExecutor", orderedThreadPoolExecutor)
  }

  /**
   * Whether to use ordered thread pool, to ensure events are processed orderly on the same channel.
   */
  public fun orderedThreadPoolExecutor(orderedThreadPoolExecutor: Boolean) {
    it.property("orderedThreadPoolExecutor", orderedThreadPoolExecutor.toString())
  }

  /**
   * Only used for TCP. You can transfer the exchange over the wire instead of just the body. The
   * following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault
   * headers, exchange properties, exchange exception. This requires that the objects are serializable.
   * Camel will exclude any non-serializable objects and log it at WARN level. Also make sure to
   * configure objectCodecPattern to (star) to allow transferring java objects.
   */
  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  /**
   * Only used for TCP. You can transfer the exchange over the wire instead of just the body. The
   * following fields are transferred: In body, Out body, fault body, In headers, Out headers, fault
   * headers, exchange properties, exchange exception. This requires that the objects are serializable.
   * Camel will exclude any non-serializable objects and log it at WARN level. Also make sure to
   * configure objectCodecPattern to (star) to allow transferring java objects.
   */
  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  /**
   * The mina component installs a default codec if both, codec is null and textline is false.
   * Setting allowDefaultCodec to false prevents the mina component from installing a default codec as
   * the first element in the filter chain. This is useful in scenarios where another filter must be
   * the first in the filter chain, like the SSL filter.
   */
  public fun allowDefaultCodec(allowDefaultCodec: String) {
    it.property("allowDefaultCodec", allowDefaultCodec)
  }

  /**
   * The mina component installs a default codec if both, codec is null and textline is false.
   * Setting allowDefaultCodec to false prevents the mina component from installing a default codec as
   * the first element in the filter chain. This is useful in scenarios where another filter must be
   * the first in the filter chain, like the SSL filter.
   */
  public fun allowDefaultCodec(allowDefaultCodec: Boolean) {
    it.property("allowDefaultCodec", allowDefaultCodec.toString())
  }

  /**
   * To use a custom minda codec implementation.
   */
  public fun codec(codec: String) {
    it.property("codec", codec)
  }

  /**
   * To set the textline protocol decoder max line length. By default the default value of Mina
   * itself is used which are 1024.
   */
  public fun decoderMaxLineLength(decoderMaxLineLength: String) {
    it.property("decoderMaxLineLength", decoderMaxLineLength)
  }

  /**
   * To set the textline protocol decoder max line length. By default the default value of Mina
   * itself is used which are 1024.
   */
  public fun decoderMaxLineLength(decoderMaxLineLength: Int) {
    it.property("decoderMaxLineLength", decoderMaxLineLength.toString())
  }

  /**
   * To set the textline protocol encoder max line length. By default the default value of Mina
   * itself is used which are Integer.MAX_VALUE.
   */
  public fun encoderMaxLineLength(encoderMaxLineLength: String) {
    it.property("encoderMaxLineLength", encoderMaxLineLength)
  }

  /**
   * To set the textline protocol encoder max line length. By default the default value of Mina
   * itself is used which are Integer.MAX_VALUE.
   */
  public fun encoderMaxLineLength(encoderMaxLineLength: Int) {
    it.property("encoderMaxLineLength", encoderMaxLineLength.toString())
  }

  /**
   * You can configure the encoding (a charset name) to use for the TCP textline codec and the UDP
   * protocol. If not provided, Camel will use the JVM default Charset
   */
  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  /**
   * You can set a list of Mina IoFilters to use.
   */
  public fun filters(filters: String) {
    it.property("filters", filters)
  }

  /**
   * Accept the wildcard specified classes for Object deserialization, unless they are otherwise
   * rejected. Multiple patterns can be separated by comma.
   */
  public fun objectCodecPattern(objectCodecPattern: String) {
    it.property("objectCodecPattern", objectCodecPattern)
  }

  /**
   * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line
   * based codec; if not specified or the value is false, then Object Serialization is assumed over
   * TCP.
   */
  public fun textline(textline: String) {
    it.property("textline", textline)
  }

  /**
   * Only used for TCP. If no codec is specified, you can use this flag to indicate a text line
   * based codec; if not specified or the value is false, then Object Serialization is assumed over
   * TCP.
   */
  public fun textline(textline: Boolean) {
    it.property("textline", textline.toString())
  }

  /**
   * Only used for TCP and if textline=true. Sets the text line delimiter to use. If none provided,
   * Camel will use DEFAULT. This delimiter is used to mark the end of text.
   */
  public fun textlineDelimiter(textlineDelimiter: String) {
    it.property("textlineDelimiter", textlineDelimiter)
  }

  /**
   * To configure SSL security.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
