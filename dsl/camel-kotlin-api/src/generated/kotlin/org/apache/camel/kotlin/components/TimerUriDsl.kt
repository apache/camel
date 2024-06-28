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
 * Generate messages in specified intervals using java.util.Timer.
 */
public fun UriDsl.timer(i: TimerUriDsl.() -> Unit) {
  TimerUriDsl(this).apply(i)
}

@CamelDslMarker
public class TimerUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("timer")
  }

  private var timerName: String = ""

  /**
   * The name of the timer
   */
  public fun timerName(timerName: String) {
    this.timerName = timerName
    it.url("$timerName")
  }

  /**
   * The number of milliseconds to wait before the first event is generated. Should not be used in
   * conjunction with the time option. The default value is 1000.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Events take place at approximately regular intervals, separated by the specified period.
   */
  public fun fixedRate(fixedRate: String) {
    it.property("fixedRate", fixedRate)
  }

  /**
   * Events take place at approximately regular intervals, separated by the specified period.
   */
  public fun fixedRate(fixedRate: Boolean) {
    it.property("fixedRate", fixedRate.toString())
  }

  /**
   * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
   */
  public fun includeMetadata(includeMetadata: String) {
    it.property("includeMetadata", includeMetadata)
  }

  /**
   * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
   */
  public fun includeMetadata(includeMetadata: Boolean) {
    it.property("includeMetadata", includeMetadata.toString())
  }

  /**
   * Generate periodic events every period. Must be zero or positive value. The default value is
   * 1000.
   */
  public fun period(period: String) {
    it.property("period", period)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the timer will only fire
   * once. If you set it to 5, it will only fire five times. A value of zero or negative means fire
   * forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the timer will only fire
   * once. If you set it to 5, it will only fire five times. A value of zero or negative means fire
   * forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
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
   * Specifies whether or not the thread associated with the timer endpoint runs as a daemon. The
   * default value is true.
   */
  public fun daemon(daemon: String) {
    it.property("daemon", daemon)
  }

  /**
   * Specifies whether or not the thread associated with the timer endpoint runs as a daemon. The
   * default value is true.
   */
  public fun daemon(daemon: Boolean) {
    it.property("daemon", daemon.toString())
  }

  /**
   * Allows you to specify a custom Date pattern to use for setting the time option using URI
   * syntax.
   */
  public fun pattern(pattern: String) {
    it.property("pattern", pattern)
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
   * A java.util.Date the first event should be generated. If using the URI, the pattern expected
   * is: yyyy-MM-dd HH:mm:ss or yyyy-MM-dd'T'HH:mm:ss.
   */
  public fun time(time: String) {
    it.property("time", time)
  }

  /**
   * To use a custom Timer
   */
  public fun timer(timer: String) {
    it.property("timer", timer)
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }
}
