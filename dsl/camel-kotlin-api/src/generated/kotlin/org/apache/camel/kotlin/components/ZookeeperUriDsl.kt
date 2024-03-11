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
 * Manage ZooKeeper clusters.
 */
public fun UriDsl.zookeeper(i: ZookeeperUriDsl.() -> Unit) {
  ZookeeperUriDsl(this).apply(i)
}

@CamelDslMarker
public class ZookeeperUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("zookeeper")
  }

  private var serverUrls: String = ""

  private var path: String = ""

  /**
   * The zookeeper server hosts (multiple servers can be separated by comma)
   */
  public fun serverUrls(serverUrls: String) {
    this.serverUrls = serverUrls
    it.url("$serverUrls/$path")
  }

  /**
   * The node in the ZooKeeper server (aka znode)
   */
  public fun path(path: String) {
    this.path = path
    it.url("$serverUrls/$path")
  }

  /**
   * Whether the children of the node should be listed
   */
  public fun listChildren(listChildren: String) {
    it.property("listChildren", listChildren)
  }

  /**
   * Whether the children of the node should be listed
   */
  public fun listChildren(listChildren: Boolean) {
    it.property("listChildren", listChildren.toString())
  }

  /**
   * The time interval to wait on connection before timing out.
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * The time interval to wait on connection before timing out.
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * The time interval to backoff for after an error before retrying.
   */
  public fun backoff(backoff: String) {
    it.property("backoff", backoff)
  }

  /**
   * The time interval to backoff for after an error before retrying.
   */
  public fun backoff(backoff: Int) {
    it.property("backoff", backoff.toString())
  }

  /**
   * Should changes to the znode be 'watched' and repeatedly processed.
   */
  public fun repeat(repeat: String) {
    it.property("repeat", repeat)
  }

  /**
   * Should changes to the znode be 'watched' and repeatedly processed.
   */
  public fun repeat(repeat: Boolean) {
    it.property("repeat", repeat.toString())
  }

  /**
   * Upon the delete of a znode, should an empty message be send to the consumer
   */
  public fun sendEmptyMessageOnDelete(sendEmptyMessageOnDelete: String) {
    it.property("sendEmptyMessageOnDelete", sendEmptyMessageOnDelete)
  }

  /**
   * Upon the delete of a znode, should an empty message be send to the consumer
   */
  public fun sendEmptyMessageOnDelete(sendEmptyMessageOnDelete: Boolean) {
    it.property("sendEmptyMessageOnDelete", sendEmptyMessageOnDelete.toString())
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
   * Should the endpoint create the node if it does not currently exist.
   */
  public fun create(create: String) {
    it.property("create", create)
  }

  /**
   * Should the endpoint create the node if it does not currently exist.
   */
  public fun create(create: Boolean) {
    it.property("create", create.toString())
  }

  /**
   * The create mode that should be used for the newly created node
   */
  public fun createMode(createMode: String) {
    it.property("createMode", createMode)
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
