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
 * Produce or consume Apache Avro RPC services.
 */
public fun UriDsl.avro(i: AvroUriDsl.() -> Unit) {
  AvroUriDsl(this).apply(i)
}

@CamelDslMarker
public class AvroUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("avro")
  }

  private var transport: String = ""

  private var port: String = ""

  private var host: String = ""

  private var messageName: String = ""

  /**
   * Transport to use, can be either http or netty
   */
  public fun transport(transport: String) {
    this.transport = transport
    it.url("$transport:$host:$port/$messageName")
  }

  /**
   * Port number to use
   */
  public fun port(port: String) {
    this.port = port
    it.url("$transport:$host:$port/$messageName")
  }

  /**
   * Port number to use
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$transport:$host:$port/$messageName")
  }

  /**
   * Hostname to use
   */
  public fun host(host: String) {
    this.host = host
    it.url("$transport:$host:$port/$messageName")
  }

  /**
   * The name of the message to send.
   */
  public fun messageName(messageName: String) {
    this.messageName = messageName
    it.url("$transport:$host:$port/$messageName")
  }

  /**
   * Avro protocol to use
   */
  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  /**
   * Avro protocol to use defined by the FQN class name
   */
  public fun protocolClassName(protocolClassName: String) {
    it.property("protocolClassName", protocolClassName)
  }

  /**
   * Avro protocol location
   */
  public fun protocolLocation(protocolLocation: String) {
    it.property("protocolLocation", protocolLocation)
  }

  /**
   * If the protocol object provided is reflection protocol. Should be used only with protocol
   * parameter because for protocolClassName protocol type will be auto-detected
   */
  public fun reflectionProtocol(reflectionProtocol: String) {
    it.property("reflectionProtocol", reflectionProtocol)
  }

  /**
   * If the protocol object provided is reflection protocol. Should be used only with protocol
   * parameter because for protocolClassName protocol type will be auto-detected
   */
  public fun reflectionProtocol(reflectionProtocol: Boolean) {
    it.property("reflectionProtocol", reflectionProtocol.toString())
  }

  /**
   * If true, consumer parameter won't be wrapped into an array. Will fail if protocol specifies
   * more than one parameter for the message
   */
  public fun singleParameter(singleParameter: String) {
    it.property("singleParameter", singleParameter)
  }

  /**
   * If true, consumer parameter won't be wrapped into an array. Will fail if protocol specifies
   * more than one parameter for the message
   */
  public fun singleParameter(singleParameter: Boolean) {
    it.property("singleParameter", singleParameter.toString())
  }

  /**
   * Authority to use (username and password)
   */
  public fun uriAuthority(uriAuthority: String) {
    it.property("uriAuthority", uriAuthority)
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
}
