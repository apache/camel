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
 * Communicate with external systems using the MLLP protocol.
 */
public fun UriDsl.mllp(i: MllpUriDsl.() -> Unit) {
  MllpUriDsl(this).apply(i)
}

@CamelDslMarker
public class MllpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mllp")
  }

  private var hostname: String = ""

  private var port: String = ""

  /**
   * Hostname or IP for connection for the TCP connection. The default value is null, which means
   * any local IP address
   */
  public fun hostname(hostname: String) {
    this.hostname = hostname
    it.url("$hostname:$port")
  }

  /**
   * Port number for the TCP connection
   */
  public fun port(port: String) {
    this.port = port
    it.url("$hostname:$port")
  }

  /**
   * Port number for the TCP connection
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$hostname:$port")
  }

  /**
   * Enable/Disable the automatic generation of a MLLP Acknowledgement MLLP Consumers only
   */
  public fun autoAck(autoAck: String) {
    it.property("autoAck", autoAck)
  }

  /**
   * Enable/Disable the automatic generation of a MLLP Acknowledgement MLLP Consumers only
   */
  public fun autoAck(autoAck: Boolean) {
    it.property("autoAck", autoAck.toString())
  }

  /**
   * Sets the default charset to use
   */
  public fun charsetName(charsetName: String) {
    it.property("charsetName", charsetName)
  }

  /**
   * Enable/Disable the automatic generation of message headers from the HL7 Message MLLP Consumers
   * only
   */
  public fun hl7Headers(hl7Headers: String) {
    it.property("hl7Headers", hl7Headers)
  }

  /**
   * Enable/Disable the automatic generation of message headers from the HL7 Message MLLP Consumers
   * only
   */
  public fun hl7Headers(hl7Headers: Boolean) {
    it.property("hl7Headers", hl7Headers.toString())
  }

  /**
   * Enable/Disable strict compliance to the MLLP standard. The MLLP standard specifies
   * START_OF_BLOCKhl7 payloadEND_OF_BLOCKEND_OF_DATA, however, some systems do not send the final
   * END_OF_DATA byte. This setting controls whether or not the final END_OF_DATA byte is required or
   * optional.
   */
  public fun requireEndOfData(requireEndOfData: String) {
    it.property("requireEndOfData", requireEndOfData)
  }

  /**
   * Enable/Disable strict compliance to the MLLP standard. The MLLP standard specifies
   * START_OF_BLOCKhl7 payloadEND_OF_BLOCKEND_OF_DATA, however, some systems do not send the final
   * END_OF_DATA byte. This setting controls whether or not the final END_OF_DATA byte is required or
   * optional.
   */
  public fun requireEndOfData(requireEndOfData: Boolean) {
    it.property("requireEndOfData", requireEndOfData.toString())
  }

  /**
   * Enable/Disable converting the payload to a String. If enabled, HL7 Payloads received from
   * external systems will be validated converted to a String. If the charsetName property is set, that
   * character set will be used for the conversion. If the charsetName property is not set, the value
   * of MSH-18 will be used to determine th appropriate character set. If MSH-18 is not set, then the
   * default ISO-8859-1 character set will be use.
   */
  public fun stringPayload(stringPayload: String) {
    it.property("stringPayload", stringPayload)
  }

  /**
   * Enable/Disable converting the payload to a String. If enabled, HL7 Payloads received from
   * external systems will be validated converted to a String. If the charsetName property is set, that
   * character set will be used for the conversion. If the charsetName property is not set, the value
   * of MSH-18 will be used to determine th appropriate character set. If MSH-18 is not set, then the
   * default ISO-8859-1 character set will be use.
   */
  public fun stringPayload(stringPayload: Boolean) {
    it.property("stringPayload", stringPayload.toString())
  }

  /**
   * Enable/Disable the validation of HL7 Payloads If enabled, HL7 Payloads received from external
   * systems will be validated (see Hl7Util.generateInvalidPayloadExceptionMessage for details on the
   * validation). If and invalid payload is detected, a MllpInvalidMessageException (for consumers) or
   * a MllpInvalidAcknowledgementException will be thrown.
   */
  public fun validatePayload(validatePayload: String) {
    it.property("validatePayload", validatePayload)
  }

  /**
   * Enable/Disable the validation of HL7 Payloads If enabled, HL7 Payloads received from external
   * systems will be validated (see Hl7Util.generateInvalidPayloadExceptionMessage for details on the
   * validation). If and invalid payload is detected, a MllpInvalidMessageException (for consumers) or
   * a MllpInvalidAcknowledgementException will be thrown.
   */
  public fun validatePayload(validatePayload: Boolean) {
    it.property("validatePayload", validatePayload.toString())
  }

  /**
   * Timeout (in milliseconds) while waiting for a TCP connection TCP Server Only
   */
  public fun acceptTimeout(acceptTimeout: String) {
    it.property("acceptTimeout", acceptTimeout)
  }

  /**
   * Timeout (in milliseconds) while waiting for a TCP connection TCP Server Only
   */
  public fun acceptTimeout(acceptTimeout: Int) {
    it.property("acceptTimeout", acceptTimeout.toString())
  }

  /**
   * The maximum queue length for incoming connection indications (a request to connect) is set to
   * the backlog parameter. If a connection indication arrives when the queue is full, the connection
   * is refused.
   */
  public fun backlog(backlog: String) {
    it.property("backlog", backlog)
  }

  /**
   * The maximum queue length for incoming connection indications (a request to connect) is set to
   * the backlog parameter. If a connection indication arrives when the queue is full, the connection
   * is refused.
   */
  public fun backlog(backlog: Int) {
    it.property("backlog", backlog.toString())
  }

  /**
   * TCP Server Only - The number of milliseconds to wait between bind attempts
   */
  public fun bindRetryInterval(bindRetryInterval: String) {
    it.property("bindRetryInterval", bindRetryInterval)
  }

  /**
   * TCP Server Only - The number of milliseconds to wait between bind attempts
   */
  public fun bindRetryInterval(bindRetryInterval: Int) {
    it.property("bindRetryInterval", bindRetryInterval.toString())
  }

  /**
   * TCP Server Only - The number of milliseconds to retry binding to a server port
   */
  public fun bindTimeout(bindTimeout: String) {
    it.property("bindTimeout", bindTimeout)
  }

  /**
   * TCP Server Only - The number of milliseconds to retry binding to a server port
   */
  public fun bindTimeout(bindTimeout: Int) {
    it.property("bindTimeout", bindTimeout.toString())
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * occurred while the consumer is trying to receive incoming messages, or the likes, will now be
   * processed as a message and handled by the routing Error Handler. If disabled, the consumer will
   * use the org.apache.camel.spi.ExceptionHandler to deal with exceptions by logging them at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * occurred while the consumer is trying to receive incoming messages, or the likes, will now be
   * processed as a message and handled by the routing Error Handler. If disabled, the consumer will
   * use the org.apache.camel.spi.ExceptionHandler to deal with exceptions by logging them at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * TCP Server Only - Allow the endpoint to start before the TCP ServerSocket is bound. In some
   * environments, it may be desirable to allow the endpoint to start before the TCP ServerSocket is
   * bound.
   */
  public fun lenientBind(lenientBind: String) {
    it.property("lenientBind", lenientBind)
  }

  /**
   * TCP Server Only - Allow the endpoint to start before the TCP ServerSocket is bound. In some
   * environments, it may be desirable to allow the endpoint to start before the TCP ServerSocket is
   * bound.
   */
  public fun lenientBind(lenientBind: Boolean) {
    it.property("lenientBind", lenientBind.toString())
  }

  /**
   * The maximum number of concurrent MLLP Consumer connections that will be allowed. If a new
   * connection is received and the maximum is number are already established, the new connection will
   * be reset immediately.
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: String) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers)
  }

  /**
   * The maximum number of concurrent MLLP Consumer connections that will be allowed. If a new
   * connection is received and the maximum is number are already established, the new connection will
   * be reset immediately.
   */
  public fun maxConcurrentConsumers(maxConcurrentConsumers: Int) {
    it.property("maxConcurrentConsumers", maxConcurrentConsumers.toString())
  }

  /**
   * Enable/disable the SO_REUSEADDR socket option.
   */
  public fun reuseAddress(reuseAddress: String) {
    it.property("reuseAddress", reuseAddress)
  }

  /**
   * Enable/disable the SO_REUSEADDR socket option.
   */
  public fun reuseAddress(reuseAddress: Boolean) {
    it.property("reuseAddress", reuseAddress.toString())
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
   * Timeout (in milliseconds) for establishing for a TCP connection TCP Client only
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * Timeout (in milliseconds) for establishing for a TCP connection TCP Client only
   */
  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  /**
   * decide what action to take when idle timeout occurs. Possible values are : RESET: set SO_LINGER
   * to 0 and reset the socket CLOSE: close the socket gracefully default is RESET.
   */
  public fun idleTimeoutStrategy(idleTimeoutStrategy: String) {
    it.property("idleTimeoutStrategy", idleTimeoutStrategy)
  }

  /**
   * Enable/disable the SO_KEEPALIVE socket option.
   */
  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  /**
   * Enable/disable the SO_KEEPALIVE socket option.
   */
  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  /**
   * Enable/disable the TCP_NODELAY socket option.
   */
  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  /**
   * Enable/disable the TCP_NODELAY socket option.
   */
  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
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
   * Maximum buffer size used when receiving or sending data over the wire.
   */
  public fun maxBufferSize(maxBufferSize: String) {
    it.property("maxBufferSize", maxBufferSize)
  }

  /**
   * Maximum buffer size used when receiving or sending data over the wire.
   */
  public fun maxBufferSize(maxBufferSize: Int) {
    it.property("maxBufferSize", maxBufferSize.toString())
  }

  /**
   * Minimum buffer size used when receiving or sending data over the wire.
   */
  public fun minBufferSize(minBufferSize: String) {
    it.property("minBufferSize", minBufferSize)
  }

  /**
   * Minimum buffer size used when receiving or sending data over the wire.
   */
  public fun minBufferSize(minBufferSize: Int) {
    it.property("minBufferSize", minBufferSize.toString())
  }

  /**
   * The SO_TIMEOUT value (in milliseconds) used after the start of an MLLP frame has been received
   */
  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  /**
   * The SO_TIMEOUT value (in milliseconds) used after the start of an MLLP frame has been received
   */
  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }

  /**
   * Sets the SO_RCVBUF option to the specified value (in bytes)
   */
  public fun receiveBufferSize(receiveBufferSize: String) {
    it.property("receiveBufferSize", receiveBufferSize)
  }

  /**
   * Sets the SO_RCVBUF option to the specified value (in bytes)
   */
  public fun receiveBufferSize(receiveBufferSize: Int) {
    it.property("receiveBufferSize", receiveBufferSize.toString())
  }

  /**
   * The SO_TIMEOUT value (in milliseconds) used when waiting for the start of an MLLP frame
   */
  public fun receiveTimeout(receiveTimeout: String) {
    it.property("receiveTimeout", receiveTimeout)
  }

  /**
   * The SO_TIMEOUT value (in milliseconds) used when waiting for the start of an MLLP frame
   */
  public fun receiveTimeout(receiveTimeout: Int) {
    it.property("receiveTimeout", receiveTimeout.toString())
  }

  /**
   * Sets the SO_SNDBUF option to the specified value (in bytes)
   */
  public fun sendBufferSize(sendBufferSize: String) {
    it.property("sendBufferSize", sendBufferSize)
  }

  /**
   * Sets the SO_SNDBUF option to the specified value (in bytes)
   */
  public fun sendBufferSize(sendBufferSize: Int) {
    it.property("sendBufferSize", sendBufferSize.toString())
  }

  /**
   * The approximate idle time allowed before the Client TCP Connection will be reset. A null value
   * or a value less than or equal to zero will disable the idle timeout.
   */
  public fun idleTimeout(idleTimeout: String) {
    it.property("idleTimeout", idleTimeout)
  }

  /**
   * The approximate idle time allowed before the Client TCP Connection will be reset. A null value
   * or a value less than or equal to zero will disable the idle timeout.
   */
  public fun idleTimeout(idleTimeout: Int) {
    it.property("idleTimeout", idleTimeout.toString())
  }
}
