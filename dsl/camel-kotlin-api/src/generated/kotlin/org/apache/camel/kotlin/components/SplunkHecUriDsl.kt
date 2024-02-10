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

public fun UriDsl.`splunk-hec`(i: SplunkHecUriDsl.() -> Unit) {
  SplunkHecUriDsl(this).apply(i)
}

@CamelDslMarker
public class SplunkHecUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("splunk-hec")
  }

  private var splunkURL: String = ""

  public fun splunkURL(splunkURL: String) {
    this.splunkURL = splunkURL
    it.url("$splunkURL")
  }

  public fun bodyOnly(bodyOnly: String) {
    it.property("bodyOnly", bodyOnly)
  }

  public fun bodyOnly(bodyOnly: Boolean) {
    it.property("bodyOnly", bodyOnly.toString())
  }

  public fun headersOnly(headersOnly: String) {
    it.property("headersOnly", headersOnly)
  }

  public fun headersOnly(headersOnly: Boolean) {
    it.property("headersOnly", headersOnly.toString())
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun index(index: String) {
    it.property("index", index)
  }

  public fun source(source: String) {
    it.property("source", source)
  }

  public fun sourceType(sourceType: String) {
    it.property("sourceType", sourceType)
  }

  public fun splunkEndpoint(splunkEndpoint: String) {
    it.property("splunkEndpoint", splunkEndpoint)
  }

  public fun time(time: String) {
    it.property("time", time)
  }

  public fun time(time: Int) {
    it.property("time", time.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun https(https: String) {
    it.property("https", https)
  }

  public fun https(https: Boolean) {
    it.property("https", https.toString())
  }

  public fun skipTlsVerify(skipTlsVerify: String) {
    it.property("skipTlsVerify", skipTlsVerify)
  }

  public fun skipTlsVerify(skipTlsVerify: Boolean) {
    it.property("skipTlsVerify", skipTlsVerify.toString())
  }

  public fun token(token: String) {
    it.property("token", token)
  }
}
