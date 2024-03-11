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

/**
 * The Dynamic Router control endpoint for operations that allow routing participants to subscribe
 * or unsubscribe to participate in dynamic message routing.
 */
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

  /**
   * Control action
   */
  public fun controlAction(controlAction: String) {
    this.controlAction = controlAction
    it.url("$controlAction")
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
   * The destination URI for exchanges that match.
   */
  public fun destinationUri(destinationUri: String) {
    it.property("destinationUri", destinationUri)
  }

  /**
   * The subscription predicate language.
   */
  public fun expressionLanguage(expressionLanguage: String) {
    it.property("expressionLanguage", expressionLanguage)
  }

  /**
   * The subscription predicate.
   */
  public fun predicate(predicate: String) {
    it.property("predicate", predicate)
  }

  /**
   * A Predicate instance in the registry.
   */
  public fun predicateBean(predicateBean: String) {
    it.property("predicateBean", predicateBean)
  }

  /**
   * The subscription priority.
   */
  public fun priority(priority: String) {
    it.property("priority", priority)
  }

  /**
   * The subscription priority.
   */
  public fun priority(priority: Int) {
    it.property("priority", priority.toString())
  }

  /**
   * The channel to subscribe to
   */
  public fun subscribeChannel(subscribeChannel: String) {
    it.property("subscribeChannel", subscribeChannel)
  }

  /**
   * The subscription ID; if unspecified, one will be assigned and returned.
   */
  public fun subscriptionId(subscriptionId: String) {
    it.property("subscriptionId", subscriptionId)
  }
}
