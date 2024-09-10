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
 * Sparkplug B Host Application support over MQTT using Eclipse Tahu
 */
public fun UriDsl.`tahu-host`(i: TahuHostUriDsl.() -> Unit) {
  TahuHostUriDsl(this).apply(i)
}

@CamelDslMarker
public class TahuHostUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("tahu-host")
  }

  private var hostId: String = ""

  /**
   * ID for the host application
   */
  public fun hostId(hostId: String) {
    this.hostId = hostId
    it.url("$hostId")
  }

  /**
   * MQTT client ID length check enabled
   */
  public fun checkClientIdLength(checkClientIdLength: String) {
    it.property("checkClientIdLength", checkClientIdLength)
  }

  /**
   * MQTT client ID length check enabled
   */
  public fun checkClientIdLength(checkClientIdLength: Boolean) {
    it.property("checkClientIdLength", checkClientIdLength.toString())
  }

  /**
   * MQTT client ID to use for all server definitions, rather than specifying the same one for each.
   * Note that if neither the 'clientId' parameter nor an 'MqttClientId' are defined for an MQTT
   * Server, a random MQTT Client ID will be generated automatically, prefaced with 'Camel'
   */
  public fun clientId(clientId: String) {
    it.property("clientId", clientId)
  }

  /**
   * MQTT connection keep alive timeout, in seconds
   */
  public fun keepAliveTimeout(keepAliveTimeout: String) {
    it.property("keepAliveTimeout", keepAliveTimeout)
  }

  /**
   * MQTT connection keep alive timeout, in seconds
   */
  public fun keepAliveTimeout(keepAliveTimeout: Int) {
    it.property("keepAliveTimeout", keepAliveTimeout.toString())
  }

  /**
   * Delay before recurring node rebirth messages will be sent
   */
  public fun rebirthDebounceDelay(rebirthDebounceDelay: String) {
    it.property("rebirthDebounceDelay", rebirthDebounceDelay)
  }

  /**
   * Delay before recurring node rebirth messages will be sent
   */
  public fun rebirthDebounceDelay(rebirthDebounceDelay: Int) {
    it.property("rebirthDebounceDelay", rebirthDebounceDelay.toString())
  }

  /**
   * MQTT server definitions, given with the following syntax in a comma-separated list:
   * MqttServerName:(MqttClientId:)(tcp/ssl)://hostname(:port),...
   */
  public fun servers(servers: String) {
    it.property("servers", servers)
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
   * Password for MQTT server authentication
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * SSL configuration for MQTT server connections
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * Username for MQTT server authentication
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
