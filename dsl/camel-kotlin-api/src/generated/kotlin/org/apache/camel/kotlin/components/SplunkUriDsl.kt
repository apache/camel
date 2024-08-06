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
 * Publish or search for events in Splunk.
 */
public fun UriDsl.splunk(i: SplunkUriDsl.() -> Unit) {
  SplunkUriDsl(this).apply(i)
}

@CamelDslMarker
public class SplunkUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("splunk")
  }

  private var name: String = ""

  /**
   * Name has no purpose
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * Splunk app
   */
  public fun app(app: String) {
    it.property("app", app)
  }

  /**
   * Timeout in MS when connecting to Splunk server
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Timeout in MS when connecting to Splunk server
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * Splunk host.
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * Splunk owner
   */
  public fun owner(owner: String) {
    it.property("owner", owner)
  }

  /**
   * Splunk port
   */
  public fun port(port: String) {
    it.property("port", port)
  }

  /**
   * Splunk port
   */
  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  /**
   * Splunk scheme
   */
  public fun scheme(scheme: String) {
    it.property("scheme", scheme)
  }

  /**
   * A number that indicates the maximum number of entities to return.
   */
  public fun count(count: String) {
    it.property("count", count)
  }

  /**
   * A number that indicates the maximum number of entities to return.
   */
  public fun count(count: Int) {
    it.property("count", count.toString())
  }

  /**
   * Earliest time of the search time window.
   */
  public fun earliestTime(earliestTime: String) {
    it.property("earliestTime", earliestTime)
  }

  /**
   * Initial start offset of the first search
   */
  public fun initEarliestTime(initEarliestTime: String) {
    it.property("initEarliestTime", initEarliestTime)
  }

  /**
   * Latest time of the search time window.
   */
  public fun latestTime(latestTime: String) {
    it.property("latestTime", latestTime)
  }

  /**
   * The name of the query saved in Splunk to run
   */
  public fun savedSearch(savedSearch: String) {
    it.property("savedSearch", savedSearch)
  }

  /**
   * The Splunk query to run
   */
  public fun search(search: String) {
    it.property("search", search)
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
   * Sets streaming mode. Streaming mode sends exchanges as they are received, rather than in a
   * batch.
   */
  public fun streaming(streaming: String) {
    it.property("streaming", streaming)
  }

  /**
   * Sets streaming mode. Streaming mode sends exchanges as they are received, rather than in a
   * batch.
   */
  public fun streaming(streaming: Boolean) {
    it.property("streaming", streaming.toString())
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
   * Override the default Splunk event host field
   */
  public fun eventHost(eventHost: String) {
    it.property("eventHost", eventHost)
  }

  /**
   * Splunk index to write to
   */
  public fun index(index: String) {
    it.property("index", index)
  }

  /**
   * Should the payload be inserted raw
   */
  public fun raw(raw: String) {
    it.property("raw", raw)
  }

  /**
   * Should the payload be inserted raw
   */
  public fun raw(raw: Boolean) {
    it.property("raw", raw.toString())
  }

  /**
   * Splunk source argument
   */
  public fun source(source: String) {
    it.property("source", source)
  }

  /**
   * Splunk sourcetype argument
   */
  public fun sourceType(sourceType: String) {
    it.property("sourceType", sourceType)
  }

  /**
   * Splunk tcp receiver port defined locally on splunk server. (For example if splunk port 9997 is
   * mapped to 12345, tcpReceiverLocalPort has to be 9997)
   */
  public fun tcpReceiverLocalPort(tcpReceiverLocalPort: String) {
    it.property("tcpReceiverLocalPort", tcpReceiverLocalPort)
  }

  /**
   * Splunk tcp receiver port defined locally on splunk server. (For example if splunk port 9997 is
   * mapped to 12345, tcpReceiverLocalPort has to be 9997)
   */
  public fun tcpReceiverLocalPort(tcpReceiverLocalPort: Int) {
    it.property("tcpReceiverLocalPort", tcpReceiverLocalPort.toString())
  }

  /**
   * Splunk tcp receiver port
   */
  public fun tcpReceiverPort(tcpReceiverPort: String) {
    it.property("tcpReceiverPort", tcpReceiverPort)
  }

  /**
   * Splunk tcp receiver port
   */
  public fun tcpReceiverPort(tcpReceiverPort: Int) {
    it.property("tcpReceiverPort", tcpReceiverPort.toString())
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
   * Milliseconds before the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
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
   * Password for Splunk
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Set the ssl protocol to use
   */
  public fun sslProtocol(sslProtocol: String) {
    it.property("sslProtocol", sslProtocol)
  }

  /**
   * User's token for Splunk. This takes precedence over password when both are set
   */
  public fun token(token: String) {
    it.property("token", token)
  }

  /**
   * Username for Splunk
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * Use sun.net.www.protocol.https.Handler Https handler to establish the Splunk Connection. Can be
   * useful when running in application servers to avoid app. server https handling.
   */
  public fun useSunHttpsHandler(useSunHttpsHandler: String) {
    it.property("useSunHttpsHandler", useSunHttpsHandler)
  }

  /**
   * Use sun.net.www.protocol.https.Handler Https handler to establish the Splunk Connection. Can be
   * useful when running in application servers to avoid app. server https handling.
   */
  public fun useSunHttpsHandler(useSunHttpsHandler: Boolean) {
    it.property("useSunHttpsHandler", useSunHttpsHandler.toString())
  }

  /**
   * Sets client's certificate validation mode. Value false makes SSL vulnerable and is not
   * recommended for the production environment.
   */
  public fun validateCertificates(validateCertificates: String) {
    it.property("validateCertificates", validateCertificates)
  }

  /**
   * Sets client's certificate validation mode. Value false makes SSL vulnerable and is not
   * recommended for the production environment.
   */
  public fun validateCertificates(validateCertificates: Boolean) {
    it.property("validateCertificates", validateCertificates.toString())
  }
}
