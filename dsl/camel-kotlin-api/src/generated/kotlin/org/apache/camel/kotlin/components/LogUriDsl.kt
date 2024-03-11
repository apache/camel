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
 * Log messages to the underlying logging mechanism.
 */
public fun UriDsl.log(i: LogUriDsl.() -> Unit) {
  LogUriDsl(this).apply(i)
}

@CamelDslMarker
public class LogUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("log")
  }

  private var loggerName: String = ""

  /**
   * Name of the logging category to use
   */
  public fun loggerName(loggerName: String) {
    this.loggerName = loggerName
    it.url("$loggerName")
  }

  /**
   * If true, will hide stats when no new messages have been received for a time interval, if false,
   * show stats regardless of message traffic.
   */
  public fun groupActiveOnly(groupActiveOnly: String) {
    it.property("groupActiveOnly", groupActiveOnly)
  }

  /**
   * If true, will hide stats when no new messages have been received for a time interval, if false,
   * show stats regardless of message traffic.
   */
  public fun groupActiveOnly(groupActiveOnly: Boolean) {
    it.property("groupActiveOnly", groupActiveOnly.toString())
  }

  /**
   * Set the initial delay for stats (in millis)
   */
  public fun groupDelay(groupDelay: String) {
    it.property("groupDelay", groupDelay)
  }

  /**
   * Set the initial delay for stats (in millis)
   */
  public fun groupDelay(groupDelay: Int) {
    it.property("groupDelay", groupDelay.toString())
  }

  /**
   * If specified will group message stats by this time interval (in millis)
   */
  public fun groupInterval(groupInterval: String) {
    it.property("groupInterval", groupInterval)
  }

  /**
   * If specified will group message stats by this time interval (in millis)
   */
  public fun groupInterval(groupInterval: Int) {
    it.property("groupInterval", groupInterval.toString())
  }

  /**
   * An integer that specifies a group size for throughput logging.
   */
  public fun groupSize(groupSize: String) {
    it.property("groupSize", groupSize)
  }

  /**
   * An integer that specifies a group size for throughput logging.
   */
  public fun groupSize(groupSize: Int) {
    it.property("groupSize", groupSize.toString())
  }

  /**
   * Logging level to use. The default value is INFO.
   */
  public fun level(level: String) {
    it.property("level", level)
  }

  /**
   * If true, mask sensitive information like password or passphrase in the log.
   */
  public fun logMask(logMask: String) {
    it.property("logMask", logMask)
  }

  /**
   * If true, mask sensitive information like password or passphrase in the log.
   */
  public fun logMask(logMask: Boolean) {
    it.property("logMask", logMask.toString())
  }

  /**
   * An optional Marker name to use.
   */
  public fun marker(marker: String) {
    it.property("marker", marker)
  }

  /**
   * If enabled only the body will be printed out
   */
  public fun plain(plain: String) {
    it.property("plain", plain)
  }

  /**
   * If enabled only the body will be printed out
   */
  public fun plain(plain: Boolean) {
    it.property("plain", plain.toString())
  }

  /**
   * If enabled then the source location of where the log endpoint is used in Camel routes, would be
   * used as logger name, instead of the given name. However, if the source location is disabled or not
   * possible to resolve then the existing logger name will be used.
   */
  public fun sourceLocationLoggerName(sourceLocationLoggerName: String) {
    it.property("sourceLocationLoggerName", sourceLocationLoggerName)
  }

  /**
   * If enabled then the source location of where the log endpoint is used in Camel routes, would be
   * used as logger name, instead of the given name. However, if the source location is disabled or not
   * possible to resolve then the existing logger name will be used.
   */
  public fun sourceLocationLoggerName(sourceLocationLoggerName: Boolean) {
    it.property("sourceLocationLoggerName", sourceLocationLoggerName.toString())
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
   * To use a custom exchange formatter
   */
  public fun exchangeFormatter(exchangeFormatter: String) {
    it.property("exchangeFormatter", exchangeFormatter)
  }

  /**
   * Limits the number of characters logged per line.
   */
  public fun maxChars(maxChars: String) {
    it.property("maxChars", maxChars)
  }

  /**
   * Limits the number of characters logged per line.
   */
  public fun maxChars(maxChars: Int) {
    it.property("maxChars", maxChars.toString())
  }

  /**
   * If enabled then each information is outputted on a newline.
   */
  public fun multiline(multiline: String) {
    it.property("multiline", multiline)
  }

  /**
   * If enabled then each information is outputted on a newline.
   */
  public fun multiline(multiline: Boolean) {
    it.property("multiline", multiline.toString())
  }

  /**
   * Quick option for turning all options on. (multiline, maxChars has to be manually set if to be
   * used)
   */
  public fun showAll(showAll: String) {
    it.property("showAll", showAll)
  }

  /**
   * Quick option for turning all options on. (multiline, maxChars has to be manually set if to be
   * used)
   */
  public fun showAll(showAll: Boolean) {
    it.property("showAll", showAll.toString())
  }

  /**
   * Show all of the exchange properties (both internal and custom).
   */
  public fun showAllProperties(showAllProperties: String) {
    it.property("showAllProperties", showAllProperties)
  }

  /**
   * Show all of the exchange properties (both internal and custom).
   */
  public fun showAllProperties(showAllProperties: Boolean) {
    it.property("showAllProperties", showAllProperties.toString())
  }

  /**
   * Show the message body.
   */
  public fun showBody(showBody: String) {
    it.property("showBody", showBody)
  }

  /**
   * Show the message body.
   */
  public fun showBody(showBody: Boolean) {
    it.property("showBody", showBody.toString())
  }

  /**
   * Show the body Java type.
   */
  public fun showBodyType(showBodyType: String) {
    it.property("showBodyType", showBodyType)
  }

  /**
   * Show the body Java type.
   */
  public fun showBodyType(showBodyType: Boolean) {
    it.property("showBodyType", showBodyType.toString())
  }

  /**
   * Whether Camel should show cached stream bodies or not (org.apache.camel.StreamCache).
   */
  public fun showCachedStreams(showCachedStreams: String) {
    it.property("showCachedStreams", showCachedStreams)
  }

  /**
   * Whether Camel should show cached stream bodies or not (org.apache.camel.StreamCache).
   */
  public fun showCachedStreams(showCachedStreams: Boolean) {
    it.property("showCachedStreams", showCachedStreams.toString())
  }

  /**
   * If the exchange has a caught exception, show the exception message (no stack trace). A caught
   * exception is stored as a property on the exchange (using the key
   * org.apache.camel.Exchange#EXCEPTION_CAUGHT) and for instance a doCatch can catch exceptions.
   */
  public fun showCaughtException(showCaughtException: String) {
    it.property("showCaughtException", showCaughtException)
  }

  /**
   * If the exchange has a caught exception, show the exception message (no stack trace). A caught
   * exception is stored as a property on the exchange (using the key
   * org.apache.camel.Exchange#EXCEPTION_CAUGHT) and for instance a doCatch can catch exceptions.
   */
  public fun showCaughtException(showCaughtException: Boolean) {
    it.property("showCaughtException", showCaughtException.toString())
  }

  /**
   * If the exchange has an exception, show the exception message (no stacktrace)
   */
  public fun showException(showException: String) {
    it.property("showException", showException)
  }

  /**
   * If the exchange has an exception, show the exception message (no stacktrace)
   */
  public fun showException(showException: Boolean) {
    it.property("showException", showException.toString())
  }

  /**
   * Show the unique exchange ID.
   */
  public fun showExchangeId(showExchangeId: String) {
    it.property("showExchangeId", showExchangeId)
  }

  /**
   * Show the unique exchange ID.
   */
  public fun showExchangeId(showExchangeId: Boolean) {
    it.property("showExchangeId", showExchangeId.toString())
  }

  /**
   * Shows the Message Exchange Pattern (or MEP for short).
   */
  public fun showExchangePattern(showExchangePattern: String) {
    it.property("showExchangePattern", showExchangePattern)
  }

  /**
   * Shows the Message Exchange Pattern (or MEP for short).
   */
  public fun showExchangePattern(showExchangePattern: Boolean) {
    it.property("showExchangePattern", showExchangePattern.toString())
  }

  /**
   * If enabled Camel will output files
   */
  public fun showFiles(showFiles: String) {
    it.property("showFiles", showFiles)
  }

  /**
   * If enabled Camel will output files
   */
  public fun showFiles(showFiles: Boolean) {
    it.property("showFiles", showFiles.toString())
  }

  /**
   * If enabled Camel will on Future objects wait for it to complete to obtain the payload to be
   * logged.
   */
  public fun showFuture(showFuture: String) {
    it.property("showFuture", showFuture)
  }

  /**
   * If enabled Camel will on Future objects wait for it to complete to obtain the payload to be
   * logged.
   */
  public fun showFuture(showFuture: Boolean) {
    it.property("showFuture", showFuture.toString())
  }

  /**
   * Show the message headers.
   */
  public fun showHeaders(showHeaders: String) {
    it.property("showHeaders", showHeaders)
  }

  /**
   * Show the message headers.
   */
  public fun showHeaders(showHeaders: Boolean) {
    it.property("showHeaders", showHeaders.toString())
  }

  /**
   * Show the exchange properties (only custom). Use showAllProperties to show both internal and
   * custom properties.
   */
  public fun showProperties(showProperties: String) {
    it.property("showProperties", showProperties)
  }

  /**
   * Show the exchange properties (only custom). Use showAllProperties to show both internal and
   * custom properties.
   */
  public fun showProperties(showProperties: Boolean) {
    it.property("showProperties", showProperties.toString())
  }

  /**
   * Show route Group.
   */
  public fun showRouteGroup(showRouteGroup: String) {
    it.property("showRouteGroup", showRouteGroup)
  }

  /**
   * Show route Group.
   */
  public fun showRouteGroup(showRouteGroup: Boolean) {
    it.property("showRouteGroup", showRouteGroup.toString())
  }

  /**
   * Show route ID.
   */
  public fun showRouteId(showRouteId: String) {
    it.property("showRouteId", showRouteId)
  }

  /**
   * Show route ID.
   */
  public fun showRouteId(showRouteId: Boolean) {
    it.property("showRouteId", showRouteId.toString())
  }

  /**
   * Show the stack trace, if an exchange has an exception. Only effective if one of showAll,
   * showException or showCaughtException are enabled.
   */
  public fun showStackTrace(showStackTrace: String) {
    it.property("showStackTrace", showStackTrace)
  }

  /**
   * Show the stack trace, if an exchange has an exception. Only effective if one of showAll,
   * showException or showCaughtException are enabled.
   */
  public fun showStackTrace(showStackTrace: Boolean) {
    it.property("showStackTrace", showStackTrace.toString())
  }

  /**
   * Whether Camel should show stream bodies or not (eg such as java.io.InputStream). Beware if you
   * enable this option then you may not be able later to access the message body as the stream have
   * already been read by this logger. To remedy this you will have to use Stream Caching.
   */
  public fun showStreams(showStreams: String) {
    it.property("showStreams", showStreams)
  }

  /**
   * Whether Camel should show stream bodies or not (eg such as java.io.InputStream). Beware if you
   * enable this option then you may not be able later to access the message body as the stream have
   * already been read by this logger. To remedy this you will have to use Stream Caching.
   */
  public fun showStreams(showStreams: Boolean) {
    it.property("showStreams", showStreams.toString())
  }

  /**
   * Show the variables.
   */
  public fun showVariables(showVariables: String) {
    it.property("showVariables", showVariables)
  }

  /**
   * Show the variables.
   */
  public fun showVariables(showVariables: Boolean) {
    it.property("showVariables", showVariables.toString())
  }

  /**
   * Whether to skip line separators when logging the message body. This allows to log the message
   * body in one line, setting this option to false will preserve any line separators from the body,
   * which then will log the body as is.
   */
  public fun skipBodyLineSeparator(skipBodyLineSeparator: String) {
    it.property("skipBodyLineSeparator", skipBodyLineSeparator)
  }

  /**
   * Whether to skip line separators when logging the message body. This allows to log the message
   * body in one line, setting this option to false will preserve any line separators from the body,
   * which then will log the body as is.
   */
  public fun skipBodyLineSeparator(skipBodyLineSeparator: Boolean) {
    it.property("skipBodyLineSeparator", skipBodyLineSeparator.toString())
  }

  /**
   * Sets the outputs style to use.
   */
  public fun style(style: String) {
    it.property("style", style)
  }
}
