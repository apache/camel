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
 * Get, set, delete or watch keys in etcd key-value store.
 */
public fun UriDsl.etcd3(i: Etcd3UriDsl.() -> Unit) {
  Etcd3UriDsl(this).apply(i)
}

@CamelDslMarker
public class Etcd3UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("etcd3")
  }

  private var path: String = ""

  /**
   * The path the endpoint refers to
   */
  public fun path(path: String) {
    this.path = path
    it.url("$path")
  }

  /**
   * Configure etcd server endpoints using the IPNameResolver. Multiple endpoints can be separated
   * by comma.
   */
  public fun endpoints(endpoints: String) {
    it.property("endpoints", endpoints)
  }

  /**
   * Configure the charset to use for the keys.
   */
  public fun keyCharset(keyCharset: String) {
    it.property("keyCharset", keyCharset)
  }

  /**
   * Configure the namespace of keys used. / will be treated as no namespace.
   */
  public fun namespace(namespace: String) {
    it.property("namespace", namespace)
  }

  /**
   * To apply an action on all the key-value pairs whose key that starts with the target path.
   */
  public fun prefix(prefix: String) {
    it.property("prefix", prefix)
  }

  /**
   * To apply an action on all the key-value pairs whose key that starts with the target path.
   */
  public fun prefix(prefix: Boolean) {
    it.property("prefix", prefix.toString())
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
   * The index to watch from
   */
  public fun fromIndex(fromIndex: String) {
    it.property("fromIndex", fromIndex)
  }

  /**
   * The index to watch from
   */
  public fun fromIndex(fromIndex: Int) {
    it.property("fromIndex", fromIndex.toString())
  }

  /**
   * Configure the charset to use for the values.
   */
  public fun valueCharset(valueCharset: String) {
    it.property("valueCharset", valueCharset)
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
   * Configure the headers to be added to auth request headers.
   */
  public fun authHeaders(authHeaders: String) {
    it.property("authHeaders", authHeaders)
  }

  /**
   * Configure the authority used to authenticate connections to servers.
   */
  public fun authority(authority: String) {
    it.property("authority", authority)
  }

  /**
   * Configure the connection timeout.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Configure the headers to be added to http request headers.
   */
  public fun headers(headers: String) {
    it.property("headers", headers)
  }

  /**
   * Configure the interval for gRPC keepalives. The current minimum allowed by gRPC is 10 seconds.
   */
  public fun keepAliveTime(keepAliveTime: String) {
    it.property("keepAliveTime", keepAliveTime)
  }

  /**
   * Configure the timeout for gRPC keepalives.
   */
  public fun keepAliveTimeout(keepAliveTimeout: String) {
    it.property("keepAliveTimeout", keepAliveTimeout)
  }

  /**
   * Configure etcd load balancer policy.
   */
  public fun loadBalancerPolicy(loadBalancerPolicy: String) {
    it.property("loadBalancerPolicy", loadBalancerPolicy)
  }

  /**
   * Configure the maximum message size allowed for a single gRPC frame.
   */
  public fun maxInboundMessageSize(maxInboundMessageSize: String) {
    it.property("maxInboundMessageSize", maxInboundMessageSize)
  }

  /**
   * Configure the maximum message size allowed for a single gRPC frame.
   */
  public fun maxInboundMessageSize(maxInboundMessageSize: Int) {
    it.property("maxInboundMessageSize", maxInboundMessageSize.toString())
  }

  /**
   * Configure the delay between retries in milliseconds.
   */
  public fun retryDelay(retryDelay: String) {
    it.property("retryDelay", retryDelay)
  }

  /**
   * Configure the delay between retries in milliseconds.
   */
  public fun retryDelay(retryDelay: Int) {
    it.property("retryDelay", retryDelay.toString())
  }

  /**
   * Configure the max backing off delay between retries in milliseconds.
   */
  public fun retryMaxDelay(retryMaxDelay: String) {
    it.property("retryMaxDelay", retryMaxDelay)
  }

  /**
   * Configure the max backing off delay between retries in milliseconds.
   */
  public fun retryMaxDelay(retryMaxDelay: Int) {
    it.property("retryMaxDelay", retryMaxDelay.toString())
  }

  /**
   * Configure the retries max duration.
   */
  public fun retryMaxDuration(retryMaxDuration: String) {
    it.property("retryMaxDuration", retryMaxDuration)
  }

  /**
   * The path to look for service discovery.
   */
  public fun servicePath(servicePath: String) {
    it.property("servicePath", servicePath)
  }

  /**
   * Configure etcd auth password.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Configure SSL/TLS context to use instead of the system default.
   */
  public fun sslContext(sslContext: String) {
    it.property("sslContext", sslContext)
  }

  /**
   * Configure etcd auth user.
   */
  public fun userName(userName: String) {
    it.property("userName", userName)
  }
}
