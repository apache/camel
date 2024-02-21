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

public fun UriDsl.qdrant(i: QdrantUriDsl.() -> Unit) {
  QdrantUriDsl(this).apply(i)
}

@CamelDslMarker
public class QdrantUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("qdrant")
  }

  private var collection: String = ""

  public fun collection(collection: String) {
    this.collection = collection
    it.url("$collection")
  }

  public fun apiKey(apiKey: String) {
    it.property("apiKey", apiKey)
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun port(port: String) {
    it.property("port", port)
  }

  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  public fun tls(tls: String) {
    it.property("tls", tls)
  }

  public fun tls(tls: Boolean) {
    it.property("tls", tls.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
