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
 * Integrate with Consul service discovery and configuration store.
 */
public fun UriDsl.consul(i: ConsulUriDsl.() -> Unit) {
  ConsulUriDsl(this).apply(i)
}

@CamelDslMarker
public class ConsulUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("consul")
  }

  private var apiEndpoint: String = ""

  /**
   * The API endpoint
   */
  public fun apiEndpoint(apiEndpoint: String) {
    this.apiEndpoint = apiEndpoint
    it.url("$apiEndpoint")
  }

  /**
   * Connect timeout for OkHttpClient
   */
  public fun connectTimeout(connectTimeout: String) {
    it.property("connectTimeout", connectTimeout)
  }

  /**
   * Reference to a org.kiwiproject.consul.Consul in the registry.
   */
  public fun consulClient(consulClient: String) {
    it.property("consulClient", consulClient)
  }

  /**
   * The default key. Can be overridden by CamelConsulKey
   */
  public fun key(key: String) {
    it.property("key", key)
  }

  /**
   * Configure if the AgentClient should attempt a ping before returning the Consul instance
   */
  public fun pingInstance(pingInstance: String) {
    it.property("pingInstance", pingInstance)
  }

  /**
   * Configure if the AgentClient should attempt a ping before returning the Consul instance
   */
  public fun pingInstance(pingInstance: Boolean) {
    it.property("pingInstance", pingInstance.toString())
  }

  /**
   * Read timeout for OkHttpClient
   */
  public fun readTimeout(readTimeout: String) {
    it.property("readTimeout", readTimeout)
  }

  /**
   * Set tags. You can separate multiple tags by comma.
   */
  public fun tags(tags: String) {
    it.property("tags", tags)
  }

  /**
   * The Consul agent URL
   */
  public fun url(url: String) {
    it.property("url", url)
  }

  /**
   * Write timeout for OkHttpClient
   */
  public fun writeTimeout(writeTimeout: String) {
    it.property("writeTimeout", writeTimeout)
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
   * The default action. Can be overridden by CamelConsulAction
   */
  public fun action(action: String) {
    it.property("action", action)
  }

  /**
   * Default to transform values retrieved from Consul i.e. on KV endpoint to string.
   */
  public fun valueAsString(valueAsString: String) {
    it.property("valueAsString", valueAsString)
  }

  /**
   * Default to transform values retrieved from Consul i.e. on KV endpoint to string.
   */
  public fun valueAsString(valueAsString: Boolean) {
    it.property("valueAsString", valueAsString.toString())
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
   * The consistencyMode used for queries, default ConsistencyMode.DEFAULT
   */
  public fun consistencyMode(consistencyMode: String) {
    it.property("consistencyMode", consistencyMode)
  }

  /**
   * The data center
   */
  public fun datacenter(datacenter: String) {
    it.property("datacenter", datacenter)
  }

  /**
   * The near node to use for queries.
   */
  public fun nearNode(nearNode: String) {
    it.property("nearNode", nearNode)
  }

  /**
   * The note meta-data to use for queries.
   */
  public fun nodeMeta(nodeMeta: String) {
    it.property("nodeMeta", nodeMeta)
  }

  /**
   * Sets the ACL token to be used with Consul
   */
  public fun aclToken(aclToken: String) {
    it.property("aclToken", aclToken)
  }

  /**
   * Sets the password to be used for basic authentication
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * SSL configuration using an org.apache.camel.support.jsse.SSLContextParameters instance.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * Sets the username to be used for basic authentication
   */
  public fun userName(userName: String) {
    it.property("userName", userName)
  }

  /**
   * The second to wait for a watch event, default 10 seconds
   */
  public fun blockSeconds(blockSeconds: String) {
    it.property("blockSeconds", blockSeconds)
  }

  /**
   * The second to wait for a watch event, default 10 seconds
   */
  public fun blockSeconds(blockSeconds: Int) {
    it.property("blockSeconds", blockSeconds.toString())
  }

  /**
   * The first index for watch for, default 0
   */
  public fun firstIndex(firstIndex: String) {
    it.property("firstIndex", firstIndex)
  }

  /**
   * Recursively watch, default false
   */
  public fun recursive(recursive: String) {
    it.property("recursive", recursive)
  }

  /**
   * Recursively watch, default false
   */
  public fun recursive(recursive: Boolean) {
    it.property("recursive", recursive.toString())
  }
}
