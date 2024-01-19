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

  public fun loggerName(loggerName: String) {
    this.loggerName = loggerName
    it.url("$loggerName")
  }

  public fun groupActiveOnly(groupActiveOnly: String) {
    it.property("groupActiveOnly", groupActiveOnly)
  }

  public fun groupActiveOnly(groupActiveOnly: Boolean) {
    it.property("groupActiveOnly", groupActiveOnly.toString())
  }

  public fun groupDelay(groupDelay: String) {
    it.property("groupDelay", groupDelay)
  }

  public fun groupDelay(groupDelay: Int) {
    it.property("groupDelay", groupDelay.toString())
  }

  public fun groupInterval(groupInterval: String) {
    it.property("groupInterval", groupInterval)
  }

  public fun groupInterval(groupInterval: Int) {
    it.property("groupInterval", groupInterval.toString())
  }

  public fun groupSize(groupSize: String) {
    it.property("groupSize", groupSize)
  }

  public fun groupSize(groupSize: Int) {
    it.property("groupSize", groupSize.toString())
  }

  public fun level(level: String) {
    it.property("level", level)
  }

  public fun logMask(logMask: String) {
    it.property("logMask", logMask)
  }

  public fun logMask(logMask: Boolean) {
    it.property("logMask", logMask.toString())
  }

  public fun marker(marker: String) {
    it.property("marker", marker)
  }

  public fun plain(plain: String) {
    it.property("plain", plain)
  }

  public fun plain(plain: Boolean) {
    it.property("plain", plain.toString())
  }

  public fun sourceLocationLoggerName(sourceLocationLoggerName: String) {
    it.property("sourceLocationLoggerName", sourceLocationLoggerName)
  }

  public fun sourceLocationLoggerName(sourceLocationLoggerName: Boolean) {
    it.property("sourceLocationLoggerName", sourceLocationLoggerName.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun exchangeFormatter(exchangeFormatter: String) {
    it.property("exchangeFormatter", exchangeFormatter)
  }

  public fun maxChars(maxChars: String) {
    it.property("maxChars", maxChars)
  }

  public fun maxChars(maxChars: Int) {
    it.property("maxChars", maxChars.toString())
  }

  public fun multiline(multiline: String) {
    it.property("multiline", multiline)
  }

  public fun multiline(multiline: Boolean) {
    it.property("multiline", multiline.toString())
  }

  public fun showAll(showAll: String) {
    it.property("showAll", showAll)
  }

  public fun showAll(showAll: Boolean) {
    it.property("showAll", showAll.toString())
  }

  public fun showAllProperties(showAllProperties: String) {
    it.property("showAllProperties", showAllProperties)
  }

  public fun showAllProperties(showAllProperties: Boolean) {
    it.property("showAllProperties", showAllProperties.toString())
  }

  public fun showBody(showBody: String) {
    it.property("showBody", showBody)
  }

  public fun showBody(showBody: Boolean) {
    it.property("showBody", showBody.toString())
  }

  public fun showBodyType(showBodyType: String) {
    it.property("showBodyType", showBodyType)
  }

  public fun showBodyType(showBodyType: Boolean) {
    it.property("showBodyType", showBodyType.toString())
  }

  public fun showCachedStreams(showCachedStreams: String) {
    it.property("showCachedStreams", showCachedStreams)
  }

  public fun showCachedStreams(showCachedStreams: Boolean) {
    it.property("showCachedStreams", showCachedStreams.toString())
  }

  public fun showCaughtException(showCaughtException: String) {
    it.property("showCaughtException", showCaughtException)
  }

  public fun showCaughtException(showCaughtException: Boolean) {
    it.property("showCaughtException", showCaughtException.toString())
  }

  public fun showException(showException: String) {
    it.property("showException", showException)
  }

  public fun showException(showException: Boolean) {
    it.property("showException", showException.toString())
  }

  public fun showExchangeId(showExchangeId: String) {
    it.property("showExchangeId", showExchangeId)
  }

  public fun showExchangeId(showExchangeId: Boolean) {
    it.property("showExchangeId", showExchangeId.toString())
  }

  public fun showExchangePattern(showExchangePattern: String) {
    it.property("showExchangePattern", showExchangePattern)
  }

  public fun showExchangePattern(showExchangePattern: Boolean) {
    it.property("showExchangePattern", showExchangePattern.toString())
  }

  public fun showFiles(showFiles: String) {
    it.property("showFiles", showFiles)
  }

  public fun showFiles(showFiles: Boolean) {
    it.property("showFiles", showFiles.toString())
  }

  public fun showFuture(showFuture: String) {
    it.property("showFuture", showFuture)
  }

  public fun showFuture(showFuture: Boolean) {
    it.property("showFuture", showFuture.toString())
  }

  public fun showHeaders(showHeaders: String) {
    it.property("showHeaders", showHeaders)
  }

  public fun showHeaders(showHeaders: Boolean) {
    it.property("showHeaders", showHeaders.toString())
  }

  public fun showProperties(showProperties: String) {
    it.property("showProperties", showProperties)
  }

  public fun showProperties(showProperties: Boolean) {
    it.property("showProperties", showProperties.toString())
  }

  public fun showRouteGroup(showRouteGroup: String) {
    it.property("showRouteGroup", showRouteGroup)
  }

  public fun showRouteGroup(showRouteGroup: Boolean) {
    it.property("showRouteGroup", showRouteGroup.toString())
  }

  public fun showRouteId(showRouteId: String) {
    it.property("showRouteId", showRouteId)
  }

  public fun showRouteId(showRouteId: Boolean) {
    it.property("showRouteId", showRouteId.toString())
  }

  public fun showStackTrace(showStackTrace: String) {
    it.property("showStackTrace", showStackTrace)
  }

  public fun showStackTrace(showStackTrace: Boolean) {
    it.property("showStackTrace", showStackTrace.toString())
  }

  public fun showStreams(showStreams: String) {
    it.property("showStreams", showStreams)
  }

  public fun showStreams(showStreams: Boolean) {
    it.property("showStreams", showStreams.toString())
  }

  public fun showVariables(showVariables: String) {
    it.property("showVariables", showVariables)
  }

  public fun showVariables(showVariables: Boolean) {
    it.property("showVariables", showVariables.toString())
  }

  public fun skipBodyLineSeparator(skipBodyLineSeparator: String) {
    it.property("skipBodyLineSeparator", skipBodyLineSeparator)
  }

  public fun skipBodyLineSeparator(skipBodyLineSeparator: Boolean) {
    it.property("skipBodyLineSeparator", skipBodyLineSeparator.toString())
  }

  public fun style(style: String) {
    it.property("style", style)
  }
}
