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
 * Send requests to external HTTP servers using Apache HTTP Client 5.x.
 */
public fun UriDsl.http(i: HttpUriDsl.() -> Unit) {
  HttpUriDsl(this).apply(i)
}

@CamelDslMarker
public class HttpUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("http")
  }

  private var httpUri: String = ""

  /**
   * The url of the HTTP endpoint to call.
   */
  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("//$httpUri")
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
   * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
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
   * Specifies whether a Connection Close header must be added to HTTP Request. By default
   * connectionClose is false.
   */
  public fun connectionClose(connectionClose: String) {
    it.property("connectionClose", connectionClose)
  }

  /**
   * Specifies whether a Connection Close header must be added to HTTP Request. By default
   * connectionClose is false.
   */
  public fun connectionClose(connectionClose: Boolean) {
    it.property("connectionClose", connectionClose.toString())
  }

  /**
   * Configure the HTTP method to use. The HttpMethod header cannot override this option if set.
   */
  public fun httpMethod(httpMethod: String) {
    it.property("httpMethod", httpMethod)
  }

  /**
   * Whether to skip mapping all the Camel headers as HTTP request headers. If there are no data
   * from Camel headers needed to be included in the HTTP request then this can avoid parsing overhead
   * with many object allocations for the JVM garbage collector.
   */
  public fun skipRequestHeaders(skipRequestHeaders: String) {
    it.property("skipRequestHeaders", skipRequestHeaders)
  }

  /**
   * Whether to skip mapping all the Camel headers as HTTP request headers. If there are no data
   * from Camel headers needed to be included in the HTTP request then this can avoid parsing overhead
   * with many object allocations for the JVM garbage collector.
   */
  public fun skipRequestHeaders(skipRequestHeaders: Boolean) {
    it.property("skipRequestHeaders", skipRequestHeaders.toString())
  }

  /**
   * Whether to skip mapping all the HTTP response headers to Camel headers. If there are no data
   * needed from HTTP headers then this can avoid parsing overhead with many object allocations for the
   * JVM garbage collector.
   */
  public fun skipResponseHeaders(skipResponseHeaders: String) {
    it.property("skipResponseHeaders", skipResponseHeaders)
  }

  /**
   * Whether to skip mapping all the HTTP response headers to Camel headers. If there are no data
   * needed from HTTP headers then this can avoid parsing overhead with many object allocations for the
   * JVM garbage collector.
   */
  public fun skipResponseHeaders(skipResponseHeaders: Boolean) {
    it.property("skipResponseHeaders", skipResponseHeaders.toString())
  }

  /**
   * Option to disable throwing the HttpOperationFailedException in case of failed responses from
   * the remote server. This allows you to get all responses regardless of the HTTP status code.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  /**
   * Option to disable throwing the HttpOperationFailedException in case of failed responses from
   * the remote server. This allows you to get all responses regardless of the HTTP status code.
   */
  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  /**
   * Whether to clear expired cookies before sending the HTTP request. This ensures the cookies
   * store does not keep growing by adding new cookies which is newer removed when they are expired. If
   * the component has disabled cookie management then this option is disabled too.
   */
  public fun clearExpiredCookies(clearExpiredCookies: String) {
    it.property("clearExpiredCookies", clearExpiredCookies)
  }

  /**
   * Whether to clear expired cookies before sending the HTTP request. This ensures the cookies
   * store does not keep growing by adding new cookies which is newer removed when they are expired. If
   * the component has disabled cookie management then this option is disabled too.
   */
  public fun clearExpiredCookies(clearExpiredCookies: Boolean) {
    it.property("clearExpiredCookies", clearExpiredCookies.toString())
  }

  /**
   * Configure a cookie handler to maintain a HTTP session
   */
  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  /**
   * To use a custom CookieStore. By default the BasicCookieStore is used which is an in-memory only
   * cookie store. Notice if bridgeEndpoint=true then the cookie store is forced to be a noop cookie
   * store as cookie shouldn't be stored as we are just bridging (eg acting as a proxy). If a
   * cookieHandler is set then the cookie store is also forced to be a noop cookie store as cookie
   * handling is then performed by the cookieHandler.
   */
  public fun cookieStore(cookieStore: String) {
    it.property("cookieStore", cookieStore)
  }

  /**
   * If this option is true then IN exchange headers will be copied to OUT exchange headers
   * according to copy strategy. Setting this to false, allows to only include the headers from the
   * HTTP response (not propagating IN headers).
   */
  public fun copyHeaders(copyHeaders: String) {
    it.property("copyHeaders", copyHeaders)
  }

  /**
   * If this option is true then IN exchange headers will be copied to OUT exchange headers
   * according to copy strategy. Setting this to false, allows to only include the headers from the
   * HTTP response (not propagating IN headers).
   */
  public fun copyHeaders(copyHeaders: Boolean) {
    it.property("copyHeaders", copyHeaders.toString())
  }

  /**
   * To use custom host header for producer. When not set in query will be ignored. When set will
   * override host header derived from url.
   */
  public fun customHostHeader(customHostHeader: String) {
    it.property("customHostHeader", customHostHeader)
  }

  /**
   * Whether the HTTP DELETE should include the message body or not. By default HTTP DELETE do not
   * include any HTTP body. However in some rare cases users may need to be able to include the message
   * body.
   */
  public fun deleteWithBody(deleteWithBody: String) {
    it.property("deleteWithBody", deleteWithBody)
  }

  /**
   * Whether the HTTP DELETE should include the message body or not. By default HTTP DELETE do not
   * include any HTTP body. However in some rare cases users may need to be able to include the message
   * body.
   */
  public fun deleteWithBody(deleteWithBody: Boolean) {
    it.property("deleteWithBody", deleteWithBody.toString())
  }

  /**
   * Whether to the HTTP request should follow redirects. By default the HTTP request does not
   * follow redirects
   */
  public fun followRedirects(followRedirects: String) {
    it.property("followRedirects", followRedirects)
  }

  /**
   * Whether to the HTTP request should follow redirects. By default the HTTP request does not
   * follow redirects
   */
  public fun followRedirects(followRedirects: Boolean) {
    it.property("followRedirects", followRedirects.toString())
  }

  /**
   * Whether the HTTP GET should include the message body or not. By default HTTP GET do not include
   * any HTTP body. However in some rare cases users may need to be able to include the message body.
   */
  public fun getWithBody(getWithBody: String) {
    it.property("getWithBody", getWithBody)
  }

  /**
   * Whether the HTTP GET should include the message body or not. By default HTTP GET do not include
   * any HTTP body. However in some rare cases users may need to be able to include the message body.
   */
  public fun getWithBody(getWithBody: Boolean) {
    it.property("getWithBody", getWithBody.toString())
  }

  /**
   * If this option is true, The http producer won't read response body and cache the input stream
   */
  public fun ignoreResponseBody(ignoreResponseBody: String) {
    it.property("ignoreResponseBody", ignoreResponseBody)
  }

  /**
   * If this option is true, The http producer won't read response body and cache the input stream
   */
  public fun ignoreResponseBody(ignoreResponseBody: Boolean) {
    it.property("ignoreResponseBody", ignoreResponseBody.toString())
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
   * The status codes which are considered a success response. The values are inclusive. Multiple
   * ranges can be defined, separated by comma, e.g. 200-204,209,301-304. Each range must be a single
   * number or from-to with the dash included.
   */
  public fun okStatusCodeRange(okStatusCodeRange: String) {
    it.property("okStatusCodeRange", okStatusCodeRange)
  }

  /**
   * If the option is true, HttpProducer will set the Host header to the value contained in the
   * current exchange Host header, useful in reverse proxy applications where you want the Host header
   * received by the downstream server to reflect the URL called by the upstream client, this allows
   * applications which use the Host header to generate accurate URL's for a proxied service
   */
  public fun preserveHostHeader(preserveHostHeader: String) {
    it.property("preserveHostHeader", preserveHostHeader)
  }

  /**
   * If the option is true, HttpProducer will set the Host header to the value contained in the
   * current exchange Host header, useful in reverse proxy applications where you want the Host header
   * received by the downstream server to reflect the URL called by the upstream client, this allows
   * applications which use the Host header to generate accurate URL's for a proxied service
   */
  public fun preserveHostHeader(preserveHostHeader: Boolean) {
    it.property("preserveHostHeader", preserveHostHeader.toString())
  }

  /**
   * To set a custom HTTP User-Agent request header
   */
  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
  }

  /**
   * Provide access to the http client request parameters used on new RequestConfig instances used
   * by producers or consumers of this endpoint.
   */
  public fun clientBuilder(clientBuilder: String) {
    it.property("clientBuilder", clientBuilder)
  }

  /**
   * To use a custom HttpClientConnectionManager to manage connections
   */
  public fun clientConnectionManager(clientConnectionManager: String) {
    it.property("clientConnectionManager", clientConnectionManager)
  }

  /**
   * The maximum number of connections per route.
   */
  public fun connectionsPerRoute(connectionsPerRoute: String) {
    it.property("connectionsPerRoute", connectionsPerRoute)
  }

  /**
   * The maximum number of connections per route.
   */
  public fun connectionsPerRoute(connectionsPerRoute: Int) {
    it.property("connectionsPerRoute", connectionsPerRoute.toString())
  }

  /**
   * Sets a custom HttpClient to be used by the producer
   */
  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  /**
   * Register a custom configuration strategy for new HttpClient instances created by producers or
   * consumers such as to configure authentication mechanisms etc.
   */
  public fun httpClientConfigurer(httpClientConfigurer: String) {
    it.property("httpClientConfigurer", httpClientConfigurer)
  }

  /**
   * To configure the HttpClient using the key/values from the Map.
   */
  public fun httpClientOptions(httpClientOptions: String) {
    it.property("httpClientOptions", httpClientOptions)
  }

  /**
   * To configure the connection and the socket using the key/values from the Map.
   */
  public fun httpConnectionOptions(httpConnectionOptions: String) {
    it.property("httpConnectionOptions", httpConnectionOptions)
  }

  /**
   * To use a custom HttpContext instance
   */
  public fun httpContext(httpContext: String) {
    it.property("httpContext", httpContext)
  }

  /**
   * The maximum number of connections.
   */
  public fun maxTotalConnections(maxTotalConnections: String) {
    it.property("maxTotalConnections", maxTotalConnections)
  }

  /**
   * The maximum number of connections.
   */
  public fun maxTotalConnections(maxTotalConnections: Int) {
    it.property("maxTotalConnections", maxTotalConnections.toString())
  }

  /**
   * To use System Properties as fallback for configuration
   */
  public fun useSystemProperties(useSystemProperties: String) {
    it.property("useSystemProperties", useSystemProperties)
  }

  /**
   * To use System Properties as fallback for configuration
   */
  public fun useSystemProperties(useSystemProperties: Boolean) {
    it.property("useSystemProperties", useSystemProperties.toString())
  }

  /**
   * Proxy authentication domain to use with NTML
   */
  public fun proxyAuthDomain(proxyAuthDomain: String) {
    it.property("proxyAuthDomain", proxyAuthDomain)
  }

  /**
   * Proxy authentication host
   */
  public fun proxyAuthHost(proxyAuthHost: String) {
    it.property("proxyAuthHost", proxyAuthHost)
  }

  /**
   * Proxy authentication method to use
   */
  public fun proxyAuthMethod(proxyAuthMethod: String) {
    it.property("proxyAuthMethod", proxyAuthMethod)
  }

  /**
   * Proxy authentication domain (workstation name) to use with NTML
   */
  public fun proxyAuthNtHost(proxyAuthNtHost: String) {
    it.property("proxyAuthNtHost", proxyAuthNtHost)
  }

  /**
   * Proxy authentication password
   */
  public fun proxyAuthPassword(proxyAuthPassword: String) {
    it.property("proxyAuthPassword", proxyAuthPassword)
  }

  /**
   * Proxy authentication port
   */
  public fun proxyAuthPort(proxyAuthPort: String) {
    it.property("proxyAuthPort", proxyAuthPort)
  }

  /**
   * Proxy authentication port
   */
  public fun proxyAuthPort(proxyAuthPort: Int) {
    it.property("proxyAuthPort", proxyAuthPort.toString())
  }

  /**
   * Proxy authentication scheme to use
   */
  public fun proxyAuthScheme(proxyAuthScheme: String) {
    it.property("proxyAuthScheme", proxyAuthScheme)
  }

  /**
   * Proxy authentication username
   */
  public fun proxyAuthUsername(proxyAuthUsername: String) {
    it.property("proxyAuthUsername", proxyAuthUsername)
  }

  /**
   * Proxy hostname to use
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * Proxy port to use
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * Proxy port to use
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * Authentication domain to use with NTML
   */
  public fun authDomain(authDomain: String) {
    it.property("authDomain", authDomain)
  }

  /**
   * If this option is true, camel-http sends preemptive basic authentication to the server.
   */
  public fun authenticationPreemptive(authenticationPreemptive: String) {
    it.property("authenticationPreemptive", authenticationPreemptive)
  }

  /**
   * If this option is true, camel-http sends preemptive basic authentication to the server.
   */
  public fun authenticationPreemptive(authenticationPreemptive: Boolean) {
    it.property("authenticationPreemptive", authenticationPreemptive.toString())
  }

  /**
   * Authentication host to use with NTML
   */
  public fun authHost(authHost: String) {
    it.property("authHost", authHost)
  }

  /**
   * Authentication methods allowed to use as a comma separated list of values Basic, Digest or
   * NTLM.
   */
  public fun authMethod(authMethod: String) {
    it.property("authMethod", authMethod)
  }

  /**
   * Which authentication method to prioritize to use, either as Basic, Digest or NTLM.
   */
  public fun authMethodPriority(authMethodPriority: String) {
    it.property("authMethodPriority", authMethodPriority)
  }

  /**
   * Authentication password
   */
  public fun authPassword(authPassword: String) {
    it.property("authPassword", authPassword)
  }

  /**
   * Authentication username
   */
  public fun authUsername(authUsername: String) {
    it.property("authUsername", authUsername)
  }

  /**
   * OAuth2 client id
   */
  public fun oauth2ClientId(oauth2ClientId: String) {
    it.property("oauth2ClientId", oauth2ClientId)
  }

  /**
   * OAuth2 client secret
   */
  public fun oauth2ClientSecret(oauth2ClientSecret: String) {
    it.property("oauth2ClientSecret", oauth2ClientSecret)
  }

  /**
   * OAuth2 scope
   */
  public fun oauth2Scope(oauth2Scope: String) {
    it.property("oauth2Scope", oauth2Scope)
  }

  /**
   * OAuth2 Token endpoint
   */
  public fun oauth2TokenEndpoint(oauth2TokenEndpoint: String) {
    it.property("oauth2TokenEndpoint", oauth2TokenEndpoint)
  }

  /**
   * To configure security using SSLContextParameters. Important: Only one instance of
   * org.apache.camel.util.jsse.SSLContextParameters is supported per HttpComponent. If you need to use
   * 2 or more different instances, you need to define a new HttpComponent per instance you need.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * To use a custom X509HostnameVerifier such as DefaultHostnameVerifier or NoopHostnameVerifier
   */
  public fun x509HostnameVerifier(x509HostnameVerifier: String) {
    it.property("x509HostnameVerifier", x509HostnameVerifier)
  }
}
