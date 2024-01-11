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

public fun UriDsl.mina(i: MinaUriDsl.() -> Unit) {
  MinaUriDsl(this).apply(i)
}

@CamelDslMarker
public class MinaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("mina")
  }

  private var protocol: String = ""

  private var host: String = ""

  private var port: String = ""

  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol:$host:$port")
  }

  public fun host(host: String) {
    this.host = host
    it.url("$protocol:$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$protocol:$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol:$host:$port")
  }

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun minaLogger(minaLogger: String) {
    it.property("minaLogger", minaLogger)
  }

  public fun minaLogger(minaLogger: Boolean) {
    it.property("minaLogger", minaLogger.toString())
  }

  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun writeTimeout(writeTimeout: String) {
    it.property("writeTimeout", writeTimeout)
  }

  public fun writeTimeout(writeTimeout: Int) {
    it.property("writeTimeout", writeTimeout.toString())
  }

  public fun clientMode(clientMode: String) {
    it.property("clientMode", clientMode)
  }

  public fun clientMode(clientMode: Boolean) {
    it.property("clientMode", clientMode.toString())
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

  public fun noReplyLogLevel(noReplyLogLevel: String) {
    it.property("noReplyLogLevel", noReplyLogLevel)
  }

  public fun cachedAddress(cachedAddress: String) {
    it.property("cachedAddress", cachedAddress)
  }

  public fun cachedAddress(cachedAddress: Boolean) {
    it.property("cachedAddress", cachedAddress.toString())
  }

  public fun lazySessionCreation(lazySessionCreation: String) {
    it.property("lazySessionCreation", lazySessionCreation)
  }

  public fun lazySessionCreation(lazySessionCreation: Boolean) {
    it.property("lazySessionCreation", lazySessionCreation.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun disconnectOnNoReply(disconnectOnNoReply: String) {
    it.property("disconnectOnNoReply", disconnectOnNoReply)
  }

  public fun disconnectOnNoReply(disconnectOnNoReply: Boolean) {
    it.property("disconnectOnNoReply", disconnectOnNoReply.toString())
  }

  public fun maximumPoolSize(maximumPoolSize: String) {
    it.property("maximumPoolSize", maximumPoolSize)
  }

  public fun maximumPoolSize(maximumPoolSize: Int) {
    it.property("maximumPoolSize", maximumPoolSize.toString())
  }

  public fun orderedThreadPoolExecutor(orderedThreadPoolExecutor: String) {
    it.property("orderedThreadPoolExecutor", orderedThreadPoolExecutor)
  }

  public fun orderedThreadPoolExecutor(orderedThreadPoolExecutor: Boolean) {
    it.property("orderedThreadPoolExecutor", orderedThreadPoolExecutor.toString())
  }

  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  public fun allowDefaultCodec(allowDefaultCodec: String) {
    it.property("allowDefaultCodec", allowDefaultCodec)
  }

  public fun allowDefaultCodec(allowDefaultCodec: Boolean) {
    it.property("allowDefaultCodec", allowDefaultCodec.toString())
  }

  public fun codec(codec: String) {
    it.property("codec", codec)
  }

  public fun decoderMaxLineLength(decoderMaxLineLength: String) {
    it.property("decoderMaxLineLength", decoderMaxLineLength)
  }

  public fun decoderMaxLineLength(decoderMaxLineLength: Int) {
    it.property("decoderMaxLineLength", decoderMaxLineLength.toString())
  }

  public fun encoderMaxLineLength(encoderMaxLineLength: String) {
    it.property("encoderMaxLineLength", encoderMaxLineLength)
  }

  public fun encoderMaxLineLength(encoderMaxLineLength: Int) {
    it.property("encoderMaxLineLength", encoderMaxLineLength.toString())
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun filters(filters: String) {
    it.property("filters", filters)
  }

  public fun textline(textline: String) {
    it.property("textline", textline)
  }

  public fun textline(textline: Boolean) {
    it.property("textline", textline.toString())
  }

  public fun textlineDelimiter(textlineDelimiter: String) {
    it.property("textlineDelimiter", textlineDelimiter)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
