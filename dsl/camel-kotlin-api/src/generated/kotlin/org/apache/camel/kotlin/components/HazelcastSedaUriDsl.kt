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
 * Asynchronously send/receive Exchanges between Camel routes running on potentially distinct
 * JVMs/hosts backed by Hazelcast BlockingQueue.
 */
public fun UriDsl.`hazelcast-seda`(i: HazelcastSedaUriDsl.() -> Unit) {
  HazelcastSedaUriDsl(this).apply(i)
}

@CamelDslMarker
public class HazelcastSedaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hazelcast-seda")
  }

  private var cacheName: String = ""

  /**
   * The name of the cache
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * To specify a default operation to use, if no operation header has been provided.
   */
  public fun defaultOperation(defaultOperation: String) {
    it.property("defaultOperation", defaultOperation)
  }

  /**
   * Hazelcast configuration file.
   */
  public fun hazelcastConfigUri(hazelcastConfigUri: String) {
    it.property("hazelcastConfigUri", hazelcastConfigUri)
  }

  /**
   * The hazelcast instance reference which can be used for hazelcast endpoint.
   */
  public fun hazelcastInstance(hazelcastInstance: String) {
    it.property("hazelcastInstance", hazelcastInstance)
  }

  /**
   * The hazelcast instance reference name which can be used for hazelcast endpoint. If you don't
   * specify the instance reference, camel use the default hazelcast instance from the camel-hazelcast
   * instance.
   */
  public fun hazelcastInstanceName(hazelcastInstanceName: String) {
    it.property("hazelcastInstanceName", hazelcastInstanceName)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
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
   * To use concurrent consumers polling from the SEDA queue.
   */
  public fun concurrentConsumers(concurrentConsumers: String) {
    it.property("concurrentConsumers", concurrentConsumers)
  }

  /**
   * To use concurrent consumers polling from the SEDA queue.
   */
  public fun concurrentConsumers(concurrentConsumers: Int) {
    it.property("concurrentConsumers", concurrentConsumers.toString())
  }

  /**
   * Milliseconds before consumer continues polling after an error has occurred.
   */
  public fun onErrorDelay(onErrorDelay: String) {
    it.property("onErrorDelay", onErrorDelay)
  }

  /**
   * Milliseconds before consumer continues polling after an error has occurred.
   */
  public fun onErrorDelay(onErrorDelay: Int) {
    it.property("onErrorDelay", onErrorDelay.toString())
  }

  /**
   * The timeout used when consuming from the SEDA queue. When a timeout occurs, the consumer can
   * check whether it is allowed to continue running. Setting a lower value allows the consumer to
   * react more quickly upon shutdown.
   */
  public fun pollTimeout(pollTimeout: String) {
    it.property("pollTimeout", pollTimeout)
  }

  /**
   * The timeout used when consuming from the SEDA queue. When a timeout occurs, the consumer can
   * check whether it is allowed to continue running. Setting a lower value allows the consumer to
   * react more quickly upon shutdown.
   */
  public fun pollTimeout(pollTimeout: Int) {
    it.property("pollTimeout", pollTimeout.toString())
  }

  /**
   * If set to true then the consumer runs in transaction mode, where the messages in the seda queue
   * will only be removed if the transaction commits, which happens when the processing is complete.
   */
  public fun transacted(transacted: String) {
    it.property("transacted", transacted)
  }

  /**
   * If set to true then the consumer runs in transaction mode, where the messages in the seda queue
   * will only be removed if the transaction commits, which happens when the processing is complete.
   */
  public fun transacted(transacted: Boolean) {
    it.property("transacted", transacted.toString())
  }

  /**
   * If set to true the whole Exchange will be transfered. If header or body contains not
   * serializable objects, they will be skipped.
   */
  public fun transferExchange(transferExchange: String) {
    it.property("transferExchange", transferExchange)
  }

  /**
   * If set to true the whole Exchange will be transfered. If header or body contains not
   * serializable objects, they will be skipped.
   */
  public fun transferExchange(transferExchange: Boolean) {
    it.property("transferExchange", transferExchange.toString())
  }
}
