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
 * Manage Docker containers.
 */
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

  /**
   * Which operation to use
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * Email address associated with the user
   */
  public fun email(email: String) {
    it.property("email", email)
  }

  /**
   * Docker host
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * Docker port
   */
  public fun port(port: String) {
    it.property("port", port)
  }

  /**
   * Docker port
   */
  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  /**
   * Request timeout for response (in seconds)
   */
  public fun requestTimeout(requestTimeout: String) {
    it.property("requestTimeout", requestTimeout)
  }

  /**
   * Request timeout for response (in seconds)
   */
  public fun requestTimeout(requestTimeout: Int) {
    it.property("requestTimeout", requestTimeout.toString())
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
   * The fully qualified class name of the DockerCmdExecFactory implementation to use
   */
  public fun cmdExecFactory(cmdExecFactory: String) {
    it.property("cmdExecFactory", cmdExecFactory)
  }

  /**
   * Whether to follow redirect filter
   */
  public fun followRedirectFilter(followRedirectFilter: String) {
    it.property("followRedirectFilter", followRedirectFilter)
  }

  /**
   * Whether to follow redirect filter
   */
  public fun followRedirectFilter(followRedirectFilter: Boolean) {
    it.property("followRedirectFilter", followRedirectFilter.toString())
  }

  /**
   * Whether to use logging filter
   */
  public fun loggingFilter(loggingFilter: String) {
    it.property("loggingFilter", loggingFilter)
  }

  /**
   * Whether to use logging filter
   */
  public fun loggingFilter(loggingFilter: Boolean) {
    it.property("loggingFilter", loggingFilter.toString())
  }

  /**
   * Maximum route connections
   */
  public fun maxPerRouteConnections(maxPerRouteConnections: String) {
    it.property("maxPerRouteConnections", maxPerRouteConnections)
  }

  /**
   * Maximum route connections
   */
  public fun maxPerRouteConnections(maxPerRouteConnections: Int) {
    it.property("maxPerRouteConnections", maxPerRouteConnections.toString())
  }

  /**
   * Maximum total connections
   */
  public fun maxTotalConnections(maxTotalConnections: String) {
    it.property("maxTotalConnections", maxTotalConnections)
  }

  /**
   * Maximum total connections
   */
  public fun maxTotalConnections(maxTotalConnections: Int) {
    it.property("maxTotalConnections", maxTotalConnections.toString())
  }

  /**
   * Additional configuration parameters as key/value pairs
   */
  public fun parameters(parameters: String) {
    it.property("parameters", parameters)
  }

  /**
   * Server address for docker registry.
   */
  public fun serverAddress(serverAddress: String) {
    it.property("serverAddress", serverAddress)
  }

  /**
   * Socket connection mode
   */
  public fun socket(socket: String) {
    it.property("socket", socket)
  }

  /**
   * Socket connection mode
   */
  public fun socket(socket: Boolean) {
    it.property("socket", socket.toString())
  }

  /**
   * Location containing the SSL certificate chain
   */
  public fun certPath(certPath: String) {
    it.property("certPath", certPath)
  }

  /**
   * Password to authenticate with
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Use HTTPS communication
   */
  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  /**
   * Use HTTPS communication
   */
  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  /**
   * Check TLS
   */
  public fun tlsVerify(tlsVerify: String) {
    it.property("tlsVerify", tlsVerify)
  }

  /**
   * Check TLS
   */
  public fun tlsVerify(tlsVerify: Boolean) {
    it.property("tlsVerify", tlsVerify.toString())
  }

  /**
   * User name to authenticate with
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
