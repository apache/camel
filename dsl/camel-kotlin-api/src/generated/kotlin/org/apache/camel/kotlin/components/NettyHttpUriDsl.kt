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

public fun UriDsl.`netty-http`(i: NettyHttpUriDsl.() -> Unit) {
  NettyHttpUriDsl(this).apply(i)
}

@CamelDslMarker
public class NettyHttpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("netty-http")
  }

  private var protocol: String = ""

  private var host: String = ""

  private var port: String = ""

  private var path: String = ""

  public fun protocol(protocol: String) {
    this.protocol = protocol
    it.url("$protocol://$host:$port/$path")
  }

  public fun host(host: String) {
    this.host = host
    it.url("$protocol://$host:$port/$path")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$protocol://$host:$port/$path")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$protocol://$host:$port/$path")
  }

  public fun path(path: String) {
    this.path = path
    it.url("$protocol://$host:$port/$path")
  }

  public fun bridgeEndpoint(bridgeEndpoint: String) {
    it.property("bridgeEndpoint", bridgeEndpoint)
  }

  public fun bridgeEndpoint(bridgeEndpoint: Boolean) {
    it.property("bridgeEndpoint", bridgeEndpoint.toString())
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

  public fun matchOnUriPrefix(matchOnUriPrefix: String) {
    it.property("matchOnUriPrefix", matchOnUriPrefix)
  }

  public fun matchOnUriPrefix(matchOnUriPrefix: Boolean) {
    it.property("matchOnUriPrefix", matchOnUriPrefix.toString())
  }

  public fun muteException(muteException: String) {
    it.property("muteException", muteException)
  }

  public fun muteException(muteException: Boolean) {
    it.property("muteException", muteException.toString())
  }

  public fun send503whenSuspended(send503whenSuspended: String) {
    it.property("send503whenSuspended", send503whenSuspended)
  }

  public fun send503whenSuspended(send503whenSuspended: Boolean) {
    it.property("send503whenSuspended", send503whenSuspended.toString())
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

  public fun chunkedMaxContentLength(chunkedMaxContentLength: String) {
    it.property("chunkedMaxContentLength", chunkedMaxContentLength)
  }

  public fun chunkedMaxContentLength(chunkedMaxContentLength: Int) {
    it.property("chunkedMaxContentLength", chunkedMaxContentLength.toString())
  }

  public fun compression(compression: String) {
    it.property("compression", compression)
  }

  public fun compression(compression: Boolean) {
    it.property("compression", compression.toString())
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

  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  public fun logWarnOnBadRequest(logWarnOnBadRequest: String) {
    it.property("logWarnOnBadRequest", logWarnOnBadRequest)
  }

  public fun logWarnOnBadRequest(logWarnOnBadRequest: Boolean) {
    it.property("logWarnOnBadRequest", logWarnOnBadRequest.toString())
  }

  public fun mapHeaders(mapHeaders: String) {
    it.property("mapHeaders", mapHeaders)
  }

  public fun mapHeaders(mapHeaders: Boolean) {
    it.property("mapHeaders", mapHeaders.toString())
  }

  public fun maxChunkSize(maxChunkSize: String) {
    it.property("maxChunkSize", maxChunkSize)
  }

  public fun maxChunkSize(maxChunkSize: Int) {
    it.property("maxChunkSize", maxChunkSize.toString())
  }

  public fun maxHeaderSize(maxHeaderSize: String) {
    it.property("maxHeaderSize", maxHeaderSize)
  }

  public fun maxHeaderSize(maxHeaderSize: Int) {
    it.property("maxHeaderSize", maxHeaderSize.toString())
  }

  public fun maxInitialLineLength(maxInitialLineLength: String) {
    it.property("maxInitialLineLength", maxInitialLineLength)
  }

  public fun maxInitialLineLength(maxInitialLineLength: Int) {
    it.property("maxInitialLineLength", maxInitialLineLength.toString())
  }

  public fun nettyServerBootstrapFactory(nettyServerBootstrapFactory: String) {
    it.property("nettyServerBootstrapFactory", nettyServerBootstrapFactory)
  }

  public fun nettySharedHttpServer(nettySharedHttpServer: String) {
    it.property("nettySharedHttpServer", nettySharedHttpServer)
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

  public fun traceEnabled(traceEnabled: String) {
    it.property("traceEnabled", traceEnabled)
  }

  public fun traceEnabled(traceEnabled: Boolean) {
    it.property("traceEnabled", traceEnabled.toString())
  }

  public fun urlDecodeHeaders(urlDecodeHeaders: String) {
    it.property("urlDecodeHeaders", urlDecodeHeaders)
  }

  public fun urlDecodeHeaders(urlDecodeHeaders: Boolean) {
    it.property("urlDecodeHeaders", urlDecodeHeaders.toString())
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

  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  public fun clientInitializerFactory(clientInitializerFactory: String) {
    it.property("clientInitializerFactory", clientInitializerFactory)
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

  public fun okStatusCodeRange(okStatusCodeRange: String) {
    it.property("okStatusCodeRange", okStatusCodeRange)
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

  public fun useRelativePath(useRelativePath: String) {
    it.property("useRelativePath", useRelativePath)
  }

  public fun useRelativePath(useRelativePath: Boolean) {
    it.property("useRelativePath", useRelativePath.toString())
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

  public fun configuration(configuration: String) {
    it.property("configuration", configuration)
  }

  public fun disableStreamCache(disableStreamCache: String) {
    it.property("disableStreamCache", disableStreamCache)
  }

  public fun disableStreamCache(disableStreamCache: Boolean) {
    it.property("disableStreamCache", disableStreamCache.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun nativeTransport(nativeTransport: String) {
    it.property("nativeTransport", nativeTransport)
  }

  public fun nativeTransport(nativeTransport: Boolean) {
    it.property("nativeTransport", nativeTransport.toString())
  }

  public fun nettyHttpBinding(nettyHttpBinding: String) {
    it.property("nettyHttpBinding", nettyHttpBinding)
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

  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
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

  public fun decoders(decoders: String) {
    it.property("decoders", decoders)
  }

  public fun encoders(encoders: String) {
    it.property("encoders", encoders)
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

  public fun securityConfiguration(securityConfiguration: String) {
    it.property("securityConfiguration", securityConfiguration)
  }

  public fun securityOptions(securityOptions: String) {
    it.property("securityOptions", securityOptions)
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
