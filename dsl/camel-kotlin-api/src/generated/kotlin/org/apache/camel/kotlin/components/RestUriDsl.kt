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

  public fun method(method: String) {
    this.method = method
    it.url("$method:$path:$uriTemplate")
  }

  public fun path(path: String) {
    this.path = path
    it.url("$method:$path:$uriTemplate")
  }

  public fun uriTemplate(uriTemplate: String) {
    this.uriTemplate = uriTemplate
    it.url("$method:$path:$uriTemplate")
  }

  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  public fun inType(inType: String) {
    it.property("inType", inType)
  }

  public fun outType(outType: String) {
    it.property("outType", outType)
  }

  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  public fun routeId(routeId: String) {
    it.property("routeId", routeId)
  }

  public fun consumerComponentName(consumerComponentName: String) {
    it.property("consumerComponentName", consumerComponentName)
  }

  public fun description(description: String) {
    it.property("description", description)
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

  public fun apiDoc(apiDoc: String) {
    it.property("apiDoc", apiDoc)
  }

  public fun bindingMode(bindingMode: String) {
    it.property("bindingMode", bindingMode)
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun producerComponentName(producerComponentName: String) {
    it.property("producerComponentName", producerComponentName)
  }

  public fun queryParameters(queryParameters: String) {
    it.property("queryParameters", queryParameters)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
