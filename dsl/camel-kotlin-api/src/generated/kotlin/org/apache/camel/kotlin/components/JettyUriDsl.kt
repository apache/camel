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
 * Expose HTTP endpoints using Jetty 12.
 */
public fun UriDsl.jetty(i: JettyUriDsl.() -> Unit) {
  JettyUriDsl(this).apply(i)
}

@CamelDslMarker
public class JettyUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jetty")
  }

  private var httpUri: String = ""

  /**
   * The url of the HTTP endpoint to call.
   */
  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("$httpUri")
  }

  /**
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * To use a custom HttpBinding to control the mapping between Camel message and HttpClient.
   */
  public fun httpBinding(httpBinding: String) {
    it.property("httpBinding", httpBinding)
  }

  /**
   * If this option is false the Servlet will disable the HTTP streaming and set the content-length
   * header on the response
   */
  public fun chunked(chunked: String) {
    it.property("chunked", chunked)
  }

  /**
   * If this option is false the Servlet will disable the HTTP streaming and set the content-length
   * header on the response
   */
  public fun chunked(chunked: Boolean) {
    it.property("chunked", chunked.toString())
  }

  /**
   * Determines whether or not the raw input stream is cached or not. The Camel consumer
   * (camel-servlet, camel-jetty etc.) will by default cache the input stream to support reading it
   * multiple times to ensure it Camel can retrieve all data from the stream. However you can set this
   * option to true when you for example need to access the raw stream, such as streaming it directly
   * to a file or other persistent store. DefaultHttpBinding will copy the request input stream into a
   * stream cache and put it into message body if this option is false to support reading the stream
   * multiple times. If you use Servlet to bridge/proxy an endpoint then consider enabling this option
   * to improve performance, in case you do not need to read the message payload multiple times. The
   * producer (camel-http) will by default cache the response body stream. If setting this option to
   * true, then the producers will not cache the response body stream but use the response stream as-is
   * (the stream can only be read once) as the message body.
   */
  public fun disableStreamCache(disableStreamCache: String) {
    it.property("disableStreamCache", disableStreamCache)
  }

  /**
   * Determines whether or not the raw input stream is cached or not. The Camel consumer
   * (camel-servlet, camel-jetty etc.) will by default cache the input stream to support reading it
   * multiple times to ensure it Camel can retrieve all data from the stream. However you can set this
   * option to true when you for example need to access the raw stream, such as streaming it directly
   * to a file or other persistent store. DefaultHttpBinding will copy the request input stream into a
   * stream cache and put it into message body if this option is false to support reading the stream
   * multiple times. If you use Servlet to bridge/proxy an endpoint then consider enabling this option
   * to improve performance, in case you do not need to read the message payload multiple times. The
   * producer (camel-http) will by default cache the response body stream. If setting this option to
   * true, then the producers will not cache the response body stream but use the response stream as-is
   * (the stream can only be read once) as the message body.
   */
  public fun disableStreamCache(disableStreamCache: Boolean) {
    it.property("disableStreamCache", disableStreamCache.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception
   * was send back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is, instead of the
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Java will deserialize the incoming data
   * from the request to Java and that can be a potential security risk.
   */
  public fun transferException(transferException: String) {
    it.property("transferException", transferException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side, and if the caused Exception
   * was send back serialized in the response as a application/x-java-serialized-object content type.
   * On the producer side the exception will be deserialized and thrown as is, instead of the
   * HttpOperationFailedException. The caused exception is required to be serialized. This is by
   * default turned off. If you enable this then be aware that Java will deserialize the incoming data
   * from the request to Java and that can be a potential security risk.
   */
  public fun transferException(transferException: Boolean) {
    it.property("transferException", transferException.toString())
  }

  /**
   * Configure the consumer to work in async mode
   */
  public fun async(async: String) {
    it.property("async", async)
  }

  /**
   * Configure the consumer to work in async mode
   */
  public fun async(async: Boolean) {
    it.property("async", async.toString())
  }

  /**
   * Allows to set a timeout in millis when using Jetty as consumer (server). By default Jetty uses
   * 30000. You can use a value of = 0 to never expire. If a timeout occurs then the request will be
   * expired and Jetty will return back a http error 503 to the client. This option is only in use when
   * using Jetty with the Asynchronous Routing Engine.
   */
  public fun continuationTimeout(continuationTimeout: String) {
    it.property("continuationTimeout", continuationTimeout)
  }

  /**
   * Allows to set a timeout in millis when using Jetty as consumer (server). By default Jetty uses
   * 30000. You can use a value of = 0 to never expire. If a timeout occurs then the request will be
   * expired and Jetty will return back a http error 503 to the client. This option is only in use when
   * using Jetty with the Asynchronous Routing Engine.
   */
  public fun continuationTimeout(continuationTimeout: Int) {
    it.property("continuationTimeout", continuationTimeout.toString())
  }

  /**
   * If the option is true, Jetty server will setup the CrossOriginFilter which supports the CORS
   * out of box.
   */
  public fun enableCORS(enableCORS: String) {
    it.property("enableCORS", enableCORS)
  }

  /**
   * If the option is true, Jetty server will setup the CrossOriginFilter which supports the CORS
   * out of box.
   */
  public fun enableCORS(enableCORS: Boolean) {
    it.property("enableCORS", enableCORS.toString())
  }

  /**
   * If this option is true, Jetty JMX support will be enabled for this endpoint. See Jetty JMX
   * support for more details.
   */
  public fun enableJmx(enableJmx: String) {
    it.property("enableJmx", enableJmx)
  }

  /**
   * If this option is true, Jetty JMX support will be enabled for this endpoint. See Jetty JMX
   * support for more details.
   */
  public fun enableJmx(enableJmx: Boolean) {
    it.property("enableJmx", enableJmx.toString())
  }

  /**
   * Whether org.apache.camel.component.jetty.MultiPartFilter is enabled or not. You should set this
   * value to false when bridging endpoints, to ensure multipart requests is proxied/bridged as well.
   */
  public fun enableMultipartFilter(enableMultipartFilter: String) {
    it.property("enableMultipartFilter", enableMultipartFilter)
  }

  /**
   * Whether org.apache.camel.component.jetty.MultiPartFilter is enabled or not. You should set this
   * value to false when bridging endpoints, to ensure multipart requests is proxied/bridged as well.
   */
  public fun enableMultipartFilter(enableMultipartFilter: Boolean) {
    it.property("enableMultipartFilter", enableMultipartFilter.toString())
  }

  /**
   * Used to only allow consuming if the HttpMethod matches, such as GET/POST/PUT etc. Multiple
   * methods can be specified separated by comma.
   */
  public fun httpMethodRestrict(httpMethodRestrict: String) {
    it.property("httpMethodRestrict", httpMethodRestrict)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the exception's stack trace
   * will be logged when the exception stack trace is not sent in the response's body.
   */
  public fun logException(logException: String) {
    it.property("logException", logException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the exception's stack trace
   * will be logged when the exception stack trace is not sent in the response's body.
   */
  public fun logException(logException: Boolean) {
    it.property("logException", logException.toString())
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: String) {
    it.property("matchOnUriPrefix", matchOnUriPrefix)
  }

  /**
   * Whether or not the consumer should try to find a target consumer by matching the URI prefix if
   * no exact match is found.
   */
  public fun matchOnUriPrefix(matchOnUriPrefix: Boolean) {
    it.property("matchOnUriPrefix", matchOnUriPrefix.toString())
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: String) {
    it.property("muteException", muteException)
  }

  /**
   * If enabled and an Exchange failed processing on the consumer side the response's body won't
   * contain the exception's stack trace.
   */
  public fun muteException(muteException: Boolean) {
    it.property("muteException", muteException.toString())
  }

  /**
   * To use a custom buffer size on the jakarta.servlet.ServletResponse.
   */
  public fun responseBufferSize(responseBufferSize: String) {
    it.property("responseBufferSize", responseBufferSize)
  }

  /**
   * To use a custom buffer size on the jakarta.servlet.ServletResponse.
   */
  public fun responseBufferSize(responseBufferSize: Int) {
    it.property("responseBufferSize", responseBufferSize.toString())
  }

  /**
   * If the option is true, jetty server will send the date header to the client which sends the
   * request. NOTE please make sure there is no any other camel-jetty endpoint is share the same port,
   * otherwise this option may not work as expected.
   */
  public fun sendDateHeader(sendDateHeader: String) {
    it.property("sendDateHeader", sendDateHeader)
  }

  /**
   * If the option is true, jetty server will send the date header to the client which sends the
   * request. NOTE please make sure there is no any other camel-jetty endpoint is share the same port,
   * otherwise this option may not work as expected.
   */
  public fun sendDateHeader(sendDateHeader: Boolean) {
    it.property("sendDateHeader", sendDateHeader.toString())
  }

  /**
   * If the option is true, jetty will send the server header with the jetty version information to
   * the client which sends the request. NOTE please make sure there is no any other camel-jetty
   * endpoint is share the same port, otherwise this option may not work as expected.
   */
  public fun sendServerVersion(sendServerVersion: String) {
    it.property("sendServerVersion", sendServerVersion)
  }

  /**
   * If the option is true, jetty will send the server header with the jetty version information to
   * the client which sends the request. NOTE please make sure there is no any other camel-jetty
   * endpoint is share the same port, otherwise this option may not work as expected.
   */
  public fun sendServerVersion(sendServerVersion: Boolean) {
    it.property("sendServerVersion", sendServerVersion.toString())
  }

  /**
   * Specifies whether to enable the session manager on the server side of Jetty.
   */
  public fun sessionSupport(sessionSupport: String) {
    it.property("sessionSupport", sessionSupport)
  }

  /**
   * Specifies whether to enable the session manager on the server side of Jetty.
   */
  public fun sessionSupport(sessionSupport: Boolean) {
    it.property("sessionSupport", sessionSupport.toString())
  }

  /**
   * Whether or not to use Jetty continuations for the Jetty Server.
   */
  public fun useContinuation(useContinuation: String) {
    it.property("useContinuation", useContinuation)
  }

  /**
   * Whether or not to use Jetty continuations for the Jetty Server.
   */
  public fun useContinuation(useContinuation: Boolean) {
    it.property("useContinuation", useContinuation.toString())
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
   * Whether to eager check whether the HTTP requests has content if the content-length header is 0
   * or not present. This can be turned on in case HTTP clients do not send streamed data.
   */
  public fun eagerCheckContentAvailable(eagerCheckContentAvailable: String) {
    it.property("eagerCheckContentAvailable", eagerCheckContentAvailable)
  }

  /**
   * Whether to eager check whether the HTTP requests has content if the content-length header is 0
   * or not present. This can be turned on in case HTTP clients do not send streamed data.
   */
  public fun eagerCheckContentAvailable(eagerCheckContentAvailable: Boolean) {
    it.property("eagerCheckContentAvailable", eagerCheckContentAvailable.toString())
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
   * The size threshold after which files will be written to disk for multipart/form-data requests.
   * By default the files are not written to disk
   */
  public fun fileSizeThreshold(fileSizeThreshold: String) {
    it.property("fileSizeThreshold", fileSizeThreshold)
  }

  /**
   * The size threshold after which files will be written to disk for multipart/form-data requests.
   * By default the files are not written to disk
   */
  public fun fileSizeThreshold(fileSizeThreshold: Int) {
    it.property("fileSizeThreshold", fileSizeThreshold.toString())
  }

  /**
   * The directory location where files will be store for multipart/form-data requests. By default
   * the files are written in the system temporary folder
   */
  public fun filesLocation(filesLocation: String) {
    it.property("filesLocation", filesLocation)
  }

  /**
   * Configuration of the filter init parameters. These parameters will be applied to the filter
   * list before starting the jetty server.
   */
  public fun filterInitParameters(filterInitParameters: String) {
    it.property("filterInitParameters", filterInitParameters)
  }

  /**
   * Allows using a custom filters which is putted into a list and can be find in the Registry.
   * Multiple values can be separated by comma.
   */
  public fun filters(filters: String) {
    it.property("filters", filters)
  }

  /**
   * Specifies a comma-delimited set of Handler instances to lookup in your Registry. These handlers
   * are added to the Jetty servlet context (for example, to add security). Important: You can not use
   * different handlers with different Jetty endpoints using the same port number. The handlers is
   * associated to the port number. If you need different handlers, then use different port numbers.
   */
  public fun handlers(handlers: String) {
    it.property("handlers", handlers)
  }

  /**
   * The max idle time (in milli seconds) is applied to an HTTP request for IO operations and
   * delayed dispatch. Idle time 0 implies an infinite timeout, -1 (default) implies no HTTP channel
   * timeout and the connection timeout is used instead.
   */
  public fun idleTimeout(idleTimeout: String) {
    it.property("idleTimeout", idleTimeout)
  }

  /**
   * The max idle time (in milli seconds) is applied to an HTTP request for IO operations and
   * delayed dispatch. Idle time 0 implies an infinite timeout, -1 (default) implies no HTTP channel
   * timeout and the connection timeout is used instead.
   */
  public fun idleTimeout(idleTimeout: Int) {
    it.property("idleTimeout", idleTimeout.toString())
  }

  /**
   * If this option is true then IN exchange Body of the exchange will be mapped to HTTP body.
   * Setting this to false will avoid the HTTP mapping.
   */
  public fun mapHttpMessageBody(mapHttpMessageBody: String) {
    it.property("mapHttpMessageBody", mapHttpMessageBody)
  }

  /**
   * If this option is true then IN exchange Body of the exchange will be mapped to HTTP body.
   * Setting this to false will avoid the HTTP mapping.
   */
  public fun mapHttpMessageBody(mapHttpMessageBody: Boolean) {
    it.property("mapHttpMessageBody", mapHttpMessageBody.toString())
  }

  /**
   * If this option is true then IN exchange Form Encoded body of the exchange will be mapped to
   * HTTP. Setting this to false will avoid the HTTP Form Encoded body mapping.
   */
  public fun mapHttpMessageFormUrlEncodedBody(mapHttpMessageFormUrlEncodedBody: String) {
    it.property("mapHttpMessageFormUrlEncodedBody", mapHttpMessageFormUrlEncodedBody)
  }

  /**
   * If this option is true then IN exchange Form Encoded body of the exchange will be mapped to
   * HTTP. Setting this to false will avoid the HTTP Form Encoded body mapping.
   */
  public fun mapHttpMessageFormUrlEncodedBody(mapHttpMessageFormUrlEncodedBody: Boolean) {
    it.property("mapHttpMessageFormUrlEncodedBody", mapHttpMessageFormUrlEncodedBody.toString())
  }

  /**
   * If this option is true then IN exchange Headers of the exchange will be mapped to HTTP headers.
   * Setting this to false will avoid the HTTP Headers mapping.
   */
  public fun mapHttpMessageHeaders(mapHttpMessageHeaders: String) {
    it.property("mapHttpMessageHeaders", mapHttpMessageHeaders)
  }

  /**
   * If this option is true then IN exchange Headers of the exchange will be mapped to HTTP headers.
   * Setting this to false will avoid the HTTP Headers mapping.
   */
  public fun mapHttpMessageHeaders(mapHttpMessageHeaders: Boolean) {
    it.property("mapHttpMessageHeaders", mapHttpMessageHeaders.toString())
  }

  /**
   * The maximum size allowed for uploaded files. -1 means no limit
   */
  public fun maxFileSize(maxFileSize: String) {
    it.property("maxFileSize", maxFileSize)
  }

  /**
   * The maximum size allowed for uploaded files. -1 means no limit
   */
  public fun maxFileSize(maxFileSize: Int) {
    it.property("maxFileSize", maxFileSize.toString())
  }

  /**
   * The maximum size allowed for multipart/form-data requests. -1 means no limit
   */
  public fun maxRequestSize(maxRequestSize: String) {
    it.property("maxRequestSize", maxRequestSize)
  }

  /**
   * The maximum size allowed for multipart/form-data requests. -1 means no limit
   */
  public fun maxRequestSize(maxRequestSize: Int) {
    it.property("maxRequestSize", maxRequestSize.toString())
  }

  /**
   * Allows using a custom multipart filter. Note: setting multipartFilterRef forces the value of
   * enableMultipartFilter to true.
   */
  public fun multipartFilter(multipartFilter: String) {
    it.property("multipartFilter", multipartFilter)
  }

  /**
   * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is
   * turned off.
   */
  public fun optionsEnabled(optionsEnabled: String) {
    it.property("optionsEnabled", optionsEnabled)
  }

  /**
   * Specifies whether to enable HTTP OPTIONS for this Servlet consumer. By default OPTIONS is
   * turned off.
   */
  public fun optionsEnabled(optionsEnabled: Boolean) {
    it.property("optionsEnabled", optionsEnabled.toString())
  }

  /**
   * Specifies whether to enable HTTP TRACE for this Servlet consumer. By default TRACE is turned
   * off.
   */
  public fun traceEnabled(traceEnabled: String) {
    it.property("traceEnabled", traceEnabled)
  }

  /**
   * Specifies whether to enable HTTP TRACE for this Servlet consumer. By default TRACE is turned
   * off.
   */
  public fun traceEnabled(traceEnabled: Boolean) {
    it.property("traceEnabled", traceEnabled.toString())
  }

  /**
   * To configure security using SSLContextParameters
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
