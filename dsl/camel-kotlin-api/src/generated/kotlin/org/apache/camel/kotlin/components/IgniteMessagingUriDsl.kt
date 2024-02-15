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

public fun UriDsl.`ignite-messaging`(i: IgniteMessagingUriDsl.() -> Unit) {
  IgniteMessagingUriDsl(this).apply(i)
}

@CamelDslMarker
public class IgniteMessagingUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ignite-messaging")
  }

  private var topic: String = ""

  public fun topic(topic: String) {
    this.topic = topic
    it.url("$topic")
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: String) {
    it.property("propagateIncomingBodyIfNoReturnValue", propagateIncomingBodyIfNoReturnValue)
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: Boolean) {
    it.property("propagateIncomingBodyIfNoReturnValue",
        propagateIncomingBodyIfNoReturnValue.toString())
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: String) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects)
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: Boolean) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects.toString())
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

  public fun clusterGroupExpression(clusterGroupExpression: String) {
    it.property("clusterGroupExpression", clusterGroupExpression)
  }

  public fun sendMode(sendMode: String) {
    it.property("sendMode", sendMode)
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
