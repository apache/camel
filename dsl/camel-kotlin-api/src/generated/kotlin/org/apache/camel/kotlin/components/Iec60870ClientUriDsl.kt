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
 * IEC 60870 supervisory control and data acquisition (SCADA) client using NeoSCADA implementation.
 */
public fun UriDsl.`iec60870-client`(i: Iec60870ClientUriDsl.() -> Unit) {
  Iec60870ClientUriDsl(this).apply(i)
}

@CamelDslMarker
public class Iec60870ClientUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("iec60870-client")
  }

  private var uriPath: String = ""

  /**
   * The object information address
   */
  public fun uriPath(uriPath: String) {
    this.uriPath = uriPath
    it.url("$uriPath")
  }

  /**
   * Data module options
   */
  public fun dataModuleOptions(dataModuleOptions: String) {
    it.property("dataModuleOptions", dataModuleOptions)
  }

  /**
   * Protocol options
   */
  public fun protocolOptions(protocolOptions: String) {
    it.property("protocolOptions", protocolOptions)
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

  /**
   * Parameter W - Acknowledgment window.
   */
  public fun acknowledgeWindow(acknowledgeWindow: String) {
    it.property("acknowledgeWindow", acknowledgeWindow)
  }

  /**
   * Parameter W - Acknowledgment window.
   */
  public fun acknowledgeWindow(acknowledgeWindow: Int) {
    it.property("acknowledgeWindow", acknowledgeWindow.toString())
  }

  /**
   * The common ASDU address size. May be either SIZE_1 or SIZE_2.
   */
  public fun adsuAddressType(adsuAddressType: String) {
    it.property("adsuAddressType", adsuAddressType)
  }

  /**
   * The cause of transmission type. May be either SIZE_1 or SIZE_2.
   */
  public fun causeOfTransmissionType(causeOfTransmissionType: String) {
    it.property("causeOfTransmissionType", causeOfTransmissionType)
  }

  /**
   * The information address size. May be either SIZE_1, SIZE_2 or SIZE_3.
   */
  public fun informationObjectAddressType(informationObjectAddressType: String) {
    it.property("informationObjectAddressType", informationObjectAddressType)
  }

  /**
   * Parameter K - Maximum number of un-acknowledged messages.
   */
  public fun maxUnacknowledged(maxUnacknowledged: String) {
    it.property("maxUnacknowledged", maxUnacknowledged)
  }

  /**
   * Parameter K - Maximum number of un-acknowledged messages.
   */
  public fun maxUnacknowledged(maxUnacknowledged: Int) {
    it.property("maxUnacknowledged", maxUnacknowledged.toString())
  }

  /**
   * Timeout T1 in milliseconds.
   */
  public fun timeout1(timeout1: String) {
    it.property("timeout1", timeout1)
  }

  /**
   * Timeout T1 in milliseconds.
   */
  public fun timeout1(timeout1: Int) {
    it.property("timeout1", timeout1.toString())
  }

  /**
   * Timeout T2 in milliseconds.
   */
  public fun timeout2(timeout2: String) {
    it.property("timeout2", timeout2)
  }

  /**
   * Timeout T2 in milliseconds.
   */
  public fun timeout2(timeout2: Int) {
    it.property("timeout2", timeout2.toString())
  }

  /**
   * Timeout T3 in milliseconds.
   */
  public fun timeout3(timeout3: String) {
    it.property("timeout3", timeout3)
  }

  /**
   * Timeout T3 in milliseconds.
   */
  public fun timeout3(timeout3: Int) {
    it.property("timeout3", timeout3.toString())
  }

  /**
   * Whether to include the source address
   */
  public fun causeSourceAddress(causeSourceAddress: String) {
    it.property("causeSourceAddress", causeSourceAddress)
  }

  /**
   * Whether to include the source address
   */
  public fun causeSourceAddress(causeSourceAddress: Int) {
    it.property("causeSourceAddress", causeSourceAddress.toString())
  }

  /**
   * Timeout in millis to wait for client to establish a connected connection.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Timeout in millis to wait for client to establish a connected connection.
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * Whether background scan transmissions should be ignored.
   */
  public fun ignoreBackgroundScan(ignoreBackgroundScan: String) {
    it.property("ignoreBackgroundScan", ignoreBackgroundScan)
  }

  /**
   * Whether background scan transmissions should be ignored.
   */
  public fun ignoreBackgroundScan(ignoreBackgroundScan: Boolean) {
    it.property("ignoreBackgroundScan", ignoreBackgroundScan.toString())
  }

  /**
   * Whether to ignore or respect DST
   */
  public fun ignoreDaylightSavingTime(ignoreDaylightSavingTime: String) {
    it.property("ignoreDaylightSavingTime", ignoreDaylightSavingTime)
  }

  /**
   * Whether to ignore or respect DST
   */
  public fun ignoreDaylightSavingTime(ignoreDaylightSavingTime: Boolean) {
    it.property("ignoreDaylightSavingTime", ignoreDaylightSavingTime.toString())
  }

  /**
   * The timezone to use. May be any Java time zone string
   */
  public fun timeZone(timeZone: String) {
    it.property("timeZone", timeZone)
  }

  /**
   * An identifier grouping connection instances
   */
  public fun connectionId(connectionId: String) {
    it.property("connectionId", connectionId)
  }
}
