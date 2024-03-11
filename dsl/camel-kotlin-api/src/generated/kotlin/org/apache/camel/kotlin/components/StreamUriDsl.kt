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
 * Read from system-in and write to system-out and system-err streams.
 */
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

  /**
   * Kind of stream to use such as System.in, System.out, a file, or a http url.
   */
  public fun kind(kind: String) {
    this.kind = kind
    it.url("$kind")
  }

  /**
   * You can configure the encoding (is a charset name) to use text-based streams (for example,
   * message body is a String object). If not provided, Camel uses the JVM default Charset.
   */
  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  /**
   * When using the stream:file URI format, this option specifies the filename to stream to/from.
   */
  public fun fileName(fileName: String) {
    it.property("fileName", fileName)
  }

  /**
   * To use JVM file watcher to listen for file change events to support re-loading files that may
   * be overwritten, somewhat like tail --retry
   */
  public fun fileWatcher(fileWatcher: String) {
    it.property("fileWatcher", fileWatcher)
  }

  /**
   * To use JVM file watcher to listen for file change events to support re-loading files that may
   * be overwritten, somewhat like tail --retry
   */
  public fun fileWatcher(fileWatcher: Boolean) {
    it.property("fileWatcher", fileWatcher.toString())
  }

  /**
   * To group X number of lines in the consumer. For example to group 10 lines and therefore only
   * spit out an Exchange with 10 lines, instead of 1 Exchange per line.
   */
  public fun groupLines(groupLines: String) {
    it.property("groupLines", groupLines)
  }

  /**
   * To group X number of lines in the consumer. For example to group 10 lines and therefore only
   * spit out an Exchange with 10 lines, instead of 1 Exchange per line.
   */
  public fun groupLines(groupLines: Int) {
    it.property("groupLines", groupLines.toString())
  }

  /**
   * Allows to use a custom GroupStrategy to control how to group lines.
   */
  public fun groupStrategy(groupStrategy: String) {
    it.property("groupStrategy", groupStrategy)
  }

  /**
   * When using stream:http format, this option specifies optional http headers, such as Accept:
   * application/json. Multiple headers can be separated by comma. The format of headers can be either
   * HEADER=VALUE or HEADER:VALUE. In accordance with the HTTP/1.1 specification, leading and/or
   * trailing whitespace is ignored
   */
  public fun httpHeaders(httpHeaders: String) {
    it.property("httpHeaders", httpHeaders)
  }

  /**
   * When using stream:http format, this option specifies the http url to stream from.
   */
  public fun httpUrl(httpUrl: String) {
    it.property("httpUrl", httpUrl)
  }

  /**
   * Initial delay in milliseconds before showing the message prompt. This delay occurs only once.
   * Can be used during system startup to avoid message prompts being written while other logging is
   * done to the system out.
   */
  public fun initialPromptDelay(initialPromptDelay: String) {
    it.property("initialPromptDelay", initialPromptDelay)
  }

  /**
   * Initial delay in milliseconds before showing the message prompt. This delay occurs only once.
   * Can be used during system startup to avoid message prompts being written while other logging is
   * done to the system out.
   */
  public fun initialPromptDelay(initialPromptDelay: Int) {
    it.property("initialPromptDelay", initialPromptDelay.toString())
  }

  /**
   * Optional delay in milliseconds before showing the message prompt.
   */
  public fun promptDelay(promptDelay: String) {
    it.property("promptDelay", promptDelay)
  }

  /**
   * Optional delay in milliseconds before showing the message prompt.
   */
  public fun promptDelay(promptDelay: Int) {
    it.property("promptDelay", promptDelay.toString())
  }

  /**
   * Message prompt to use when reading from stream:in; for example, you could set this to Enter a
   * command:
   */
  public fun promptMessage(promptMessage: String) {
    it.property("promptMessage", promptMessage)
  }

  /**
   * Whether to read the input stream in line mode (terminate by line breaks). Setting this to
   * false, will instead read the entire stream until EOL.
   */
  public fun readLine(readLine: String) {
    it.property("readLine", readLine)
  }

  /**
   * Whether to read the input stream in line mode (terminate by line breaks). Setting this to
   * false, will instead read the entire stream until EOL.
   */
  public fun readLine(readLine: Boolean) {
    it.property("readLine", readLine.toString())
  }

  /**
   * Will retry opening the stream if it's overwritten, somewhat like tail --retry If reading from
   * files then you should also enable the fileWatcher option, to make it work reliable.
   */
  public fun retry(retry: String) {
    it.property("retry", retry)
  }

  /**
   * Will retry opening the stream if it's overwritten, somewhat like tail --retry If reading from
   * files then you should also enable the fileWatcher option, to make it work reliable.
   */
  public fun retry(retry: Boolean) {
    it.property("retry", retry.toString())
  }

  /**
   * To be used for continuously reading a stream such as the unix tail command.
   */
  public fun scanStream(scanStream: String) {
    it.property("scanStream", scanStream)
  }

  /**
   * To be used for continuously reading a stream such as the unix tail command.
   */
  public fun scanStream(scanStream: Boolean) {
    it.property("scanStream", scanStream.toString())
  }

  /**
   * Delay in milliseconds between read attempts when using scanStream.
   */
  public fun scanStreamDelay(scanStreamDelay: String) {
    it.property("scanStreamDelay", scanStreamDelay)
  }

  /**
   * Delay in milliseconds between read attempts when using scanStream.
   */
  public fun scanStreamDelay(scanStreamDelay: Int) {
    it.property("scanStreamDelay", scanStreamDelay.toString())
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
   * Whether to append a new line character at end of output.
   */
  public fun appendNewLine(appendNewLine: String) {
    it.property("appendNewLine", appendNewLine)
  }

  /**
   * Whether to append a new line character at end of output.
   */
  public fun appendNewLine(appendNewLine: Boolean) {
    it.property("appendNewLine", appendNewLine.toString())
  }

  /**
   * Number of messages to process before closing stream on Producer side. Never close stream by
   * default (only when Producer is stopped). If more messages are sent, the stream is reopened for
   * another autoCloseCount batch.
   */
  public fun autoCloseCount(autoCloseCount: String) {
    it.property("autoCloseCount", autoCloseCount)
  }

  /**
   * Number of messages to process before closing stream on Producer side. Never close stream by
   * default (only when Producer is stopped). If more messages are sent, the stream is reopened for
   * another autoCloseCount batch.
   */
  public fun autoCloseCount(autoCloseCount: Int) {
    it.property("autoCloseCount", autoCloseCount.toString())
  }

  /**
   * This option is used in combination with Splitter and streaming to the same file. The idea is to
   * keep the stream open and only close when the Splitter is done, to improve performance. Mind this
   * requires that you only stream to the same file, and not 2 or more files.
   */
  public fun closeOnDone(closeOnDone: String) {
    it.property("closeOnDone", closeOnDone)
  }

  /**
   * This option is used in combination with Splitter and streaming to the same file. The idea is to
   * keep the stream open and only close when the Splitter is done, to improve performance. Mind this
   * requires that you only stream to the same file, and not 2 or more files.
   */
  public fun closeOnDone(closeOnDone: Boolean) {
    it.property("closeOnDone", closeOnDone.toString())
  }

  /**
   * Initial delay in milliseconds before producing the stream.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * Initial delay in milliseconds before producing the stream.
   */
  public fun delay(delay: Int) {
    it.property("delay", delay.toString())
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
   * Sets the read timeout to a specified timeout, in milliseconds. A non-zero value specifies the
   * timeout when reading from Input stream when a connection is established to a resource. If the
   * timeout expires before there is data available for read, a java.net.SocketTimeoutException is
   * raised. A timeout of zero is interpreted as an infinite timeout.
   */
  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  /**
   * Sets the read timeout to a specified timeout, in milliseconds. A non-zero value specifies the
   * timeout when reading from Input stream when a connection is established to a resource. If the
   * timeout expires before there is data available for read, a java.net.SocketTimeoutException is
   * raised. A timeout of zero is interpreted as an infinite timeout.
   */
  public fun readTimeout(readTimeout: Int) {
    it.property("readTimeout", readTimeout.toString())
  }
}
