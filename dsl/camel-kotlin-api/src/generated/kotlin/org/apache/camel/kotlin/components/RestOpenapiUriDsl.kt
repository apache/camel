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
 * To call REST services using OpenAPI specification as contract.
 */
public fun UriDsl.`rest-openapi`(i: RestOpenapiUriDsl.() -> Unit) {
  RestOpenapiUriDsl(this).apply(i)
}

@CamelDslMarker
public class RestOpenapiUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("rest-openapi")
  }

  private var specificationUri: String = ""

  private var operationId: String = ""

  /**
   * Path to the OpenApi specification file. The scheme, host base path are taken from this
   * specification, but these can be overridden with properties on the component or endpoint level. If
   * not given the component tries to load openapi.json resource from the classpath. Note that the host
   * defined on the component and endpoint of this Component should contain the scheme, hostname and
   * optionally the port in the URI syntax (i.e. http://api.example.com:8080). Overrides component
   * configuration. The OpenApi specification can be loaded from different sources by prefixing with
   * file: classpath: http: https:. Support for https is limited to using the JDK installed UrlHandler,
   * and as such it can be cumbersome to setup TLS/SSL certificates for https (such as setting a number
   * of javax.net.ssl JVM system properties). How to do that consult the JDK documentation for
   * UrlHandler. Default value notice: By default loads openapi.json file
   */
  public fun specificationUri(specificationUri: String) {
    this.specificationUri = specificationUri
    it.url("$specificationUri#$operationId")
  }

  /**
   * ID of the operation from the OpenApi specification.
   */
  public fun operationId(operationId: String) {
    this.operationId = operationId
    it.url("$specificationUri#$operationId")
  }

  /**
   * API basePath, for example /v3. Default is unset, if set overrides the value present in OpenApi
   * specification and in the component configuration.
   */
  public fun basePath(basePath: String) {
    it.property("basePath", basePath)
  }

  /**
   * What payload type this component capable of consuming. Could be one type, like application/json
   * or multiple types as application/json, application/xml; q=0.5 according to the RFC7231. This
   * equates to the value of Accept HTTP header. If set overrides any value found in the OpenApi
   * specification and. in the component configuration
   */
  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  /**
   * Scheme hostname and port to direct the HTTP requests to in the form of https://hostname:port.
   * Can be configured at the endpoint, component or in the corresponding REST configuration in the
   * Camel Context. If you give this component a name (e.g. petstore) that REST configuration is
   * consulted first, rest-openapi next, and global configuration last. If set overrides any value
   * found in the OpenApi specification, RestConfiguration. Overrides all other configuration.
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * What payload type this component is producing. For example application/json according to the
   * RFC7231. This equates to the value of Content-Type HTTP header. If set overrides any value present
   * in the OpenApi specification. Overrides all other configuration.
   */
  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  /**
   * Enable validation of requests against the configured OpenAPI specification
   */
  public fun requestValidationEnabled(requestValidationEnabled: String) {
    it.property("requestValidationEnabled", requestValidationEnabled)
  }

  /**
   * Enable validation of requests against the configured OpenAPI specification
   */
  public fun requestValidationEnabled(requestValidationEnabled: Boolean) {
    it.property("requestValidationEnabled", requestValidationEnabled.toString())
  }

  /**
   * Name of the Camel component that will perform the requests. The component must be present in
   * Camel registry and it must implement RestProducerFactory service provider interface. If not set
   * CLASSPATH is searched for single component that implements RestProducerFactory SPI. Overrides
   * component configuration.
   */
  public fun componentName(componentName: String) {
    it.property("componentName", componentName)
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
   * If request validation is enabled, this option provides the capability to customize the creation
   * of OpenApiInteractionValidator used to validate requests.
   */
  public fun requestValidationCustomizer(requestValidationCustomizer: String) {
    it.property("requestValidationCustomizer", requestValidationCustomizer)
  }

  /**
   * Levels for specific OpenAPI request validation options. Multiple options can be specified as
   * URI options prefixed by 'validation.'. For example,
   * validation.request.body=ERROR&validation.request.body.unexpected=IGNORED. Supported values are
   * INFO, ERROR, WARN & IGNORE.
   */
  public fun requestValidationLevels(requestValidationLevels: String) {
    it.property("requestValidationLevels", requestValidationLevels)
  }
}
