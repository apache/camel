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

public fun UriDsl.docker(i: DockerUriDsl.() -> Unit) {
  DockerUriDsl(this).apply(i)
}

@CamelDslMarker
public class DockerUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("docker")
  }

  private var operation: String = ""

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun email(email: String) {
    it.property("email", email)
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun port(port: String) {
    it.property("port", port)
  }

  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
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

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun cmdExecFactory(cmdExecFactory: String) {
    it.property("cmdExecFactory", cmdExecFactory)
  }

  public fun followRedirectFilter(followRedirectFilter: String) {
    it.property("followRedirectFilter", followRedirectFilter)
  }

  public fun followRedirectFilter(followRedirectFilter: Boolean) {
    it.property("followRedirectFilter", followRedirectFilter.toString())
  }

  public fun loggingFilter(loggingFilter: String) {
    it.property("loggingFilter", loggingFilter)
  }

  public fun loggingFilter(loggingFilter: Boolean) {
    it.property("loggingFilter", loggingFilter.toString())
  }

  public fun maxPerRouteConnections(maxPerRouteConnections: String) {
    it.property("maxPerRouteConnections", maxPerRouteConnections)
  }

  public fun maxPerRouteConnections(maxPerRouteConnections: Int) {
    it.property("maxPerRouteConnections", maxPerRouteConnections.toString())
  }

  public fun maxTotalConnections(maxTotalConnections: String) {
    it.property("maxTotalConnections", maxTotalConnections)
  }

  public fun maxTotalConnections(maxTotalConnections: Int) {
    it.property("maxTotalConnections", maxTotalConnections.toString())
  }

  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }

  public fun serverAddress(serverAddress: String) {
    it.property("serverAddress", serverAddress)
  }

  public fun socket(socket: String) {
    it.property("socket", socket)
  }

  public fun socket(socket: Boolean) {
    it.property("socket", socket.toString())
  }

  public fun certPath(certPath: String) {
    it.property("certPath", certPath)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  public fun tlsVerify(tlsVerify: String) {
    it.property("tlsVerify", tlsVerify)
  }

  public fun tlsVerify(tlsVerify: Boolean) {
    it.property("tlsVerify", tlsVerify.toString())
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
