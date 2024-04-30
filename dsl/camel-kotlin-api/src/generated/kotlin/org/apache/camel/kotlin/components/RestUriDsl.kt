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
 * Expose REST services or call external REST services.
 */
public fun UriDsl.rest(i: RestUriDsl.() -> Unit) {
  RestUriDsl(this).apply(i)
}

@CamelDslMarker
public class RestUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("rest")
  }

  private var method: String = ""

  private var path: String = ""

  private var uriTemplate: String = ""

  /**
   * HTTP method to use.
   */
  public fun method(method: String) {
    this.method = method
    it.url("$method:$path:$uriTemplate")
  }

  /**
   * The base path, can use &#42; as path suffix to support wildcard HTTP route matching.
   */
  public fun path(path: String) {
    this.path = path
    it.url("$method:$path:$uriTemplate")
  }

  /**
   * The uri template
   */
  public fun uriTemplate(uriTemplate: String) {
    this.uriTemplate = uriTemplate
    it.url("$method:$path:$uriTemplate")
  }

  /**
   * Media type such as: 'text/xml', or 'application/json' this REST service accepts. By default we
   * accept all kinds of types.
   */
  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  /**
   * To declare the incoming POJO binding type as a FQN class name
   */
  public fun inType(inType: String) {
    it.property("inType", inType)
  }

  /**
   * To declare the outgoing POJO binding type as a FQN class name
   */
  public fun outType(outType: String) {
    it.property("outType", outType)
  }

  /**
   * Media type such as: 'text/xml', or 'application/json' this REST service returns.
   */
  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  /**
   * Name of the route this REST services creates
   */
  public fun routeId(routeId: String) {
    it.property("routeId", routeId)
  }

  /**
   * The Camel Rest component to use for the consumer REST transport, such as jetty, servlet,
   * undertow. If no component has been explicitly configured, then Camel will lookup if there is a
   * Camel component that integrates with the Rest DSL, or if a
   * org.apache.camel.spi.RestConsumerFactory is registered in the registry. If either one is found,
   * then that is being used.
   */
  public fun consumerComponentName(consumerComponentName: String) {
    it.property("consumerComponentName", consumerComponentName)
  }

  /**
   * Human description to document this REST service
   */
  public fun description(description: String) {
    it.property("description", description)
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
   * The openapi api doc resource to use. The resource is loaded from classpath by default and must
   * be in JSON format.
   */
  public fun apiDoc(apiDoc: String) {
    it.property("apiDoc", apiDoc)
  }

  /**
   * Configures the binding mode for the producer. If set to anything other than 'off' the producer
   * will try to convert the body of the incoming message from inType to the json or xml, and the
   * response from json or xml to outType.
   */
  public fun bindingMode(bindingMode: String) {
    it.property("bindingMode", bindingMode)
  }

  /**
   * Host and port of HTTP service to use (override host in openapi schema)
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * The Camel Rest component to use for the producer REST transport, such as http, undertow. If no
   * component has been explicitly configured, then Camel will lookup if there is a Camel component
   * that integrates with the Rest DSL, or if a org.apache.camel.spi.RestProducerFactory is registered
   * in the registry. If either one is found, then that is being used.
   */
  public fun producerComponentName(producerComponentName: String) {
    it.property("producerComponentName", producerComponentName)
  }

  /**
   * Query parameters for the HTTP service to call. The query parameters can contain multiple
   * parameters separated by ampersand such such as foo=123&bar=456.
   */
  public fun queryParameters(queryParameters: String) {
    it.property("queryParameters", queryParameters)
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
