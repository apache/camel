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
 * Receive traps and poll SNMP (Simple Network Management Protocol) capable devices.
 */
public fun UriDsl.snmp(i: SnmpUriDsl.() -> Unit) {
  SnmpUriDsl(this).apply(i)
}

@CamelDslMarker
public class SnmpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("snmp")
  }

  private var host: String = ""

  private var port: String = ""

  /**
   * Hostname of the SNMP enabled device
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  /**
   * Port number of the SNMP enabled device
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  /**
   * Port number of the SNMP enabled device
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  /**
   * Defines which values you are interested in. Please have a look at the Wikipedia to get a better
   * understanding. You may provide a single OID or a coma separated list of OIDs. Example:
   * oids=1.3.6.1.2.1.1.3.0,1.3.6.1.2.1.25.3.2.1.5.1,1.3.6.1.2.1.25.3.5.1.1.1,1.3.6.1.2.1.43.5.1.1.11.1
   */
  public fun oids(oids: String) {
    it.property("oids", oids)
  }

  /**
   * Here you can select which protocol to use. You can use either udp or tcp.
   */
  public fun protocol(protocol: String) {
    it.property("protocol", protocol)
  }

  /**
   * Defines how often a retry is made before canceling the request.
   */
  public fun retries(retries: String) {
    it.property("retries", retries)
  }

  /**
   * Defines how often a retry is made before canceling the request.
   */
  public fun retries(retries: Int) {
    it.property("retries", retries.toString())
  }

  /**
   * Sets the community octet string for the snmp request.
   */
  public fun snmpCommunity(snmpCommunity: String) {
    it.property("snmpCommunity", snmpCommunity)
  }

  /**
   * Sets the context engine ID field of the scoped PDU.
   */
  public fun snmpContextEngineId(snmpContextEngineId: String) {
    it.property("snmpContextEngineId", snmpContextEngineId)
  }

  /**
   * Sets the context name field of this scoped PDU.
   */
  public fun snmpContextName(snmpContextName: String) {
    it.property("snmpContextName", snmpContextName)
  }

  /**
   * Sets the snmp version for the request. The value 0 means SNMPv1, 1 means SNMPv2c, and the value
   * 3 means SNMPv3
   */
  public fun snmpVersion(snmpVersion: String) {
    it.property("snmpVersion", snmpVersion)
  }

  /**
   * Sets the snmp version for the request. The value 0 means SNMPv1, 1 means SNMPv2c, and the value
   * 3 means SNMPv3
   */
  public fun snmpVersion(snmpVersion: Int) {
    it.property("snmpVersion", snmpVersion.toString())
  }

  /**
   * Sets the timeout value for the request in millis.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets the timeout value for the request in millis.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Which operation to perform such as poll, trap, etc.
   */
  public fun type(type: String) {
    it.property("type", type)
  }

  /**
   * Sets update rate in seconds
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  /**
   * Sets the flag whether the scoped PDU will be displayed as the list if it has child elements in
   * its tree
   */
  public fun treeList(treeList: String) {
    it.property("treeList", treeList)
  }

  /**
   * Sets the flag whether the scoped PDU will be displayed as the list if it has child elements in
   * its tree
   */
  public fun treeList(treeList: Boolean) {
    it.property("treeList", treeList.toString())
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
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
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
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  /**
   * Allows for configuring a custom/shared thread pool to use for the consumer. By default each
   * consumer has its own single threaded thread pool.
   */
  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  /**
   * To use a cron scheduler from either camel-spring or camel-quartz component. Use value spring or
   * quartz for built in scheduler
   */
  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  /**
   * To configure additional properties when using a custom scheduler or any of the Quartz, Spring
   * based scheduler.
   */
  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  /**
   * Time unit for initialDelay and delay options.
   */
  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  /**
   * The authentication passphrase. If not null, authenticationProtocol must also be not null.
   * RFC3414 11.2 requires passphrases to have a minimum length of 8 bytes. If the length of
   * authenticationPassphrase is less than 8 bytes an IllegalArgumentException is thrown.
   */
  public fun authenticationPassphrase(authenticationPassphrase: String) {
    it.property("authenticationPassphrase", authenticationPassphrase)
  }

  /**
   * Authentication protocol to use if security level is set to enable authentication The possible
   * values are: MD5, SHA1
   */
  public fun authenticationProtocol(authenticationProtocol: String) {
    it.property("authenticationProtocol", authenticationProtocol)
  }

  /**
   * The privacy passphrase. If not null, privacyProtocol must also be not null. RFC3414 11.2
   * requires passphrases to have a minimum length of 8 bytes. If the length of
   * authenticationPassphrase is less than 8 bytes an IllegalArgumentException is thrown.
   */
  public fun privacyPassphrase(privacyPassphrase: String) {
    it.property("privacyPassphrase", privacyPassphrase)
  }

  /**
   * The privacy protocol ID to be associated with this user. If set to null, this user only
   * supports unencrypted messages.
   */
  public fun privacyProtocol(privacyProtocol: String) {
    it.property("privacyProtocol", privacyProtocol)
  }

  /**
   * Sets the security level for this target. The supplied security level must be supported by the
   * security model dependent information associated with the security name set for this target. The
   * value 1 means: No authentication and no encryption. Anyone can create and read messages with this
   * security level The value 2 means: Authentication and no encryption. Only the one with the right
   * authentication key can create messages with this security level, but anyone can read the contents
   * of the message. The value 3 means: Authentication and encryption. Only the one with the right
   * authentication key can create messages with this security level, and only the one with the right
   * encryption/decryption key can read the contents of the message.
   */
  public fun securityLevel(securityLevel: String) {
    it.property("securityLevel", securityLevel)
  }

  /**
   * Sets the security level for this target. The supplied security level must be supported by the
   * security model dependent information associated with the security name set for this target. The
   * value 1 means: No authentication and no encryption. Anyone can create and read messages with this
   * security level The value 2 means: Authentication and no encryption. Only the one with the right
   * authentication key can create messages with this security level, but anyone can read the contents
   * of the message. The value 3 means: Authentication and encryption. Only the one with the right
   * authentication key can create messages with this security level, and only the one with the right
   * encryption/decryption key can read the contents of the message.
   */
  public fun securityLevel(securityLevel: Int) {
    it.property("securityLevel", securityLevel.toString())
  }

  /**
   * Sets the security name to be used with this target.
   */
  public fun securityName(securityName: String) {
    it.property("securityName", securityName)
  }
}
