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

/**
 * Read and write from/to Infinispan distributed key/value store and data grid.
 */
public fun UriDsl.infinispan(i: InfinispanUriDsl.() -> Unit) {
  InfinispanUriDsl(this).apply(i)
}

@CamelDslMarker
public class InfinispanUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("infinispan")
  }

  private var cacheName: String = ""

  /**
   * The name of the cache to use. Use current to use the existing cache name from the currently
   * configured cached manager. Or use default for the default cache manager name.
   */
  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  /**
   * Specifies the host of the cache on Infinispan instance. Multiple hosts can be separated by
   * semicolon.
   */
  public fun hosts(hosts: String) {
    it.property("hosts", hosts)
  }

  /**
   * Specifies the query builder.
   */
  public fun queryBuilder(queryBuilder: String) {
    it.property("queryBuilder", queryBuilder)
  }

  /**
   * Returns the custom listener in use, if provided
   */
  public fun customListener(customListener: String) {
    it.property("customListener", customListener)
  }

  /**
   * Specifies the set of event types to register by the consumer.Multiple event can be separated by
   * comma. The possible event types are: CLIENT_CACHE_ENTRY_CREATED, CLIENT_CACHE_ENTRY_MODIFIED,
   * CLIENT_CACHE_ENTRY_REMOVED, CLIENT_CACHE_ENTRY_EXPIRED, CLIENT_CACHE_FAILOVER
   */
  public fun eventTypes(eventTypes: String) {
    it.property("eventTypes", eventTypes)
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
   * Set a specific default value for some producer operations
   */
  public fun defaultValue(defaultValue: String) {
    it.property("defaultValue", defaultValue)
  }

  /**
   * Set a specific key for producer operations
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * Set a specific old value for some producer operations
   */
  public fun oldValue(oldValue: String) {
    it.property("oldValue", oldValue)
  }

  /**
   * The operation to perform
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Set a specific value for producer operations
   */
  public fun `value`(`value`: String) {
    it.property("value", value)
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
   * Specifies the cache Container to connect
   */
  public fun cacheContainer(cacheContainer: String) {
    it.property("cacheContainer", cacheContainer)
  }

  /**
   * The CacheContainer configuration. Used if the cacheContainer is not defined.
   */
  public fun cacheContainerConfiguration(cacheContainerConfiguration: String) {
    it.property("cacheContainerConfiguration", cacheContainerConfiguration)
  }

  /**
   * Implementation specific properties for the CacheManager
   */
  public fun configurationProperties(configurationProperties: String) {
    it.property("configurationProperties", configurationProperties)
  }

  /**
   * An implementation specific URI for the CacheManager
   */
  public fun configurationUri(configurationUri: String) {
    it.property("configurationUri", configurationUri)
  }

  /**
   * A comma separated list of org.infinispan.client.hotrod.Flag to be applied by default on each
   * cache invocation.
   */
  public fun flags(flags: String) {
    it.property("flags", flags)
  }

  /**
   * Set a specific remappingFunction to use in a compute operation.
   */
  public fun remappingFunction(remappingFunction: String) {
    it.property("remappingFunction", remappingFunction)
  }

  /**
   * Store the operation result in a header instead of the message body. By default, resultHeader ==
   * null and the query result is stored in the message body, any existing content in the message body
   * is discarded. If resultHeader is set, the value is used as the name of the header to store the
   * query result and the original message body is preserved. This value can be overridden by an in
   * message header named: CamelInfinispanOperationResultHeader
   */
  public fun resultHeader(resultHeader: String) {
    it.property("resultHeader", resultHeader)
  }

  /**
   * Define the password to access the infinispan instance
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Define the SASL Mechanism to access the infinispan instance
   */
  public fun saslMechanism(saslMechanism: String) {
    it.property("saslMechanism", saslMechanism)
  }

  /**
   * Define if we are connecting to a secured Infinispan instance
   */
  public fun secure(secure: String) {
    it.property("secure", secure)
  }

  /**
   * Define if we are connecting to a secured Infinispan instance
   */
  public fun secure(secure: Boolean) {
    it.property("secure", secure.toString())
  }

  /**
   * Define the security realm to access the infinispan instance
   */
  public fun securityRealm(securityRealm: String) {
    it.property("securityRealm", securityRealm)
  }

  /**
   * Define the security server name to access the infinispan instance
   */
  public fun securityServerName(securityServerName: String) {
    it.property("securityServerName", securityServerName)
  }

  /**
   * Define the username to access the infinispan instance
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
