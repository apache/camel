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
 * Process payments using Braintree Payments.
 */
public fun UriDsl.braintree(i: BraintreeUriDsl.() -> Unit) {
  BraintreeUriDsl(this).apply(i)
}

@CamelDslMarker
public class BraintreeUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("braintree")
  }

  private var apiName: String = ""

  private var methodName: String = ""

  /**
   * What kind of operation to perform
   */
  public fun apiName(apiName: String) {
    this.apiName = apiName
    it.url("$apiName/$methodName")
  }

  /**
   * What sub operation to use for the selected operation
   */
  public fun methodName(methodName: String) {
    this.methodName = methodName
    it.url("$apiName/$methodName")
  }

  /**
   * The environment Either SANDBOX or PRODUCTION
   */
  public fun environment(environment: String) {
    it.property("environment", environment)
  }

  /**
   * Sets the name of a parameter to be passed in the exchange In Body
   */
  public fun inBody(inBody: String) {
    it.property("inBody", inBody)
  }

  /**
   * The merchant id provided by Braintree.
   */
  public fun merchantId(merchantId: String) {
    it.property("merchantId", merchantId)
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
   * Set read timeout for http calls.
   */
  public fun httpReadTimeout(httpReadTimeout: String) {
    it.property("httpReadTimeout", httpReadTimeout)
  }

  /**
   * Set read timeout for http calls.
   */
  public fun httpReadTimeout(httpReadTimeout: Int) {
    it.property("httpReadTimeout", httpReadTimeout.toString())
  }

  /**
   * Set logging level for http calls, see java.util.logging.Level
   */
  public fun httpLogLevel(httpLogLevel: String) {
    it.property("httpLogLevel", httpLogLevel)
  }

  /**
   * Set log category to use to log http calls.
   */
  public fun httpLogName(httpLogName: String) {
    it.property("httpLogName", httpLogName)
  }

  /**
   * Sets whether to enable the BraintreeLogHandler. It may be desirable to set this to 'false'
   * where an existing JUL - SLF4J logger bridge is on the classpath. This option can also be
   * configured globally on the BraintreeComponent.
   */
  public fun logHandlerEnabled(logHandlerEnabled: String) {
    it.property("logHandlerEnabled", logHandlerEnabled)
  }

  /**
   * Sets whether to enable the BraintreeLogHandler. It may be desirable to set this to 'false'
   * where an existing JUL - SLF4J logger bridge is on the classpath. This option can also be
   * configured globally on the BraintreeComponent.
   */
  public fun logHandlerEnabled(logHandlerEnabled: Boolean) {
    it.property("logHandlerEnabled", logHandlerEnabled.toString())
  }

  /**
   * The proxy host
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * The proxy port
   */
  public fun proxyPort(proxyPort: String) {
    it.property("proxyPort", proxyPort)
  }

  /**
   * The proxy port
   */
  public fun proxyPort(proxyPort: Int) {
    it.property("proxyPort", proxyPort.toString())
  }

  /**
   * The access token granted by a merchant to another in order to process transactions on their
   * behalf. Used in place of environment, merchant id, public key and private key fields.
   */
  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  /**
   * The private key provided by Braintree.
   */
  public fun privateKey(privateKey: String) {
    it.property("privateKey", privateKey)
  }

  /**
   * The public key provided by Braintree.
   */
  public fun publicKey(publicKey: String) {
    it.property("publicKey", publicKey)
  }
}
