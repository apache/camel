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

public fun UriDsl.stream(i: StreamUriDsl.() -> Unit) {
  StreamUriDsl(this).apply(i)
}

@CamelDslMarker
public class StreamUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("stream")
  }

  private var kind: String = ""

  public fun kind(kind: String) {
    this.kind = kind
    it.url("$kind")
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  public fun fileWatcher(fileWatcher: String) {
    it.property("fileWatcher", fileWatcher)
  }

  public fun fileWatcher(fileWatcher: Boolean) {
    it.property("fileWatcher", fileWatcher.toString())
  }

  public fun groupLines(groupLines: String) {
    it.property("groupLines", groupLines)
  }

  public fun groupLines(groupLines: Int) {
    it.property("groupLines", groupLines.toString())
  }

  public fun groupStrategy(groupStrategy: String) {
    it.property("groupStrategy", groupStrategy)
  }

  public fun httpHeaders(httpHeaders: String) {
    it.property("httpHeaders", httpHeaders)
  }

  public fun httpUrl(httpUrl: String) {
    it.property("httpUrl", httpUrl)
  }

  public fun initialPromptDelay(initialPromptDelay: String) {
    it.property("initialPromptDelay", initialPromptDelay)
  }

  public fun initialPromptDelay(initialPromptDelay: Int) {
    it.property("initialPromptDelay", initialPromptDelay.toString())
  }

  public fun promptDelay(promptDelay: String) {
    it.property("promptDelay", promptDelay)
  }

  public fun promptDelay(promptDelay: Int) {
    it.property("promptDelay", promptDelay.toString())
  }

  public fun promptMessage(promptMessage: String) {
    it.property("promptMessage", promptMessage)
  }

  public fun readLine(readLine: String) {
    it.property("readLine", readLine)
  }

  public fun readLine(readLine: Boolean) {
    it.property("readLine", readLine.toString())
  }

  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  public fun retry(retry: Boolean) {
    it.property("retry", retry.toString())
  }

  public fun scanStream(scanStream: String) {
    it.property("scanStream", scanStream)
  }

  public fun scanStream(scanStream: Boolean) {
    it.property("scanStream", scanStream.toString())
  }

  public fun scanStreamDelay(scanStreamDelay: String) {
    it.property("scanStreamDelay", scanStreamDelay)
  }

  public fun scanStreamDelay(scanStreamDelay: Int) {
    it.property("scanStreamDelay", scanStreamDelay.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun appendNewLine(appendNewLine: String) {
    it.property("appendNewLine", appendNewLine)
  }

  public fun appendNewLine(appendNewLine: Boolean) {
    it.property("appendNewLine", appendNewLine.toString())
  }

  public fun autoCloseCount(autoCloseCount: String) {
    it.property("autoCloseCount", autoCloseCount)
  }

  public fun autoCloseCount(autoCloseCount: Int) {
    it.property("autoCloseCount", autoCloseCount.toString())
  }

  public fun closeOnDone(closeOnDone: String) {
    it.property("closeOnDone", closeOnDone)
  }

  public fun closeOnDone(closeOnDone: Boolean) {
    it.property("closeOnDone", closeOnDone.toString())
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }
}
