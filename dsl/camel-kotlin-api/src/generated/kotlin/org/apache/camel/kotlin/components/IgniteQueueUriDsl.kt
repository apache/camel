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
 * Interact with Ignite Queue data structures.
 */
public fun UriDsl.`ignite-queue`(i: IgniteQueueUriDsl.() -> Unit) {
  IgniteQueueUriDsl(this).apply(i)
}

@CamelDslMarker
public class IgniteQueueUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ignite-queue")
  }

  private var name: String = ""

  /**
   * The queue name.
   */
  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  /**
   * The queue capacity. Default: non-bounded.
   */
  public fun capacity(capacity: String) {
    it.property("capacity", capacity)
  }

  /**
   * The queue capacity. Default: non-bounded.
   */
  public fun capacity(capacity: Int) {
    it.property("capacity", capacity.toString())
  }

  /**
   * The collection configuration. Default: empty configuration. You can also conveniently set inner
   * properties by using configuration.xyz=123 options.
   */
  public fun configuration(configuration: String) {
    it.property("configuration", configuration)
  }

  /**
   * The operation to invoke on the Ignite Queue. Superseded by the
   * IgniteConstants.IGNITE_QUEUE_OPERATION header in the IN message. Possible values: CONTAINS, ADD,
   * SIZE, REMOVE, ITERATOR, CLEAR, RETAIN_ALL, ARRAY, DRAIN, ELEMENT, PEEK, OFFER, POLL, TAKE, PUT.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Sets whether to propagate the incoming body if the return type of the underlying Ignite
   * operation is void.
   */
  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: String) {
    it.property("propagateIncomingBodyIfNoReturnValue", propagateIncomingBodyIfNoReturnValue)
  }

  /**
   * Sets whether to propagate the incoming body if the return type of the underlying Ignite
   * operation is void.
   */
  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: Boolean) {
    it.property("propagateIncomingBodyIfNoReturnValue",
        propagateIncomingBodyIfNoReturnValue.toString())
  }

  /**
   * The queue timeout in milliseconds. Default: no timeout.
   */
  public fun timeoutMillis(timeoutMillis: String) {
    it.property("timeoutMillis", timeoutMillis)
  }

  /**
   * The queue timeout in milliseconds. Default: no timeout.
   */
  public fun timeoutMillis(timeoutMillis: Int) {
    it.property("timeoutMillis", timeoutMillis.toString())
  }

  /**
   * Sets whether to treat Collections as cache objects or as Collections of items to
   * insert/update/compute, etc.
   */
  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: String) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects)
  }

  /**
   * Sets whether to treat Collections as cache objects or as Collections of items to
   * insert/update/compute, etc.
   */
  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: Boolean) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects.toString())
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
