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
 * Expose WebSocket endpoints using the Atmosphere framework.
 */
public fun UriDsl.`atmosphere-websocket`(i: AtmosphereWebsocketUriDsl.() -> Unit) {
  AtmosphereWebsocketUriDsl(this).apply(i)
}

@CamelDslMarker
public class AtmosphereWebsocketUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("atmosphere-websocket")
  }

  private var servicePath: String = ""

  /**
   * Name of websocket endpoint
   */
  public fun servicePath(servicePath: String) {
    this.servicePath = servicePath
    it.url("$servicePath")
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
   * Determines whether or not the raw input stream from Servlet is cached or not (Camel will read
   * the stream into a in memory/overflow to file, Stream caching) cache. By default Camel will cache
   * the Servlet input stream to support reading it multiple times to ensure it Camel can retrieve all
   * data from the stream. However you can set this option to true when you for example need to access
   * the raw stream, such as streaming it directly to a file or other persistent store.
   * DefaultHttpBinding will copy the request input stream into a stream cache and put it into message
   * body if this option is false to support reading the stream multiple times. If you use Servlet to
   * bridge/proxy an endpoint then consider enabling this option to improve performance, in case you do
   * not need to read the message payload multiple times. The http producer will by default cache the
   * response body stream. If setting this option to true, then the producers will not cache the
   * response body stream but use the response stream as-is as the message body.
   */
  public fun disableStreamCache(disableStreamCache: String) {
    it.property("disableStreamCache", disableStreamCache)
  }

  /**
   * Determines whether or not the raw input stream from Servlet is cached or not (Camel will read
   * the stream into a in memory/overflow to file, Stream caching) cache. By default Camel will cache
   * the Servlet input stream to support reading it multiple times to ensure it Camel can retrieve all
   * data from the stream. However you can set this option to true when you for example need to access
   * the raw stream, such as streaming it directly to a file or other persistent store.
   * DefaultHttpBinding will copy the request input stream into a stream cache and put it into message
   * body if this option is false to support reading the stream multiple times. If you use Servlet to
   * bridge/proxy an endpoint then consider enabling this option to improve performance, in case you do
   * not need to read the message payload multiple times. The http producer will by default cache the
   * response body stream. If setting this option to true, then the producers will not cache the
   * response body stream but use the response stream as-is as the message body.
   */
  public fun disableStreamCache(disableStreamCache: Boolean) {
    it.property("disableStreamCache", disableStreamCache.toString())
  }

  /**
   * Whether to send to all (broadcast) or send to a single receiver.
   */
  public fun sendToAll(sendToAll: String) {
    it.property("sendToAll", sendToAll)
  }

  /**
   * Whether to send to all (broadcast) or send to a single receiver.
   */
  public fun sendToAll(sendToAll: Boolean) {
    it.property("sendToAll", sendToAll.toString())
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
   * To enable streaming to send data as multiple text fragments.
   */
  public fun useStreaming(useStreaming: String) {
    it.property("useStreaming", useStreaming)
  }

  /**
   * To enable streaming to send data as multiple text fragments.
   */
  public fun useStreaming(useStreaming: Boolean) {
    it.property("useStreaming", useStreaming.toString())
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
   * Name of the servlet to use
   */
  public fun servletName(servletName: String) {
    it.property("servletName", servletName)
  }

  /**
   * Whether to automatic bind multipart/form-data as attachments on the Camel Exchange. The options
   * attachmentMultipartBinding=true and disableStreamCache=false cannot work together. Remove
   * disableStreamCache to use AttachmentMultipartBinding. This is turn off by default as this may
   * require servlet specific configuration to enable this when using Servlet's.
   */
  public fun attachmentMultipartBinding(attachmentMultipartBinding: String) {
    it.property("attachmentMultipartBinding", attachmentMultipartBinding)
  }

  /**
   * Whether to automatic bind multipart/form-data as attachments on the Camel Exchange. The options
   * attachmentMultipartBinding=true and disableStreamCache=false cannot work together. Remove
   * disableStreamCache to use AttachmentMultipartBinding. This is turn off by default as this may
   * require servlet specific configuration to enable this when using Servlet's.
   */
  public fun attachmentMultipartBinding(attachmentMultipartBinding: Boolean) {
    it.property("attachmentMultipartBinding", attachmentMultipartBinding.toString())
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
   * Whitelist of accepted filename extensions for accepting uploaded files. Multiple extensions can
   * be separated by comma, such as txt,xml.
   */
  public fun fileNameExtWhitelist(fileNameExtWhitelist: String) {
    it.property("fileNameExtWhitelist", fileNameExtWhitelist)
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
   * If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the
   * endpoint's URI for request. You may also set the option throwExceptionOnFailure to be false to let
   * the HttpProducer send all the fault response back.
   */
  public fun bridgeEndpoint(bridgeEndpoint: String) {
    it.property("bridgeEndpoint", bridgeEndpoint)
  }

  /**
   * If the option is true, HttpProducer will ignore the Exchange.HTTP_URI header, and use the
   * endpoint's URI for request. You may also set the option throwExceptionOnFailure to be false to let
   * the HttpProducer send all the fault response back.
   */
  public fun bridgeEndpoint(bridgeEndpoint: Boolean) {
    it.property("bridgeEndpoint", bridgeEndpoint.toString())
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
}
