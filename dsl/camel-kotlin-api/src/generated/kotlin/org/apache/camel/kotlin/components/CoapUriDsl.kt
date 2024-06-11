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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Send and receive messages to/from CoAP (Constrained Application Protocol) capable devices.
 */
public fun UriDsl.coap(i: CoapUriDsl.() -> Unit) {
  CoapUriDsl(this).apply(i)
}

@CamelDslMarker
public class CoapUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("coap")
  }

  private var uri: String = ""

  /**
   * The URI for the CoAP endpoint
   */
  public fun uri(uri: String) {
    this.uri = uri
    it.url("$uri")
  }

  /**
   * Comma separated list of methods that the CoAP consumer will bind to. The default is to bind to
   * all methods (DELETE, GET, POST, PUT).
   */
  public fun coapMethodRestrict(coapMethodRestrict: String) {
    it.property("coapMethodRestrict", coapMethodRestrict)
  }

  /**
   * Make CoAP resource observable for source endpoint, based on RFC 7641.
   */
  public fun observable(observable: String) {
    it.property("observable", observable)
  }

  /**
   * Make CoAP resource observable for source endpoint, based on RFC 7641.
   */
  public fun observable(observable: Boolean) {
    it.property("observable", observable.toString())
  }

  /**
   * Send an observe request from a source endpoint, based on RFC 7641.
   */
  public fun observe(observe: String) {
    it.property("observe", observe)
  }

  /**
   * Send an observe request from a source endpoint, based on RFC 7641.
   */
  public fun observe(observe: Boolean) {
    it.property("observe", observe.toString())
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
   * Notify observers that the resource of this URI has changed, based on RFC 7641. Use this flag on
   * a destination endpoint, with a URI that matches an existing source endpoint URI.
   */
  public fun notify(notify: String) {
    it.property("notify", notify)
  }

  /**
   * Notify observers that the resource of this URI has changed, based on RFC 7641. Use this flag on
   * a destination endpoint, with a URI that matches an existing source endpoint URI.
   */
  public fun notify(notify: Boolean) {
    it.property("notify", notify.toString())
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
   * Set the AdvancedCertificateVerifier to use to determine trust in raw public keys.
   */
  public fun advancedCertificateVerifier(advancedCertificateVerifier: String) {
    it.property("advancedCertificateVerifier", advancedCertificateVerifier)
  }

  /**
   * Set the AdvancedPskStore to use for pre-shared key.
   */
  public fun advancedPskStore(advancedPskStore: String) {
    it.property("advancedPskStore", advancedPskStore)
  }

  /**
   * Sets the alias used to query the KeyStore for the private key and certificate. This parameter
   * is used when we are enabling TLS with certificates on the service side, and similarly on the
   * client side when TLS is used with certificates and client authentication. If the parameter is not
   * specified then the default behavior is to use the first alias in the keystore that contains a key
   * entry. This configuration parameter does not apply to configuring TLS via a Raw Public Key or a
   * Pre-Shared Key.
   */
  public fun alias(alias: String) {
    it.property("alias", alias)
  }

  /**
   * Sets the cipherSuites String. This is a comma separated String of ciphersuites to configure. If
   * it is not specified, then it falls back to getting the ciphersuites from the sslContextParameters
   * object.
   */
  public fun cipherSuites(cipherSuites: String) {
    it.property("cipherSuites", cipherSuites)
  }

  /**
   * Sets the configuration options for server-side client-authentication requirements. The value
   * must be one of NONE, WANT, REQUIRE. If this value is not specified, then it falls back to checking
   * the sslContextParameters.getServerParameters().getClientAuthentication() value.
   */
  public fun clientAuthentication(clientAuthentication: String) {
    it.property("clientAuthentication", clientAuthentication)
  }

  /**
   * Set the configured private key for use with Raw Public Key.
   */
  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  /**
   * Set the configured public key for use with Raw Public Key.
   */
  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }

  /**
   * The CBC cipher suites are not recommended. If you want to use them, you first need to set the
   * recommendedCipherSuitesOnly option to false.
   */
  public fun recommendedCipherSuitesOnly(recommendedCipherSuitesOnly: String) {
    it.property("recommendedCipherSuitesOnly", recommendedCipherSuitesOnly)
  }

  /**
   * The CBC cipher suites are not recommended. If you want to use them, you first need to set the
   * recommendedCipherSuitesOnly option to false.
   */
  public fun recommendedCipherSuitesOnly(recommendedCipherSuitesOnly: Boolean) {
    it.property("recommendedCipherSuitesOnly", recommendedCipherSuitesOnly.toString())
  }

  /**
   * Set the SSLContextParameters object for setting up TLS. This is required for coapstcp, and for
   * coaps when we are using certificates for TLS (as opposed to RPK or PKS).
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }
}
