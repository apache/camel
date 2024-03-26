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
 * Manage Droplets and resources within the DigitalOcean cloud.
 */
public fun UriDsl.digitalocean(i: DigitaloceanUriDsl.() -> Unit) {
  DigitaloceanUriDsl(this).apply(i)
}

@CamelDslMarker
public class DigitaloceanUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("digitalocean")
  }

  private var operation: String = ""

  /**
   * The operation to perform to the given resource.
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * Use for pagination. Force the page number.
   */
  public fun page(page: String) {
    it.property("page", page)
  }

  /**
   * Use for pagination. Force the page number.
   */
  public fun page(page: Int) {
    it.property("page", page.toString())
  }

  /**
   * Use for pagination. Set the number of item per request. The maximum number of results per page
   * is 200.
   */
  public fun perPage(perPage: String) {
    it.property("perPage", perPage)
  }

  /**
   * Use for pagination. Set the number of item per request. The maximum number of results per page
   * is 200.
   */
  public fun perPage(perPage: Int) {
    it.property("perPage", perPage.toString())
  }

  /**
   * The DigitalOcean resource type on which perform the operation.
   */
  public fun resource(resource: String) {
    it.property("resource", resource)
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
   * To use a existing configured DigitalOceanClient as client
   */
  public fun digitalOceanClient(digitalOceanClient: String) {
    it.property("digitalOceanClient", digitalOceanClient)
  }

  /**
   * Set a proxy host if needed
   */
  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  /**
   * Set a proxy password if needed
   */
  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  /**
   * Set a proxy port if needed
   */
  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  /**
   * Set a proxy port if needed
   */
  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  /**
   * Set a proxy host if needed
   */
  public fun httpProxyUser(httpProxyUser: String) {
    it.property("httpProxyUser", httpProxyUser)
  }

  /**
   * DigitalOcean OAuth Token
   */
  public fun oAuthToken(oAuthToken: String) {
    it.property("oAuthToken", oAuthToken)
  }
}
