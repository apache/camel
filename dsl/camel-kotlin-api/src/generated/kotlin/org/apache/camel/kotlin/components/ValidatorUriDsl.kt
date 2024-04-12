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
 * Validate the payload using XML Schema and JAXP Validation.
 */
public fun UriDsl.validator(i: ValidatorUriDsl.() -> Unit) {
  ValidatorUriDsl(this).apply(i)
}

@CamelDslMarker
public class ValidatorUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("validator")
  }

  private var resourceUri: String = ""

  /**
   * URL to a local resource on the classpath, or a reference to lookup a bean in the Registry, or a
   * full URL to a remote resource or resource on the file system which contains the XSD to validate
   * against.
   */
  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$resourceUri")
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
   * To use a custom org.apache.camel.processor.validation.ValidatorErrorHandler. The default error
   * handler captures the errors and throws an exception.
   */
  public fun errorHandler(errorHandler: String) {
    it.property("errorHandler", errorHandler)
  }

  /**
   * To use a custom LSResourceResolver. Do not use together with resourceResolverFactory
   */
  public fun resourceResolver(resourceResolver: String) {
    it.property("resourceResolver", resourceResolver)
  }

  /**
   * To use a custom LSResourceResolver which depends on a dynamic endpoint resource URI. The
   * default resource resolver factory returns a resource resolver which can read files from the class
   * path and file system. Do not use together with resourceResolver.
   */
  public fun resourceResolverFactory(resourceResolverFactory: String) {
    it.property("resourceResolverFactory", resourceResolverFactory)
  }

  /**
   * To use a custom javax.xml.validation.SchemaFactory
   */
  public fun schemaFactory(schemaFactory: String) {
    it.property("schemaFactory", schemaFactory)
  }

  /**
   * Configures the W3C XML Schema Namespace URI.
   */
  public fun schemaLanguage(schemaLanguage: String) {
    it.property("schemaLanguage", schemaLanguage)
  }

  /**
   * Whether the Schema instance should be shared or not. This option is introduced to work around a
   * JDK 1.6.x bug. Xerces should not have this issue.
   */
  public fun useSharedSchema(useSharedSchema: String) {
    it.property("useSharedSchema", useSharedSchema)
  }

  /**
   * Whether the Schema instance should be shared or not. This option is introduced to work around a
   * JDK 1.6.x bug. Xerces should not have this issue.
   */
  public fun useSharedSchema(useSharedSchema: Boolean) {
    it.property("useSharedSchema", useSharedSchema.toString())
  }
}
