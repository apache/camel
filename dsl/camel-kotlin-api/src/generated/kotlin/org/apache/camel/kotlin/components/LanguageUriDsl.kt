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
 * Execute scripts in any of the languages supported by Camel.
 */
public fun UriDsl.language(i: LanguageUriDsl.() -> Unit) {
  LanguageUriDsl(this).apply(i)
}

@CamelDslMarker
public class LanguageUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("language")
  }

  private var languageName: String = ""

  private var resourceUri: String = ""

  /**
   * Sets the name of the language to use
   */
  public fun languageName(languageName: String) {
    this.languageName = languageName
    it.url("$languageName:$resourceUri")
  }

  /**
   * Path to the resource, or a reference to lookup a bean in the Registry to use as the resource
   */
  public fun resourceUri(resourceUri: String) {
    this.resourceUri = resourceUri
    it.url("$languageName:$resourceUri")
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
   * Whether the script is binary content or text content. By default the script is read as text
   * content (eg java.lang.String)
   */
  public fun binary(binary: String) {
    it.property("binary", binary)
  }

  /**
   * Whether the script is binary content or text content. By default the script is read as text
   * content (eg java.lang.String)
   */
  public fun binary(binary: Boolean) {
    it.property("binary", binary.toString())
  }

  /**
   * Whether to cache the compiled script and reuse Notice reusing the script can cause side effects
   * from processing one Camel org.apache.camel.Exchange to the next org.apache.camel.Exchange.
   */
  public fun cacheScript(cacheScript: String) {
    it.property("cacheScript", cacheScript)
  }

  /**
   * Whether to cache the compiled script and reuse Notice reusing the script can cause side effects
   * from processing one Camel org.apache.camel.Exchange to the next org.apache.camel.Exchange.
   */
  public fun cacheScript(cacheScript: Boolean) {
    it.property("cacheScript", cacheScript.toString())
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
   * Sets the class of the result type (type from output)
   */
  public fun resultType(resultType: String) {
    it.property("resultType", resultType)
  }

  /**
   * Sets the script to execute
   */
  public fun script(script: String) {
    it.property("script", script)
  }

  /**
   * Whether or not the result of the script should be used as message body. This options is default
   * true.
   */
  public fun transform(transform: String) {
    it.property("transform", transform)
  }

  /**
   * Whether or not the result of the script should be used as message body. This options is default
   * true.
   */
  public fun transform(transform: Boolean) {
    it.property("transform", transform.toString())
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
