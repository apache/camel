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

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun page(page: String) {
    it.property("page", page)
  }

  public fun page(page: Int) {
    it.property("page", page.toString())
  }

  public fun perPage(perPage: String) {
    it.property("perPage", perPage)
  }

  public fun perPage(perPage: Int) {
    it.property("perPage", perPage.toString())
  }

  public fun resource(resource: String) {
    it.property("resource", resource)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun digitalOceanClient(digitalOceanClient: String) {
    it.property("digitalOceanClient", digitalOceanClient)
  }

  public fun httpProxyHost(httpProxyHost: String) {
    it.property("httpProxyHost", httpProxyHost)
  }

  public fun httpProxyPassword(httpProxyPassword: String) {
    it.property("httpProxyPassword", httpProxyPassword)
  }

  public fun httpProxyPort(httpProxyPort: String) {
    it.property("httpProxyPort", httpProxyPort)
  }

  public fun httpProxyPort(httpProxyPort: Int) {
    it.property("httpProxyPort", httpProxyPort.toString())
  }

  public fun httpProxyUser(httpProxyUser: String) {
    it.property("httpProxyUser", httpProxyUser)
  }

  public fun oAuthToken(oAuthToken: String) {
    it.property("oAuthToken", oAuthToken)
  }
}
