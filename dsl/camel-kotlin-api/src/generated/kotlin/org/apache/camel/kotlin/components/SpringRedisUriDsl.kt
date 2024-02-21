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

public fun UriDsl.`spring-redis`(i: SpringRedisUriDsl.() -> Unit) {
  SpringRedisUriDsl(this).apply(i)
}

@CamelDslMarker
public class SpringRedisUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("spring-redis")
  }

  private var host: String = ""

  private var port: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  public fun channels(channels: String) {
    it.property("channels", channels)
  }

  public fun command(command: String) {
    it.property("command", command)
  }

  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  public fun redisTemplate(redisTemplate: String) {
    it.property("redisTemplate", redisTemplate)
  }

  public fun serializer(serializer: String) {
    it.property("serializer", serializer)
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

  public fun listenerContainer(listenerContainer: String) {
    it.property("listenerContainer", listenerContainer)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
