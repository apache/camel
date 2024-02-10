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

  public fun serverUrls(serverUrls: String) {
    this.serverUrls = serverUrls
    it.url("$serverUrls/$path")
  }

  public fun path(path: String) {
    this.path = path
    it.url("$serverUrls/$path")
  }

  public fun listChildren(listChildren: String) {
    it.property("listChildren", listChildren)
  }

  public fun listChildren(listChildren: Boolean) {
    it.property("listChildren", listChildren.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  public fun backoff(backoff: String) {
    it.property("backoff", backoff)
  }

  public fun backoff(backoff: Int) {
    it.property("backoff", backoff.toString())
  }

  public fun repeat(repeat: String) {
    it.property("repeat", repeat)
  }

  public fun repeat(repeat: Boolean) {
    it.property("repeat", repeat.toString())
  }

  public fun sendEmptyMessageOnDelete(sendEmptyMessageOnDelete: String) {
    it.property("sendEmptyMessageOnDelete", sendEmptyMessageOnDelete)
  }

  public fun sendEmptyMessageOnDelete(sendEmptyMessageOnDelete: Boolean) {
    it.property("sendEmptyMessageOnDelete", sendEmptyMessageOnDelete.toString())
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

  public fun create(create: String) {
    it.property("create", create)
  }

  public fun create(create: Boolean) {
    it.property("create", create.toString())
  }

  public fun createMode(createMode: String) {
    it.property("createMode", createMode)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
