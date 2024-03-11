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
 * Get notified about file events in a directory using java.nio.file.WatchService.
 */
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

  /**
   * Path of directory to consume events from.
   */
  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  /**
   * ANT style pattern to match files. The file is matched against path relative to endpoint path.
   * Pattern must be also relative (not starting with slash)
   */
  public fun antInclude(antInclude: String) {
    it.property("antInclude", antInclude)
  }

  /**
   * Auto create directory if does not exist.
   */
  public fun autoCreate(autoCreate: String) {
    it.property("autoCreate", autoCreate)
  }

  /**
   * Auto create directory if does not exist.
   */
  public fun autoCreate(autoCreate: Boolean) {
    it.property("autoCreate", autoCreate.toString())
  }

  /**
   * Comma separated list of events to watch. Possible values: CREATE,MODIFY,DELETE
   */
  public fun events(events: String) {
    it.property("events", events)
  }

  /**
   * Watch recursive in current and child directories (including newly created directories).
   */
  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  /**
   * Watch recursive in current and child directories (including newly created directories).
   */
  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }

  /**
   * Enables or disables file hashing to detect duplicate events. If you disable this, you can get
   * some events multiple times on some platforms and JDKs. Check java.nio.file.WatchService
   * limitations for your target platform.
   */
  public fun useFileHashing(useFileHashing: String) {
    it.property("useFileHashing", useFileHashing)
  }

  /**
   * Enables or disables file hashing to detect duplicate events. If you disable this, you can get
   * some events multiple times on some platforms and JDKs. Check java.nio.file.WatchService
   * limitations for your target platform.
   */
  public fun useFileHashing(useFileHashing: Boolean) {
    it.property("useFileHashing", useFileHashing.toString())
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
   * The number of concurrent consumers. Increase this value, if your route is slow to prevent
   * buffering in queue.
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * The number of concurrent consumers. Increase this value, if your route is slow to prevent
   * buffering in queue.
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Reference to io.methvin.watcher.hashing.FileHasher. This prevents emitting duplicate events on
   * some platforms. For working with large files and if you dont need detect multiple modifications
   * per second per file, use #lastModifiedTimeFileHasher. You can also provide custom implementation
   * in registry.
   */
  public fun fileHasher(fileHasher: String) {
    it.property("fileHasher", fileHasher)
  }

  /**
   * The number of threads polling WatchService. Increase this value, if you see OVERFLOW messages
   * in log.
   */
  public fun pollThreads(pollThreads: String) {
    it.property("pollThreads", pollThreads)
  }

  /**
   * The number of threads polling WatchService. Increase this value, if you see OVERFLOW messages
   * in log.
   */
  public fun pollThreads(pollThreads: Int) {
    it.property("pollThreads", pollThreads.toString())
  }

  /**
   * Maximum size of queue between WatchService and consumer. Unbounded by default.
   */
  public fun queueSize(queueSize: String) {
    it.property("queueSize", queueSize)
  }

  /**
   * Maximum size of queue between WatchService and consumer. Unbounded by default.
   */
  public fun queueSize(queueSize: Int) {
    it.property("queueSize", queueSize.toString())
  }
}
