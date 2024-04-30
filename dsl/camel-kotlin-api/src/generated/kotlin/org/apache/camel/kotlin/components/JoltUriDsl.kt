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
 * JSON to JSON transformation using JOLT.
 */
public fun UriDsl.jolt(i: JoltUriDsl.() -> Unit) {
  JoltUriDsl(this).apply(i)
}

@CamelDslMarker
public class JoltUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("jolt")
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
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: String) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader)
  }

  /**
   * Whether to allow to use resource template from header or not (default false). Enabling this
   * allows to specify dynamic templates via message header. However this can be seen as a potential
   * security vulnerability if the header is coming from a malicious user, so use this with care.
   */
  public fun allowTemplateFromHeader(allowTemplateFromHeader: Boolean) {
    it.property("allowTemplateFromHeader", allowTemplateFromHeader.toString())
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
   * Specifies if the input is hydrated JSON or a JSON String.
   */
  public fun inputType(inputType: String) {
    it.property("inputType", inputType)
  }

  /**
   * Specifies if the output should be hydrated JSON or a JSON String.
   */
  public fun outputType(outputType: String) {
    it.property("outputType", outputType)
  }

  /**
   * Specifies the Transform DSL of the endpoint resource. If none is specified Chainr will be used.
   */
  public fun transformDsl(transformDsl: String) {
    it.property("transformDsl", transformDsl)
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
