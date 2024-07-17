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
 * Validate JSON payloads using NetworkNT JSON Schema.
 */
public fun UriDsl.`json-validator`(i: JsonValidatorUriDsl.() -> Unit) {
  JsonValidatorUriDsl(this).apply(i)
}

@CamelDslMarker
public class JsonValidatorUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("json-validator")
  }

  private var resourceUri: String = ""

  /**
   * Path to the resource. You can prefix with: classpath, file, http, ref, or bean. classpath, file
   * and http loads the resource using these protocols (classpath is default). ref will lookup the
   * resource in the registry. bean will call a method on a bean to be used as the resource. For bean
   * you can specify the method name after dot, eg bean:myBean.myMethod.
   */
  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
  }

  /**
   * Sets whether the context map should allow access to all details. By default only the message
   * body and headers can be accessed. This option can be enabled for full access to the current
   * Exchange and CamelContext. Doing so impose a potential security risk as this opens access to the
   * full power of CamelContext API.
   */
  public fun allowContextMapAll(allowContextMapAll: String) {
    it.property("allowContextMapAll", allowContextMapAll)
  }

  /**
   * Sets whether the context map should allow access to all details. By default only the message
   * body and headers can be accessed. This option can be enabled for full access to the current
   * Exchange and CamelContext. Doing so impose a potential security risk as this opens access to the
   * full power of CamelContext API.
   */
  public fun allowContextMapAll(allowContextMapAll: Boolean) {
    it.property("allowContextMapAll", allowContextMapAll.toString())
  }

  /**
   * Sets whether to use resource content cache or not
   */
  public fun contentCache(contentCache: String) {
    it.property("contentCache", contentCache)
  }

  /**
   * Sets whether to use resource content cache or not
   */
  public fun contentCache(contentCache: Boolean) {
    it.property("contentCache", contentCache.toString())
  }

  /**
   * Whether to fail if no body exists.
   */
  public fun failOnNullBody(failOnNullBody: String) {
    it.property("failOnNullBody", failOnNullBody)
  }

  /**
   * Whether to fail if no body exists.
   */
  public fun failOnNullBody(failOnNullBody: Boolean) {
    it.property("failOnNullBody", failOnNullBody.toString())
  }

  /**
   * Whether to fail if no header exists when validating against a header.
   */
  public fun failOnNullHeader(failOnNullHeader: String) {
    it.property("failOnNullHeader", failOnNullHeader)
  }

  /**
   * Whether to fail if no header exists when validating against a header.
   */
  public fun failOnNullHeader(failOnNullHeader: Boolean) {
    it.property("failOnNullHeader", failOnNullHeader.toString())
  }

  /**
   * To validate against a header instead of the message body.
   */
  public fun headerName(headerName: String) {
    it.property("headerName", headerName)
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
   * Comma-separated list of Jackson DeserializationFeature enum values which will be disabled for
   * parsing exchange body
   */
  public fun disabledDeserializationFeatures(disabledDeserializationFeatures: String) {
    it.property("disabledDeserializationFeatures", disabledDeserializationFeatures)
  }

  /**
   * Comma-separated list of Jackson DeserializationFeature enum values which will be enabled for
   * parsing exchange body
   */
  public fun enabledDeserializationFeatures(enabledDeserializationFeatures: String) {
    it.property("enabledDeserializationFeatures", enabledDeserializationFeatures)
  }

  /**
   * To use a custom ValidatorErrorHandler. The default error handler captures the errors and throws
   * an exception.
   */
  public fun errorHandler(errorHandler: String) {
    it.property("errorHandler", errorHandler)
  }

  /**
   * The used Jackson object mapper
   */
  public fun objectMapper(objectMapper: String) {
    it.property("objectMapper", objectMapper)
  }

  /**
   * To use a custom schema loader allowing for adding custom format validation. The default
   * implementation will create a schema loader that tries to determine the schema version from the
   * $schema property of the specified schema.
   */
  public fun uriSchemaLoader(uriSchemaLoader: String) {
    it.property("uriSchemaLoader", uriSchemaLoader)
  }
}
