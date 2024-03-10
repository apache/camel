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
 * Manage posts and users using Wordpress API.
 */
public fun UriDsl.wordpress(i: WordpressUriDsl.() -> Unit) {
  WordpressUriDsl(this).apply(i)
}

@CamelDslMarker
public class WordpressUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("wordpress")
  }

  private var operation: String = ""

  private var operationDetail: String = ""

  /**
   * The endpoint operation.
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * The second part of an endpoint operation. Needed only when endpoint semantic is not enough,
   * like wordpress:post:delete
   */
  public fun operationDetail(operationDetail: String) {
    this.operationDetail = operationDetail
    it.url("$operation")
  }

  /**
   * The Wordpress REST API version
   */
  public fun apiVersion(apiVersion: String) {
    it.property("apiVersion", apiVersion)
  }

  /**
   * The criteria to use with complex searches.
   */
  public fun criteria(criteria: String) {
    it.property("criteria", criteria)
  }

  /**
   * Whether to bypass trash and force deletion.
   */
  public fun force(force: String) {
    it.property("force", force)
  }

  /**
   * Whether to bypass trash and force deletion.
   */
  public fun force(force: Boolean) {
    it.property("force", force.toString())
  }

  /**
   * The entity ID. Should be passed when the operation performed requires a specific entity, e.g.
   * deleting a post
   */
  public fun id(id: String) {
    it.property("id", id)
  }

  /**
   * The entity ID. Should be passed when the operation performed requires a specific entity, e.g.
   * deleting a post
   */
  public fun id(id: Int) {
    it.property("id", id.toString())
  }

  /**
   * Password from authorized user
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Search criteria
   */
  public fun searchCriteria(searchCriteria: String) {
    it.property("searchCriteria", searchCriteria)
  }

  /**
   * The Wordpress API URL from your site, e.g. http://myblog.com/wp-json/
   */
  public fun url(url: String) {
    it.property("url", url)
  }

  /**
   * Authorized user to perform writing operations
   */
  public fun user(user: String) {
    it.property("user", user)
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
}
