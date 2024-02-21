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

public fun UriDsl.cxf(i: CxfUriDsl.() -> Unit) {
  CxfUriDsl(this).apply(i)
}

@CamelDslMarker
public class CxfUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cxf")
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

  public fun dataFormat(dataFormat: String) {
    it.property("dataFormat", dataFormat)
  }

  public fun wrappedStyle(wrappedStyle: String) {
    it.property("wrappedStyle", wrappedStyle)
  }

  public fun wrappedStyle(wrappedStyle: Boolean) {
    it.property("wrappedStyle", wrappedStyle.toString())
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

  public fun cookieHandler(cookieHandler: String) {
    it.property("cookieHandler", cookieHandler)
  }

  public fun defaultOperationName(defaultOperationName: String) {
    it.property("defaultOperationName", defaultOperationName)
  }

  public fun defaultOperationNamespace(defaultOperationNamespace: String) {
    it.property("defaultOperationNamespace", defaultOperationNamespace)
  }

  public fun hostnameVerifier(hostnameVerifier: String) {
    it.property("hostnameVerifier", hostnameVerifier)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun wrapped(wrapped: String) {
    it.property("wrapped", wrapped)
  }

  public fun wrapped(wrapped: Boolean) {
    it.property("wrapped", wrapped.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun synchronous(synchronous: String) {
    it.property("synchronous", synchronous)
  }

  public fun synchronous(synchronous: Boolean) {
    it.property("synchronous", synchronous.toString())
  }

  public fun allowStreaming(allowStreaming: String) {
    it.property("allowStreaming", allowStreaming)
  }

  public fun allowStreaming(allowStreaming: Boolean) {
    it.property("allowStreaming", allowStreaming.toString())
  }

  public fun bus(bus: String) {
    it.property("bus", bus)
  }

  public fun continuationTimeout(continuationTimeout: String) {
    it.property("continuationTimeout", continuationTimeout)
  }

  public fun cxfBinding(cxfBinding: String) {
    it.property("cxfBinding", cxfBinding)
  }

  public fun cxfConfigurer(cxfConfigurer: String) {
    it.property("cxfConfigurer", cxfConfigurer)
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

  public fun mergeProtocolHeaders(mergeProtocolHeaders: String) {
    it.property("mergeProtocolHeaders", mergeProtocolHeaders)
  }

  public fun mergeProtocolHeaders(mergeProtocolHeaders: Boolean) {
    it.property("mergeProtocolHeaders", mergeProtocolHeaders.toString())
  }

  public fun mtomEnabled(mtomEnabled: String) {
    it.property("mtomEnabled", mtomEnabled)
  }

  public fun mtomEnabled(mtomEnabled: Boolean) {
    it.property("mtomEnabled", mtomEnabled.toString())
  }

  public fun properties(properties: String) {
    it.property("properties", properties)
  }

  public fun schemaValidationEnabled(schemaValidationEnabled: String) {
    it.property("schemaValidationEnabled", schemaValidationEnabled)
  }

  public fun schemaValidationEnabled(schemaValidationEnabled: Boolean) {
    it.property("schemaValidationEnabled", schemaValidationEnabled.toString())
  }

  public fun skipPayloadMessagePartCheck(skipPayloadMessagePartCheck: String) {
    it.property("skipPayloadMessagePartCheck", skipPayloadMessagePartCheck)
  }

  public fun skipPayloadMessagePartCheck(skipPayloadMessagePartCheck: Boolean) {
    it.property("skipPayloadMessagePartCheck", skipPayloadMessagePartCheck.toString())
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

  public fun skipFaultLogging(skipFaultLogging: String) {
    it.property("skipFaultLogging", skipFaultLogging)
  }

  public fun skipFaultLogging(skipFaultLogging: Boolean) {
    it.property("skipFaultLogging", skipFaultLogging.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun bindingId(bindingId: String) {
    it.property("bindingId", bindingId)
  }

  public fun portName(portName: String) {
    it.property("portName", portName)
  }

  public fun publishedEndpointUrl(publishedEndpointUrl: String) {
    it.property("publishedEndpointUrl", publishedEndpointUrl)
  }

  public fun serviceClass(serviceClass: String) {
    it.property("serviceClass", serviceClass)
  }

  public fun serviceName(serviceName: String) {
    it.property("serviceName", serviceName)
  }

  public fun wsdlURL(wsdlURL: String) {
    it.property("wsdlURL", wsdlURL)
  }
}
