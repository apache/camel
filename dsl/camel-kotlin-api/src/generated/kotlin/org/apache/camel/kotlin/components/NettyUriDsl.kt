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

public fun UriDsl.netty(i: NettyUriDsl.() -> Unit) {
  NettyUriDsl(this).apply(i)
}

@CamelDslMarker
public class NettyUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("netty")
  }

  private var protocol: String = ""

  private var host: String = ""

  private var port: String = ""

  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol://$host:$port")
  }

  public fun host(host: String) {
    this.host = host
    it.url("$protocol://$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$protocol://$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol://$host:$port")
  }

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun keepAlive(keepAlive: String) {
    it.property("keepAlive", keepAlive)
  }

  public fun keepAlive(keepAlive: Boolean) {
    it.property("keepAlive", keepAlive.toString())
  }

  public fun reuseAddress(reuseAddress: String) {
    it.property("reuseAddress", reuseAddress)
  }

  public fun reuseAddress(reuseAddress: Boolean) {
    it.property("reuseAddress", reuseAddress.toString())
  }

  public fun reuseChannel(reuseChannel: String) {
    it.property("reuseChannel", reuseChannel)
  }

  public fun reuseChannel(reuseChannel: Boolean) {
    it.property("reuseChannel", reuseChannel.toString())
  }

  public fun sync(sync: String) {
    it.property("sync", sync)
  }

  public fun sync(sync: Boolean) {
    it.property("sync", sync.toString())
  }

  public fun tcpNoDelay(tcpNoDelay: String) {
    it.property("tcpNoDelay", tcpNoDelay)
  }

  public fun tcpNoDelay(tcpNoDelay: Boolean) {
    it.property("tcpNoDelay", tcpNoDelay.toString())
  }

  public fun broadcast(broadcast: String) {
    it.property("broadcast", broadcast)
  }

  public fun broadcast(broadcast: Boolean) {
    it.property("broadcast", broadcast.toString())
  }

  public fun clientMode(clientMode: String) {
    it.property("clientMode", clientMode)
  }

  public fun clientMode(clientMode: Boolean) {
    it.property("clientMode", clientMode.toString())
  }

  public fun reconnect(reconnect: String) {
    it.property("reconnect", reconnect)
  }

  public fun reconnect(reconnect: Boolean) {
    it.property("reconnect", reconnect.toString())
  }

  public fun reconnectInterval(reconnectInterval: String) {
    it.property("reconnectInterval", reconnectInterval)
  }

  public fun reconnectInterval(reconnectInterval: Int) {
    it.property("reconnectInterval", reconnectInterval.toString())
  }

  public fun backlog(backlog: String) {
    it.property("backlog", backlog)
  }

  public fun backlog(backlog: Int) {
    it.property("backlog", backlog.toString())
  }

  public fun bossCount(bossCount: String) {
    it.property("bossCount", bossCount)
  }

  public fun bossCount(bossCount: Int) {
    it.property("bossCount", bossCount.toString())
  }

  public fun bossGroup(bossGroup: String) {
    it.property("bossGroup", bossGroup)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun disconnectOnNoReply(disconnectOnNoReply: String) {
    it.property("disconnectOnNoReply", disconnectOnNoReply)
  }

  public fun disconnectOnNoReply(disconnectOnNoReply: Boolean) {
    it.property("disconnectOnNoReply", disconnectOnNoReply.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun nettyServerBootstrapFactory(nettyServerBootstrapFactory: String) {
    it.property("nettyServerBootstrapFactory", nettyServerBootstrapFactory)
  }

  public fun networkInterface(networkInterface: String) {
    it.property("networkInterface", networkInterface)
  }

  public fun noReplyLogLevel(noReplyLogLevel: String) {
    it.property("noReplyLogLevel", noReplyLogLevel)
  }

  public
      fun serverClosedChannelExceptionCaughtLogLevel(serverClosedChannelExceptionCaughtLogLevel: String) {
    it.property("serverClosedChannelExceptionCaughtLogLevel",
        serverClosedChannelExceptionCaughtLogLevel)
  }

  public fun serverExceptionCaughtLogLevel(serverExceptionCaughtLogLevel: String) {
    it.property("serverExceptionCaughtLogLevel", serverExceptionCaughtLogLevel)
  }

  public fun serverInitializerFactory(serverInitializerFactory: String) {
    it.property("serverInitializerFactory", serverInitializerFactory)
  }

  public fun usingExecutorService(usingExecutorService: String) {
    it.property("usingExecutorService", usingExecutorService)
  }

  public fun usingExecutorService(usingExecutorService: Boolean) {
    it.property("usingExecutorService", usingExecutorService.toString())
  }

  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  public fun connectTimeout(connectTimeout: Int) {
    it.property("connectTimeout", connectTimeout.toString())
  }

  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
  }

  public fun clientInitializerFactory(clientInitializerFactory: String) {
    it.property("clientInitializerFactory", clientInitializerFactory)
  }

  public fun correlationManager(correlationManager: String) {
    it.property("correlationManager", correlationManager)
  }

  public fun lazyChannelCreation(lazyChannelCreation: String) {
    it.property("lazyChannelCreation", lazyChannelCreation)
  }

  public fun lazyChannelCreation(lazyChannelCreation: Boolean) {
    it.property("lazyChannelCreation", lazyChannelCreation.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun producerPoolBlockWhenExhausted(producerPoolBlockWhenExhausted: String) {
    it.property("producerPoolBlockWhenExhausted", producerPoolBlockWhenExhausted)
  }

  public fun producerPoolBlockWhenExhausted(producerPoolBlockWhenExhausted: Boolean) {
    it.property("producerPoolBlockWhenExhausted", producerPoolBlockWhenExhausted.toString())
  }

  public fun producerPoolEnabled(producerPoolEnabled: String) {
    it.property("producerPoolEnabled", producerPoolEnabled)
  }

  public fun producerPoolEnabled(producerPoolEnabled: Boolean) {
    it.property("producerPoolEnabled", producerPoolEnabled.toString())
  }

  public fun producerPoolMaxIdle(producerPoolMaxIdle: String) {
    it.property("producerPoolMaxIdle", producerPoolMaxIdle)
  }

  public fun producerPoolMaxIdle(producerPoolMaxIdle: Int) {
    it.property("producerPoolMaxIdle", producerPoolMaxIdle.toString())
  }

  public fun producerPoolMaxTotal(producerPoolMaxTotal: String) {
    it.property("producerPoolMaxTotal", producerPoolMaxTotal)
  }

  public fun producerPoolMaxTotal(producerPoolMaxTotal: Int) {
    it.property("producerPoolMaxTotal", producerPoolMaxTotal.toString())
  }

  public fun producerPoolMaxWait(producerPoolMaxWait: String) {
    it.property("producerPoolMaxWait", producerPoolMaxWait)
  }

  public fun producerPoolMaxWait(producerPoolMaxWait: Int) {
    it.property("producerPoolMaxWait", producerPoolMaxWait.toString())
  }

  public fun producerPoolMinEvictableIdle(producerPoolMinEvictableIdle: String) {
    it.property("producerPoolMinEvictableIdle", producerPoolMinEvictableIdle)
  }

  public fun producerPoolMinEvictableIdle(producerPoolMinEvictableIdle: Int) {
    it.property("producerPoolMinEvictableIdle", producerPoolMinEvictableIdle.toString())
  }

  public fun producerPoolMinIdle(producerPoolMinIdle: String) {
    it.property("producerPoolMinIdle", producerPoolMinIdle)
  }

  public fun producerPoolMinIdle(producerPoolMinIdle: Int) {
    it.property("producerPoolMinIdle", producerPoolMinIdle.toString())
  }

  public fun udpConnectionlessSending(udpConnectionlessSending: String) {
    it.property("udpConnectionlessSending", udpConnectionlessSending)
  }

  public fun udpConnectionlessSending(udpConnectionlessSending: Boolean) {
    it.property("udpConnectionlessSending", udpConnectionlessSending.toString())
  }

  public fun useByteBuf(useByteBuf: String) {
    it.property("useByteBuf", useByteBuf)
  }

  public fun useByteBuf(useByteBuf: Boolean) {
    it.property("useByteBuf", useByteBuf.toString())
  }

  public fun hostnameVerification(hostnameVerification: String) {
    it.property("hostnameVerification", hostnameVerification)
  }

  public fun hostnameVerification(hostnameVerification: Boolean) {
    it.property("hostnameVerification", hostnameVerification.toString())
  }

  public fun allowSerializedHeaders(allowSerializedHeaders: String) {
    it.property("allowSerializedHeaders", allowSerializedHeaders)
  }

  public fun allowSerializedHeaders(allowSerializedHeaders: Boolean) {
    it.property("allowSerializedHeaders", allowSerializedHeaders.toString())
  }

  public fun channelGroup(channelGroup: String) {
    it.property("channelGroup", channelGroup)
  }

  public fun nativeTransport(nativeTransport: String) {
    it.property("nativeTransport", nativeTransport)
  }

  public fun nativeTransport(nativeTransport: Boolean) {
    it.property("nativeTransport", nativeTransport.toString())
  }

  public fun options(options: String) {
    it.property("options", options)
  }

  public fun receiveBufferSize(receiveBufferSize: String) {
    it.property("receiveBufferSize", receiveBufferSize)
  }

  public fun receiveBufferSize(receiveBufferSize: Int) {
    it.property("receiveBufferSize", receiveBufferSize.toString())
  }

  public fun receiveBufferSizePredictor(receiveBufferSizePredictor: String) {
    it.property("receiveBufferSizePredictor", receiveBufferSizePredictor)
  }

  public fun receiveBufferSizePredictor(receiveBufferSizePredictor: Int) {
    it.property("receiveBufferSizePredictor", receiveBufferSizePredictor.toString())
  }

  public fun sendBufferSize(sendBufferSize: String) {
    it.property("sendBufferSize", sendBufferSize)
  }

  public fun sendBufferSize(sendBufferSize: Int) {
    it.property("sendBufferSize", sendBufferSize.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }

  public fun udpByteArrayCodec(udpByteArrayCodec: String) {
    it.property("udpByteArrayCodec", udpByteArrayCodec)
  }

  public fun udpByteArrayCodec(udpByteArrayCodec: Boolean) {
    it.property("udpByteArrayCodec", udpByteArrayCodec.toString())
  }

  public fun unixDomainSocketPath(unixDomainSocketPath: String) {
    it.property("unixDomainSocketPath", unixDomainSocketPath)
  }

  public fun workerCount(workerCount: String) {
    it.property("workerCount", workerCount)
  }

  public fun workerCount(workerCount: Int) {
    it.property("workerCount", workerCount.toString())
  }

  public fun workerGroup(workerGroup: String) {
    it.property("workerGroup", workerGroup)
  }

  public fun allowDefaultCodec(allowDefaultCodec: String) {
    it.property("allowDefaultCodec", allowDefaultCodec)
  }

  public fun allowDefaultCodec(allowDefaultCodec: Boolean) {
    it.property("allowDefaultCodec", allowDefaultCodec.toString())
  }

  public fun autoAppendDelimiter(autoAppendDelimiter: String) {
    it.property("autoAppendDelimiter", autoAppendDelimiter)
  }

  public fun autoAppendDelimiter(autoAppendDelimiter: Boolean) {
    it.property("autoAppendDelimiter", autoAppendDelimiter.toString())
  }

  public fun decoderMaxLineLength(decoderMaxLineLength: String) {
    it.property("decoderMaxLineLength", decoderMaxLineLength)
  }

  public fun decoderMaxLineLength(decoderMaxLineLength: Int) {
    it.property("decoderMaxLineLength", decoderMaxLineLength.toString())
  }

  public fun decoders(decoders: String) {
    it.property("decoders", decoders)
  }

  public fun delimiter(delimiter: String) {
    it.property("delimiter", delimiter)
  }

  public fun encoders(encoders: String) {
    it.property("encoders", encoders)
  }

  public fun encoding(encoding: String) {
    it.property("encoding", encoding)
  }

  public fun textline(textline: String) {
    it.property("textline", textline)
  }

  public fun textline(textline: Boolean) {
    it.property("textline", textline.toString())
  }

  public fun enabledProtocols(enabledProtocols: String) {
    it.property("enabledProtocols", enabledProtocols)
  }

  public fun keyStoreFile(keyStoreFile: String) {
    it.property("keyStoreFile", keyStoreFile)
  }

  public fun keyStoreFormat(keyStoreFormat: String) {
    it.property("keyStoreFormat", keyStoreFormat)
  }

  public fun keyStoreResource(keyStoreResource: String) {
    it.property("keyStoreResource", keyStoreResource)
  }

  public fun needClientAuth(needClientAuth: String) {
    it.property("needClientAuth", needClientAuth)
  }

  public fun needClientAuth(needClientAuth: Boolean) {
    it.property("needClientAuth", needClientAuth.toString())
  }

  public fun passphrase(passphrase: String) {
    it.property("passphrase", passphrase)
  }

  public fun securityProvider(securityProvider: String) {
    it.property("securityProvider", securityProvider)
  }

  public fun ssl(ssl: String) {
    it.property("ssl", ssl)
  }

  public fun ssl(ssl: Boolean) {
    it.property("ssl", ssl.toString())
  }

  public fun sslClientCertHeaders(sslClientCertHeaders: String) {
    it.property("sslClientCertHeaders", sslClientCertHeaders)
  }

  public fun sslClientCertHeaders(sslClientCertHeaders: Boolean) {
    it.property("sslClientCertHeaders", sslClientCertHeaders.toString())
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun sslHandler(sslHandler: String) {
    it.property("sslHandler", sslHandler)
  }

  public fun trustStoreFile(trustStoreFile: String) {
    it.property("trustStoreFile", trustStoreFile)
  }

  public fun trustStoreResource(trustStoreResource: String) {
    it.property("trustStoreResource", trustStoreResource)
  }
}
