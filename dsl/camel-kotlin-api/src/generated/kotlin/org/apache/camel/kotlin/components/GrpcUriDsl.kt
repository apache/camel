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

public fun UriDsl.grpc(i: GrpcUriDsl.() -> Unit) {
  GrpcUriDsl(this).apply(i)
}

@CamelDslMarker
public class GrpcUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("grpc")
  }

  private var host: String = ""

  private var port: String = ""

  private var service: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$service")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$service")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$service")
  }

  public fun service(service: String) {
    this.service = service
    it.url("$host:$port/$service")
  }

  public fun flowControlWindow(flowControlWindow: String) {
    it.property("flowControlWindow", flowControlWindow)
  }

  public fun flowControlWindow(flowControlWindow: Int) {
    it.property("flowControlWindow", flowControlWindow.toString())
  }

  public fun maxMessageSize(maxMessageSize: String) {
    it.property("maxMessageSize", maxMessageSize)
  }

  public fun maxMessageSize(maxMessageSize: Int) {
    it.property("maxMessageSize", maxMessageSize.toString())
  }

  public fun autoDiscoverServerInterceptors(autoDiscoverServerInterceptors: String) {
    it.property("autoDiscoverServerInterceptors", autoDiscoverServerInterceptors)
  }

  public fun autoDiscoverServerInterceptors(autoDiscoverServerInterceptors: Boolean) {
    it.property("autoDiscoverServerInterceptors", autoDiscoverServerInterceptors.toString())
  }

  public fun consumerStrategy(consumerStrategy: String) {
    it.property("consumerStrategy", consumerStrategy)
  }

  public fun forwardOnCompleted(forwardOnCompleted: String) {
    it.property("forwardOnCompleted", forwardOnCompleted)
  }

  public fun forwardOnCompleted(forwardOnCompleted: Boolean) {
    it.property("forwardOnCompleted", forwardOnCompleted.toString())
  }

  public fun forwardOnError(forwardOnError: String) {
    it.property("forwardOnError", forwardOnError)
  }

  public fun forwardOnError(forwardOnError: Boolean) {
    it.property("forwardOnError", forwardOnError.toString())
  }

  public fun maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection: String) {
    it.property("maxConcurrentCallsPerConnection", maxConcurrentCallsPerConnection)
  }

  public fun maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection: Int) {
    it.property("maxConcurrentCallsPerConnection", maxConcurrentCallsPerConnection.toString())
  }

  public fun routeControlledStreamObserver(routeControlledStreamObserver: String) {
    it.property("routeControlledStreamObserver", routeControlledStreamObserver)
  }

  public fun routeControlledStreamObserver(routeControlledStreamObserver: Boolean) {
    it.property("routeControlledStreamObserver", routeControlledStreamObserver.toString())
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

  public fun autoDiscoverClientInterceptors(autoDiscoverClientInterceptors: String) {
    it.property("autoDiscoverClientInterceptors", autoDiscoverClientInterceptors)
  }

  public fun autoDiscoverClientInterceptors(autoDiscoverClientInterceptors: Boolean) {
    it.property("autoDiscoverClientInterceptors", autoDiscoverClientInterceptors.toString())
  }

  public fun inheritExchangePropertiesForReplies(inheritExchangePropertiesForReplies: String) {
    it.property("inheritExchangePropertiesForReplies", inheritExchangePropertiesForReplies)
  }

  public fun inheritExchangePropertiesForReplies(inheritExchangePropertiesForReplies: Boolean) {
    it.property("inheritExchangePropertiesForReplies",
        inheritExchangePropertiesForReplies.toString())
  }

  public fun method(method: String) {
    it.property("method", method)
  }

  public fun producerStrategy(producerStrategy: String) {
    it.property("producerStrategy", producerStrategy)
  }

  public fun streamRepliesTo(streamRepliesTo: String) {
    it.property("streamRepliesTo", streamRepliesTo)
  }

  public fun toRouteControlledStreamObserver(toRouteControlledStreamObserver: String) {
    it.property("toRouteControlledStreamObserver", toRouteControlledStreamObserver)
  }

  public fun toRouteControlledStreamObserver(toRouteControlledStreamObserver: Boolean) {
    it.property("toRouteControlledStreamObserver", toRouteControlledStreamObserver.toString())
  }

  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun authenticationType(authenticationType: String) {
    it.property("authenticationType", authenticationType)
  }

  public fun jwtAlgorithm(jwtAlgorithm: String) {
    it.property("jwtAlgorithm", jwtAlgorithm)
  }

  public fun jwtIssuer(jwtIssuer: String) {
    it.property("jwtIssuer", jwtIssuer)
  }

  public fun jwtSecret(jwtSecret: String) {
    it.property("jwtSecret", jwtSecret)
  }

  public fun jwtSubject(jwtSubject: String) {
    it.property("jwtSubject", jwtSubject)
  }

  public fun keyCertChainResource(keyCertChainResource: String) {
    it.property("keyCertChainResource", keyCertChainResource)
  }

  public fun keyPassword(keyPassword: String) {
    it.property("keyPassword", keyPassword)
  }

  public fun keyResource(keyResource: String) {
    it.property("keyResource", keyResource)
  }

  public fun negotiationType(negotiationType: String) {
    it.property("negotiationType", negotiationType)
  }

  public fun serviceAccountResource(serviceAccountResource: String) {
    it.property("serviceAccountResource", serviceAccountResource)
  }

  public fun trustCertCollectionResource(trustCertCollectionResource: String) {
    it.property("trustCertCollectionResource", trustCertCollectionResource)
  }
}
