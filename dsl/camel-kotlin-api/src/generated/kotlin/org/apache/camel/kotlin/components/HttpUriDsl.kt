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

  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("//$httpUri")
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

  public fun bridgeEndpoint(bridgeEndpoint: String) {
    it.property("bridgeEndpoint", bridgeEndpoint)
  }

  public fun bridgeEndpoint(bridgeEndpoint: Boolean) {
    it.property("bridgeEndpoint", bridgeEndpoint.toString())
  }

  public fun connectionClose(connectionClose: String) {
    it.property("connectionClose", connectionClose)
  }

  public fun connectionClose(connectionClose: Boolean) {
    it.property("connectionClose", connectionClose.toString())
  }

  public fun httpMethod(httpMethod: String) {
    it.property("httpMethod", httpMethod)
  }

  public fun skipRequestHeaders(skipRequestHeaders: String) {
    it.property("skipRequestHeaders", skipRequestHeaders)
  }

  public fun skipRequestHeaders(skipRequestHeaders: Boolean) {
    it.property("skipRequestHeaders", skipRequestHeaders.toString())
  }

  public fun skipResponseHeaders(skipResponseHeaders: String) {
    it.property("skipResponseHeaders", skipResponseHeaders)
  }

  public fun skipResponseHeaders(skipResponseHeaders: Boolean) {
    it.property("skipResponseHeaders", skipResponseHeaders.toString())
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  public fun clearExpiredCookies(clearExpiredCookies: String) {
    it.property("clearExpiredCookies", clearExpiredCookies)
  }

  public fun clearExpiredCookies(clearExpiredCookies: Boolean) {
    it.property("clearExpiredCookies", clearExpiredCookies.toString())
  }

  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  public fun cookieStore(cookieStore: String) {
    it.property("cookieStore", cookieStore)
  }

  public fun copyHeaders(copyHeaders: String) {
    it.property("copyHeaders", copyHeaders)
  }

  public fun copyHeaders(copyHeaders: Boolean) {
    it.property("copyHeaders", copyHeaders.toString())
  }

  public fun customHostHeader(customHostHeader: String) {
    it.property("customHostHeader", customHostHeader)
  }

  public fun deleteWithBody(deleteWithBody: String) {
    it.property("deleteWithBody", deleteWithBody)
  }

  public fun deleteWithBody(deleteWithBody: Boolean) {
    it.property("deleteWithBody", deleteWithBody.toString())
  }

  public fun followRedirects(followRedirects: String) {
    it.property("followRedirects", followRedirects)
  }

  public fun followRedirects(followRedirects: Boolean) {
    it.property("followRedirects", followRedirects.toString())
  }

  public fun getWithBody(getWithBody: String) {
    it.property("getWithBody", getWithBody)
  }

  public fun getWithBody(getWithBody: Boolean) {
    it.property("getWithBody", getWithBody.toString())
  }

  public fun ignoreResponseBody(ignoreResponseBody: String) {
    it.property("ignoreResponseBody", ignoreResponseBody)
  }

  public fun ignoreResponseBody(ignoreResponseBody: Boolean) {
    it.property("ignoreResponseBody", ignoreResponseBody.toString())
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

  public fun preserveHostHeader(preserveHostHeader: String) {
    it.property("preserveHostHeader", preserveHostHeader)
  }

  public fun preserveHostHeader(preserveHostHeader: Boolean) {
    it.property("preserveHostHeader", preserveHostHeader.toString())
  }

  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
  }

  public fun clientBuilder(clientBuilder: String) {
    it.property("clientBuilder", clientBuilder)
  }

  public fun clientConnectionManager(clientConnectionManager: String) {
    it.property("clientConnectionManager", clientConnectionManager)
  }

  public fun connectionsPerRoute(connectionsPerRoute: String) {
    it.property("connectionsPerRoute", connectionsPerRoute)
  }

  public fun connectionsPerRoute(connectionsPerRoute: Int) {
    it.property("connectionsPerRoute", connectionsPerRoute.toString())
  }

  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  public fun httpClientConfigurer(httpClientConfigurer: String) {
    it.property("httpClientConfigurer", httpClientConfigurer)
  }

  public fun httpClientOptions(httpClientOptions: String) {
    it.property("httpClientOptions", httpClientOptions)
  }

  public fun httpConnectionOptions(httpConnectionOptions: String) {
    it.property("httpConnectionOptions", httpConnectionOptions)
  }

  public fun httpContext(httpContext: String) {
    it.property("httpContext", httpContext)
  }

  public fun maxTotalConnections(maxTotalConnections: String) {
    it.property("maxTotalConnections", maxTotalConnections)
  }

  public fun maxTotalConnections(maxTotalConnections: Int) {
    it.property("maxTotalConnections", maxTotalConnections.toString())
  }

  public fun useSystemProperties(useSystemProperties: String) {
    it.property("useSystemProperties", useSystemProperties)
  }

  public fun useSystemProperties(useSystemProperties: Boolean) {
    it.property("useSystemProperties", useSystemProperties.toString())
  }

  public fun proxyAuthDomain(proxyAuthDomain: String) {
    it.property("proxyAuthDomain", proxyAuthDomain)
  }

  public fun proxyAuthHost(proxyAuthHost: String) {
    it.property("proxyAuthHost", proxyAuthHost)
  }

  public fun proxyAuthMethod(proxyAuthMethod: String) {
    it.property("proxyAuthMethod", proxyAuthMethod)
  }

  public fun proxyAuthNtHost(proxyAuthNtHost: String) {
    it.property("proxyAuthNtHost", proxyAuthNtHost)
  }

  public fun proxyAuthPassword(proxyAuthPassword: String) {
    it.property("proxyAuthPassword", proxyAuthPassword)
  }

  public fun proxyAuthPort(proxyAuthPort: String) {
    it.property("proxyAuthPort", proxyAuthPort)
  }

  public fun proxyAuthPort(proxyAuthPort: Int) {
    it.property("proxyAuthPort", proxyAuthPort.toString())
  }

  public fun proxyAuthScheme(proxyAuthScheme: String) {
    it.property("proxyAuthScheme", proxyAuthScheme)
  }

  public fun proxyAuthUsername(proxyAuthUsername: String) {
    it.property("proxyAuthUsername", proxyAuthUsername)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  public fun authDomain(authDomain: String) {
    it.property("authDomain", authDomain)
  }

  public fun authenticationPreemptive(authenticationPreemptive: String) {
    it.property("authenticationPreemptive", authenticationPreemptive)
  }

  public fun authenticationPreemptive(authenticationPreemptive: Boolean) {
    it.property("authenticationPreemptive", authenticationPreemptive.toString())
  }

  public fun authHost(authHost: String) {
    it.property("authHost", authHost)
  }

  public fun authMethod(authMethod: String) {
    it.property("authMethod", authMethod)
  }

  public fun authMethodPriority(authMethodPriority: String) {
    it.property("authMethodPriority", authMethodPriority)
  }

  public fun authPassword(authPassword: String) {
    it.property("authPassword", authPassword)
  }

  public fun authUsername(authUsername: String) {
    it.property("authUsername", authUsername)
  }

  public fun oauth2ClientId(oauth2ClientId: String) {
    it.property("oauth2ClientId", oauth2ClientId)
  }

  public fun oauth2ClientSecret(oauth2ClientSecret: String) {
    it.property("oauth2ClientSecret", oauth2ClientSecret)
  }

  public fun oauth2TokenEndpoint(oauth2TokenEndpoint: String) {
    it.property("oauth2TokenEndpoint", oauth2TokenEndpoint)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun x509HostnameVerifier(x509HostnameVerifier: String) {
    it.property("x509HostnameVerifier", x509HostnameVerifier)
  }
}
