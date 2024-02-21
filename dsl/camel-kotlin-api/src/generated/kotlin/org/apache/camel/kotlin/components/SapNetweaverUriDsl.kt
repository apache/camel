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

public fun UriDsl.`sap-netweaver`(i: SapNetweaverUriDsl.() -> Unit) {
  SapNetweaverUriDsl(this).apply(i)
}

@CamelDslMarker
public class SapNetweaverUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sap-netweaver")
  }

  private var url: String = ""

  public fun url(url: String) {
    this.url = url
    it.url("$url")
  }

  public fun flatternMap(flatternMap: String) {
    it.property("flatternMap", flatternMap)
  }

  public fun flatternMap(flatternMap: Boolean) {
    it.property("flatternMap", flatternMap.toString())
  }

  public fun json(json: String) {
    it.property("json", json)
  }

  public fun json(json: Boolean) {
    it.property("json", json.toString())
  }

  public fun jsonAsMap(jsonAsMap: String) {
    it.property("jsonAsMap", jsonAsMap)
  }

  public fun jsonAsMap(jsonAsMap: Boolean) {
    it.property("jsonAsMap", jsonAsMap.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
