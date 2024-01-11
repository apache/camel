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

public fun UriDsl.`file-watch`(i: FileWatchUriDsl.() -> Unit) {
  FileWatchUriDsl(this).apply(i)
}

@CamelDslMarker
public class FileWatchUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("file-watch")
  }

  private var path: String = ""

  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  public fun antInclude(antInclude: String) {
    it.property("antInclude", antInclude)
  }

  public fun autoCreate(autoCreate: String) {
    it.property("autoCreate", autoCreate)
  }

  public fun autoCreate(autoCreate: Boolean) {
    it.property("autoCreate", autoCreate.toString())
  }

  public fun events(events: String) {
    it.property("events", events)
  }

  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  public fun useFileHashing(useFileHashing: String) {
    it.property("useFileHashing", useFileHashing)
  }

  public fun useFileHashing(useFileHashing: Boolean) {
    it.property("useFileHashing", useFileHashing.toString())
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

  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  public fun fileHasher(fileHasher: String) {
    it.property("fileHasher", fileHasher)
  }

  public fun pollThreads(pollThreads: String) {
    it.property("pollThreads", pollThreads)
  }

  public fun pollThreads(pollThreads: Int) {
    it.property("pollThreads", pollThreads.toString())
  }

  public fun queueSize(queueSize: String) {
    it.property("queueSize", queueSize)
  }

  public fun queueSize(queueSize: Int) {
    it.property("queueSize", queueSize.toString())
  }
}
