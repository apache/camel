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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Receive JMX notifications.
 */
public fun UriDsl.jmx(i: JmxUriDsl.() -> Unit) {
  JmxUriDsl(this).apply(i)
}

@CamelDslMarker
public class JmxUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jmx")
  }

  private var serverURL: String = ""

  /**
   * Server url comes from the remaining endpoint. Use platform to connect to local JVM.
   */
  public fun serverURL(serverURL: String) {
    this.serverURL = serverURL
    it.url("$serverURL")
  }

  /**
   * Format for the message body. Either xml or raw. If xml, the notification is serialized to xml.
   * If raw, then the raw java object is set as the body.
   */
  public fun format(format: String) {
    it.property("format", format)
  }

  /**
   * The frequency to poll the bean to check the monitor (monitor types only).
   */
  public fun granularityPeriod(granularityPeriod: String) {
    it.property("granularityPeriod", granularityPeriod)
  }

  /**
   * The type of monitor to create. One of string, gauge, counter (monitor types only).
   */
  public fun monitorType(monitorType: String) {
    it.property("monitorType", monitorType)
  }

  /**
   * The domain for the mbean you're connecting to
   */
  public fun objectDomain(objectDomain: String) {
    it.property("objectDomain", objectDomain)
  }

  /**
   * The name key for the mbean you're connecting to. This value is mutually exclusive with the
   * object properties that get passed.
   */
  public fun objectName(objectName: String) {
    it.property("objectName", objectName)
  }

  /**
   * The attribute to observe for the monitor bean or consumer.
   */
  public fun observedAttribute(observedAttribute: String) {
    it.property("observedAttribute", observedAttribute)
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
   * To use a custom shared thread pool for the consumers. By default each consume has their own
   * thread-pool to process and route notifications.
   */
  public fun executorService(executorService: String) {
    it.property("executorService", executorService)
  }

  /**
   * Value to handback to the listener when a notification is received. This value will be put in
   * the message header with the key JMXConstants#JMX_HANDBACK.
   */
  public fun handback(handback: String) {
    it.property("handback", handback)
  }

  /**
   * Reference to a bean that implements the NotificationFilter.
   */
  public fun notificationFilter(notificationFilter: String) {
    it.property("notificationFilter", notificationFilter)
  }

  /**
   * Properties for the object name. These values will be used if the objectName param is not set
   */
  public fun objectProperties(objectProperties: String) {
    it.property("objectProperties", objectProperties)
  }

  /**
   * The number of seconds to wait before attempting to retry establishment of the initial
   * connection or attempt to reconnect a lost connection
   */
  public fun reconnectDelay(reconnectDelay: String) {
    it.property("reconnectDelay", reconnectDelay)
  }

  /**
   * The number of seconds to wait before attempting to retry establishment of the initial
   * connection or attempt to reconnect a lost connection
   */
  public fun reconnectDelay(reconnectDelay: Int) {
    it.property("reconnectDelay", reconnectDelay.toString())
  }

  /**
   * If true the consumer will attempt to reconnect to the JMX server when any connection failure
   * occurs. The consumer will attempt to re-establish the JMX connection every 'x' seconds until the
   * connection is made-- where 'x' is the configured reconnectionDelay
   */
  public fun reconnectOnConnectionFailure(reconnectOnConnectionFailure: String) {
    it.property("reconnectOnConnectionFailure", reconnectOnConnectionFailure)
  }

  /**
   * If true the consumer will attempt to reconnect to the JMX server when any connection failure
   * occurs. The consumer will attempt to re-establish the JMX connection every 'x' seconds until the
   * connection is made-- where 'x' is the configured reconnectionDelay
   */
  public fun reconnectOnConnectionFailure(reconnectOnConnectionFailure: Boolean) {
    it.property("reconnectOnConnectionFailure", reconnectOnConnectionFailure.toString())
  }

  /**
   * If true the consumer will throw an exception if unable to establish the JMX connection upon
   * startup. If false, the consumer will attempt to establish the JMX connection every 'x' seconds
   * until the connection is made -- where 'x' is the configured reconnectionDelay
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  /**
   * If true the consumer will throw an exception if unable to establish the JMX connection upon
   * startup. If false, the consumer will attempt to establish the JMX connection every 'x' seconds
   * until the connection is made -- where 'x' is the configured reconnectionDelay
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
  }

  /**
   * Initial threshold for the monitor. The value must exceed this before notifications are fired
   * (counter monitor only).
   */
  public fun initThreshold(initThreshold: String) {
    it.property("initThreshold", initThreshold)
  }

  /**
   * Initial threshold for the monitor. The value must exceed this before notifications are fired
   * (counter monitor only).
   */
  public fun initThreshold(initThreshold: Int) {
    it.property("initThreshold", initThreshold.toString())
  }

  /**
   * The value at which the counter is reset to zero (counter monitor only).
   */
  public fun modulus(modulus: String) {
    it.property("modulus", modulus)
  }

  /**
   * The value at which the counter is reset to zero (counter monitor only).
   */
  public fun modulus(modulus: Int) {
    it.property("modulus", modulus.toString())
  }

  /**
   * The amount to increment the threshold after it's been exceeded (counter monitor only).
   */
  public fun offset(offset: String) {
    it.property("offset", offset)
  }

  /**
   * The amount to increment the threshold after it's been exceeded (counter monitor only).
   */
  public fun offset(offset: Int) {
    it.property("offset", offset.toString())
  }

  /**
   * If true, then the value reported in the notification is the difference from the threshold as
   * opposed to the value itself (counter and gauge monitor only).
   */
  public fun differenceMode(differenceMode: String) {
    it.property("differenceMode", differenceMode)
  }

  /**
   * If true, then the value reported in the notification is the difference from the threshold as
   * opposed to the value itself (counter and gauge monitor only).
   */
  public fun differenceMode(differenceMode: Boolean) {
    it.property("differenceMode", differenceMode.toString())
  }

  /**
   * If true, the gauge will fire a notification when the high threshold is exceeded (gauge monitor
   * only).
   */
  public fun notifyHigh(notifyHigh: String) {
    it.property("notifyHigh", notifyHigh)
  }

  /**
   * If true, the gauge will fire a notification when the high threshold is exceeded (gauge monitor
   * only).
   */
  public fun notifyHigh(notifyHigh: Boolean) {
    it.property("notifyHigh", notifyHigh.toString())
  }

  /**
   * If true, the gauge will fire a notification when the low threshold is exceeded (gauge monitor
   * only).
   */
  public fun notifyLow(notifyLow: String) {
    it.property("notifyLow", notifyLow)
  }

  /**
   * If true, the gauge will fire a notification when the low threshold is exceeded (gauge monitor
   * only).
   */
  public fun notifyLow(notifyLow: Boolean) {
    it.property("notifyLow", notifyLow.toString())
  }

  /**
   * Value for the gauge's high threshold (gauge monitor only).
   */
  public fun thresholdHigh(thresholdHigh: String) {
    it.property("thresholdHigh", thresholdHigh)
  }

  /**
   * Value for the gauge's high threshold (gauge monitor only).
   */
  public fun thresholdHigh(thresholdHigh: Double) {
    it.property("thresholdHigh", thresholdHigh.toString())
  }

  /**
   * Value for the gauge's low threshold (gauge monitor only).
   */
  public fun thresholdLow(thresholdLow: String) {
    it.property("thresholdLow", thresholdLow)
  }

  /**
   * Value for the gauge's low threshold (gauge monitor only).
   */
  public fun thresholdLow(thresholdLow: Double) {
    it.property("thresholdLow", thresholdLow.toString())
  }

  /**
   * Credentials for making a remote connection
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Credentials for making a remote connection
   */
  public fun user(user: String) {
    it.property("user", user)
  }

  /**
   * If true, will fire a notification when the string attribute differs from the string to compare
   * (string monitor or consumer). By default the consumer will notify match if observed attribute and
   * string to compare has been configured.
   */
  public fun notifyDiffer(notifyDiffer: String) {
    it.property("notifyDiffer", notifyDiffer)
  }

  /**
   * If true, will fire a notification when the string attribute differs from the string to compare
   * (string monitor or consumer). By default the consumer will notify match if observed attribute and
   * string to compare has been configured.
   */
  public fun notifyDiffer(notifyDiffer: Boolean) {
    it.property("notifyDiffer", notifyDiffer.toString())
  }

  /**
   * If true, will fire a notification when the string attribute matches the string to compare
   * (string monitor or consumer). By default the consumer will notify match if observed attribute and
   * string to compare has been configured.
   */
  public fun notifyMatch(notifyMatch: String) {
    it.property("notifyMatch", notifyMatch)
  }

  /**
   * If true, will fire a notification when the string attribute matches the string to compare
   * (string monitor or consumer). By default the consumer will notify match if observed attribute and
   * string to compare has been configured.
   */
  public fun notifyMatch(notifyMatch: Boolean) {
    it.property("notifyMatch", notifyMatch.toString())
  }

  /**
   * Value for attribute to compare (string monitor or consumer). By default the consumer will
   * notify match if observed attribute and string to compare has been configured.
   */
  public fun stringToCompare(stringToCompare: String) {
    it.property("stringToCompare", stringToCompare)
  }
}
