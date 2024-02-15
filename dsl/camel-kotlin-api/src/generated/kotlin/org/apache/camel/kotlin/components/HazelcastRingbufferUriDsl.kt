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

public fun UriDsl.`hazelcast-ringbuffer`(i: HazelcastRingbufferUriDsl.() -> Unit) {
  HazelcastRingbufferUriDsl(this).apply(i)
}

@CamelDslMarker
public class HazelcastRingbufferUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("hazelcast-ringbuffer")
  }

  private var cacheName: String = ""

  public fun cacheName(cacheName: String) {
    this.cacheName = cacheName
    it.url("$cacheName")
  }

  public fun defaultOperation(defaultOperation: String) {
    it.property("defaultOperation", defaultOperation)
  }

  public fun hazelcastConfigUri(hazelcastConfigUri: String) {
    it.property("hazelcastConfigUri", hazelcastConfigUri)
  }

  public fun hazelcastInstance(hazelcastInstance: String) {
    it.property("hazelcastInstance", hazelcastInstance)
  }

  public fun hazelcastInstanceName(hazelcastInstanceName: String) {
    it.property("hazelcastInstanceName", hazelcastInstanceName)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
