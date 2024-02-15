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

public fun UriDsl.`dynamic-router-control`(i: DynamicRouterControlUriDsl.() -> Unit) {
  DynamicRouterControlUriDsl(this).apply(i)
}

@CamelDslMarker
public class DynamicRouterControlUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("dynamic-router-control")
  }

  private var controlAction: String = ""

  public fun controlAction(controlAction: String) {
    this.controlAction = controlAction
    it.url("$controlAction")
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun destinationUri(destinationUri: String) {
    it.property("destinationUri", destinationUri)
  }

  public fun expressionLanguage(expressionLanguage: String) {
    it.property("expressionLanguage", expressionLanguage)
  }

  public fun predicate(predicate: String) {
    it.property("predicate", predicate)
  }

  public fun predicateBean(predicateBean: String) {
    it.property("predicateBean", predicateBean)
  }

  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  public fun priority(priority: Int) {
    it.property("priority", priority.toString())
  }

  public fun subscribeChannel(subscribeChannel: String) {
    it.property("subscribeChannel", subscribeChannel)
  }

  public fun subscriptionId(subscriptionId: String) {
    it.property("subscriptionId", subscriptionId)
  }
}
