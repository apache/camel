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
 * Perform operations on the Milvus Vector Database.
 */
public fun UriDsl.milvus(i: MilvusUriDsl.() -> Unit) {
  MilvusUriDsl(this).apply(i)
}

@CamelDslMarker
public class MilvusUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("milvus")
  }

  private var collection: String = ""

  /**
   * The collection Name
   */
  public fun collection(collection: String) {
    this.collection = collection
    it.url("$collection")
  }

  /**
   * The host to connect to.
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * The port to connect to.
   */
  public fun port(port: String) {
    it.property("port", port)
  }

  /**
   * The port to connect to.
   */
  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  /**
   * Sets a default timeout for all requests
   */
  public fun timeout(timeout: String) {
    it.property("timeout", timeout)
  }

  /**
   * Sets a default timeout for all requests
   */
  public fun timeout(timeout: Int) {
    it.property("timeout", timeout.toString())
  }

  /**
   * Sets the API key to use for authentication
   */
  public fun token(token: String) {
    it.property("token", token)
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
