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

public fun UriDsl.knative(i: KnativeUriDsl.() -> Unit) {
  KnativeUriDsl(this).apply(i)
}

@CamelDslMarker
public class KnativeUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("knative")
  }

  private var type: String = ""

  private var typeId: String = ""

  public fun type(type: String) {
    this.type = type
    it.url("$type/$typeId")
  }

  public fun typeId(typeId: String) {
    this.typeId = typeId
    it.url("$type/$typeId")
  }

  public fun ceOverride(ceOverride: String) {
    it.property("ceOverride", ceOverride)
  }

  public fun cloudEventsSpecVersion(cloudEventsSpecVersion: String) {
    it.property("cloudEventsSpecVersion", cloudEventsSpecVersion)
  }

  public fun cloudEventsType(cloudEventsType: String) {
    it.property("cloudEventsType", cloudEventsType)
  }

  public fun environment(environment: String) {
    it.property("environment", environment)
  }

  public fun filters(filters: String) {
    it.property("filters", filters)
  }

  public fun sinkBinding(sinkBinding: String) {
    it.property("sinkBinding", sinkBinding)
  }

  public fun transportOptions(transportOptions: String) {
    it.property("transportOptions", transportOptions)
  }

  public fun replyWithCloudEvent(replyWithCloudEvent: String) {
    it.property("replyWithCloudEvent", replyWithCloudEvent)
  }

  public fun replyWithCloudEvent(replyWithCloudEvent: Boolean) {
    it.property("replyWithCloudEvent", replyWithCloudEvent.toString())
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

  public fun reply(reply: String) {
    it.property("reply", reply)
  }

  public fun reply(reply: Boolean) {
    it.property("reply", reply.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  public fun kind(kind: String) {
    it.property("kind", kind)
  }

  public fun name(name: String) {
    it.property("name", name)
  }
}
