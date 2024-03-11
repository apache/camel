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
 * Expose gRPC endpoints and access external gRPC endpoints.
 */
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

  /**
   * The gRPC server host name. This is localhost or 0.0.0.0 when being a consumer or remote server
   * host name when using producer.
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port/$service")
  }

  /**
   * The gRPC local or remote server port
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port/$service")
  }

  /**
   * The gRPC local or remote server port
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port/$service")
  }

  /**
   * Fully qualified service name from the protocol buffer descriptor file (package dot service
   * definition name)
   */
  public fun service(service: String) {
    this.service = service
    it.url("$host:$port/$service")
  }

  /**
   * The HTTP/2 flow control window size (MiB)
   */
  public fun flowControlWindow(flowControlWindow: String) {
    it.property("flowControlWindow", flowControlWindow)
  }

  /**
   * The HTTP/2 flow control window size (MiB)
   */
  public fun flowControlWindow(flowControlWindow: Int) {
    it.property("flowControlWindow", flowControlWindow.toString())
  }

  /**
   * The maximum message size allowed to be received/sent (MiB)
   */
  public fun maxMessageSize(maxMessageSize: String) {
    it.property("maxMessageSize", maxMessageSize)
  }

  /**
   * The maximum message size allowed to be received/sent (MiB)
   */
  public fun maxMessageSize(maxMessageSize: Int) {
    it.property("maxMessageSize", maxMessageSize.toString())
  }

  /**
   * Setting the autoDiscoverServerInterceptors mechanism, if true, the component will look for a
   * ServerInterceptor instance in the registry automatically otherwise it will skip that checking.
   */
  public fun autoDiscoverServerInterceptors(autoDiscoverServerInterceptors: String) {
    it.property("autoDiscoverServerInterceptors", autoDiscoverServerInterceptors)
  }

  /**
   * Setting the autoDiscoverServerInterceptors mechanism, if true, the component will look for a
   * ServerInterceptor instance in the registry automatically otherwise it will skip that checking.
   */
  public fun autoDiscoverServerInterceptors(autoDiscoverServerInterceptors: Boolean) {
    it.property("autoDiscoverServerInterceptors", autoDiscoverServerInterceptors.toString())
  }

  /**
   * This option specifies the top-level strategy for processing service requests and responses in
   * streaming mode. If an aggregation strategy is selected, all requests will be accumulated in the
   * list, then transferred to the flow, and the accumulated responses will be sent to the sender. If a
   * propagation strategy is selected, request is sent to the stream, and the response will be
   * immediately sent back to the sender. If a delegation strategy is selected, request is sent to the
   * stream, but no response generated under the assumption that all necessary responses will be sent
   * at another part of route. Delegation strategy always comes with routeControlledStreamObserver=true
   * to be able to achieve the assumption.
   */
  public fun consumerStrategy(consumerStrategy: String) {
    it.property("consumerStrategy", consumerStrategy)
  }

  /**
   * Determines if onCompleted events should be pushed to the Camel route.
   */
  public fun forwardOnCompleted(forwardOnCompleted: String) {
    it.property("forwardOnCompleted", forwardOnCompleted)
  }

  /**
   * Determines if onCompleted events should be pushed to the Camel route.
   */
  public fun forwardOnCompleted(forwardOnCompleted: Boolean) {
    it.property("forwardOnCompleted", forwardOnCompleted.toString())
  }

  /**
   * Determines if onError events should be pushed to the Camel route. Exceptions will be set as
   * message body.
   */
  public fun forwardOnError(forwardOnError: String) {
    it.property("forwardOnError", forwardOnError)
  }

  /**
   * Determines if onError events should be pushed to the Camel route. Exceptions will be set as
   * message body.
   */
  public fun forwardOnError(forwardOnError: Boolean) {
    it.property("forwardOnError", forwardOnError.toString())
  }

  /**
   * The maximum number of concurrent calls permitted for each incoming server connection
   */
  public fun maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection: String) {
    it.property("maxConcurrentCallsPerConnection", maxConcurrentCallsPerConnection)
  }

  /**
   * The maximum number of concurrent calls permitted for each incoming server connection
   */
  public fun maxConcurrentCallsPerConnection(maxConcurrentCallsPerConnection: Int) {
    it.property("maxConcurrentCallsPerConnection", maxConcurrentCallsPerConnection.toString())
  }

  /**
   * Lets the route to take control over stream observer. If this value is set to true, then the
   * response observer of gRPC call will be set with the name GrpcConstants.GRPC_RESPONSE_OBSERVER in
   * the Exchange object. Please note that the stream observer's onNext(), onError(), onCompleted()
   * methods should be called in the route.
   */
  public fun routeControlledStreamObserver(routeControlledStreamObserver: String) {
    it.property("routeControlledStreamObserver", routeControlledStreamObserver)
  }

  /**
   * Lets the route to take control over stream observer. If this value is set to true, then the
   * response observer of gRPC call will be set with the name GrpcConstants.GRPC_RESPONSE_OBSERVER in
   * the Exchange object. Please note that the stream observer's onNext(), onError(), onCompleted()
   * methods should be called in the route.
   */
  public fun routeControlledStreamObserver(routeControlledStreamObserver: Boolean) {
    it.property("routeControlledStreamObserver", routeControlledStreamObserver.toString())
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
   * Setting the autoDiscoverClientInterceptors mechanism, if true, the component will look for a
   * ClientInterceptor instance in the registry automatically otherwise it will skip that checking.
   */
  public fun autoDiscoverClientInterceptors(autoDiscoverClientInterceptors: String) {
    it.property("autoDiscoverClientInterceptors", autoDiscoverClientInterceptors)
  }

  /**
   * Setting the autoDiscoverClientInterceptors mechanism, if true, the component will look for a
   * ClientInterceptor instance in the registry automatically otherwise it will skip that checking.
   */
  public fun autoDiscoverClientInterceptors(autoDiscoverClientInterceptors: Boolean) {
    it.property("autoDiscoverClientInterceptors", autoDiscoverClientInterceptors.toString())
  }

  /**
   * Copies exchange properties from original exchange to all exchanges created for route defined by
   * streamRepliesTo.
   */
  public fun inheritExchangePropertiesForReplies(inheritExchangePropertiesForReplies: String) {
    it.property("inheritExchangePropertiesForReplies", inheritExchangePropertiesForReplies)
  }

  /**
   * Copies exchange properties from original exchange to all exchanges created for route defined by
   * streamRepliesTo.
   */
  public fun inheritExchangePropertiesForReplies(inheritExchangePropertiesForReplies: Boolean) {
    it.property("inheritExchangePropertiesForReplies",
        inheritExchangePropertiesForReplies.toString())
  }

  /**
   * gRPC method name
   */
  public fun method(method: String) {
    it.property("method", method)
  }

  /**
   * The mode used to communicate with a remote gRPC server. In SIMPLE mode a single exchange is
   * translated into a remote procedure call. In STREAMING mode all exchanges will be sent within the
   * same request (input and output of the recipient gRPC service must be of type 'stream').
   */
  public fun producerStrategy(producerStrategy: String) {
    it.property("producerStrategy", producerStrategy)
  }

  /**
   * When using STREAMING client mode, it indicates the endpoint where responses should be
   * forwarded.
   */
  public fun streamRepliesTo(streamRepliesTo: String) {
    it.property("streamRepliesTo", streamRepliesTo)
  }

  /**
   * Expects that exchange property GrpcConstants.GRPC_RESPONSE_OBSERVER is set. Takes its value and
   * calls onNext, onError and onComplete on that StreamObserver. All other gRPC parameters are
   * ignored.
   */
  public fun toRouteControlledStreamObserver(toRouteControlledStreamObserver: String) {
    it.property("toRouteControlledStreamObserver", toRouteControlledStreamObserver)
  }

  /**
   * Expects that exchange property GrpcConstants.GRPC_RESPONSE_OBSERVER is set. Takes its value and
   * calls onNext, onError and onComplete on that StreamObserver. All other gRPC parameters are
   * ignored.
   */
  public fun toRouteControlledStreamObserver(toRouteControlledStreamObserver: Boolean) {
    it.property("toRouteControlledStreamObserver", toRouteControlledStreamObserver.toString())
  }

  /**
   * The user agent header passed to the server
   */
  public fun userAgent(userAgent: String) {
    it.property("userAgent", userAgent)
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
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  /**
   * Sets whether synchronous processing should be strictly used
   */
  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  /**
   * Authentication method type in advance to the SSL/TLS negotiation
   */
  public fun authenticationType(authenticationType: String) {
    it.property("authenticationType", authenticationType)
  }

  /**
   * JSON Web Token sign algorithm
   */
  public fun jwtAlgorithm(jwtAlgorithm: String) {
    it.property("jwtAlgorithm", jwtAlgorithm)
  }

  /**
   * JSON Web Token issuer
   */
  public fun jwtIssuer(jwtIssuer: String) {
    it.property("jwtIssuer", jwtIssuer)
  }

  /**
   * JSON Web Token secret
   */
  public fun jwtSecret(jwtSecret: String) {
    it.property("jwtSecret", jwtSecret)
  }

  /**
   * JSON Web Token subject
   */
  public fun jwtSubject(jwtSubject: String) {
    it.property("jwtSubject", jwtSubject)
  }

  /**
   * The X.509 certificate chain file resource in PEM format link
   */
  public fun keyCertChainResource(keyCertChainResource: String) {
    it.property("keyCertChainResource", keyCertChainResource)
  }

  /**
   * The PKCS#8 private key file password
   */
  public fun keyPassword(keyPassword: String) {
    it.property("keyPassword", keyPassword)
  }

  /**
   * The PKCS#8 private key file resource in PEM format link
   */
  public fun keyResource(keyResource: String) {
    it.property("keyResource", keyResource)
  }

  /**
   * Identifies the security negotiation type used for HTTP/2 communication
   */
  public fun negotiationType(negotiationType: String) {
    it.property("negotiationType", negotiationType)
  }

  /**
   * Service Account key file in JSON format resource link supported by the Google Cloud SDK
   */
  public fun serviceAccountResource(serviceAccountResource: String) {
    it.property("serviceAccountResource", serviceAccountResource)
  }

  /**
   * The trusted certificates collection file resource in PEM format for verifying the remote
   * endpoint's certificate
   */
  public fun trustCertCollectionResource(trustCertCollectionResource: String) {
    it.property("trustCertCollectionResource", trustCertCollectionResource)
  }
}
