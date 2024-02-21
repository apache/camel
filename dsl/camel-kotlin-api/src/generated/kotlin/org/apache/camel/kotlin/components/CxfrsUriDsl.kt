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

public fun UriDsl.cxfrs(i: CxfrsUriDsl.() -> Unit) {
  CxfrsUriDsl(this).apply(i)
}

@CamelDslMarker
public class CxfrsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cxfrs")
  }

  private var beanId: String = ""

  private var address: String = ""

  public fun beanId(beanId: String) {
    this.beanId = beanId
    it.url("$beanId:$address")
  }

  public fun address(address: String) {
    this.address = address
    it.url("$beanId:$address")
  }

  public fun features(features: String) {
    it.property("features", features)
  }

  public fun loggingFeatureEnabled(loggingFeatureEnabled: String) {
    it.property("loggingFeatureEnabled", loggingFeatureEnabled)
  }

  public fun loggingFeatureEnabled(loggingFeatureEnabled: Boolean) {
    it.property("loggingFeatureEnabled", loggingFeatureEnabled.toString())
  }

  public fun loggingSizeLimit(loggingSizeLimit: String) {
    it.property("loggingSizeLimit", loggingSizeLimit)
  }

  public fun loggingSizeLimit(loggingSizeLimit: Int) {
    it.property("loggingSizeLimit", loggingSizeLimit.toString())
  }

  public fun modelRef(modelRef: String) {
    it.property("modelRef", modelRef)
  }

  public fun providers(providers: String) {
    it.property("providers", providers)
  }

  public fun resourceClasses(resourceClasses: String) {
    it.property("resourceClasses", resourceClasses)
  }

  public fun schemaLocations(schemaLocations: String) {
    it.property("schemaLocations", schemaLocations)
  }

  public fun skipFaultLogging(skipFaultLogging: String) {
    it.property("skipFaultLogging", skipFaultLogging)
  }

  public fun skipFaultLogging(skipFaultLogging: Boolean) {
    it.property("skipFaultLogging", skipFaultLogging.toString())
  }

  public fun bindingStyle(bindingStyle: String) {
    it.property("bindingStyle", bindingStyle)
  }

  public fun publishedEndpointUrl(publishedEndpointUrl: String) {
    it.property("publishedEndpointUrl", publishedEndpointUrl)
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

  public fun serviceBeans(serviceBeans: String) {
    it.property("serviceBeans", serviceBeans)
  }

  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  public fun hostnameVerifier(hostnameVerifier: String) {
    it.property("hostnameVerifier", hostnameVerifier)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: String) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure)
  }

  public fun throwExceptionOnFailure(throwExceptionOnFailure: Boolean) {
    it.property("throwExceptionOnFailure", throwExceptionOnFailure.toString())
  }

  public fun httpClientAPI(httpClientAPI: String) {
    it.property("httpClientAPI", httpClientAPI)
  }

  public fun httpClientAPI(httpClientAPI: Boolean) {
    it.property("httpClientAPI", httpClientAPI.toString())
  }

  public fun ignoreDeleteMethodMessageBody(ignoreDeleteMethodMessageBody: String) {
    it.property("ignoreDeleteMethodMessageBody", ignoreDeleteMethodMessageBody)
  }

  public fun ignoreDeleteMethodMessageBody(ignoreDeleteMethodMessageBody: Boolean) {
    it.property("ignoreDeleteMethodMessageBody", ignoreDeleteMethodMessageBody.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun maxClientCacheSize(maxClientCacheSize: String) {
    it.property("maxClientCacheSize", maxClientCacheSize)
  }

  public fun maxClientCacheSize(maxClientCacheSize: Int) {
    it.property("maxClientCacheSize", maxClientCacheSize.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  public fun bus(bus: String) {
    it.property("bus", bus)
  }

  public fun continuationTimeout(continuationTimeout: String) {
    it.property("continuationTimeout", continuationTimeout)
  }

  public fun cxfRsConfigurer(cxfRsConfigurer: String) {
    it.property("cxfRsConfigurer", cxfRsConfigurer)
  }

  public fun defaultBus(defaultBus: String) {
    it.property("defaultBus", defaultBus)
  }

  public fun defaultBus(defaultBus: Boolean) {
    it.property("defaultBus", defaultBus.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun performInvocation(performInvocation: String) {
    it.property("performInvocation", performInvocation)
  }

  public fun performInvocation(performInvocation: Boolean) {
    it.property("performInvocation", performInvocation.toString())
  }

  public fun propagateContexts(propagateContexts: String) {
    it.property("propagateContexts", propagateContexts)
  }

  public fun propagateContexts(propagateContexts: Boolean) {
    it.property("propagateContexts", propagateContexts.toString())
  }
}
