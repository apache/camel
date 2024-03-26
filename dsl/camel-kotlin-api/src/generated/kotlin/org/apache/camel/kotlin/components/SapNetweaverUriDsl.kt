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

/**
 * Send requests to SAP NetWeaver Gateway using HTTP.
 */
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

  /**
   * Url to the SAP net-weaver gateway server.
   */
  public fun url(url: String) {
    this.url = url
    it.url("$url")
  }

  /**
   * If the JSON Map contains only a single entry, then flattern by storing that single entry value
   * as the message body.
   */
  public fun flatternMap(flatternMap: String) {
    it.property("flatternMap", flatternMap)
  }

  /**
   * If the JSON Map contains only a single entry, then flattern by storing that single entry value
   * as the message body.
   */
  public fun flatternMap(flatternMap: Boolean) {
    it.property("flatternMap", flatternMap.toString())
  }

  /**
   * Whether to return data in JSON format. If this option is false, then XML is returned in Atom
   * format.
   */
  public fun json(json: String) {
    it.property("json", json)
  }

  /**
   * Whether to return data in JSON format. If this option is false, then XML is returned in Atom
   * format.
   */
  public fun json(json: Boolean) {
    it.property("json", json.toString())
  }

  /**
   * To transform the JSON from a String to a Map in the message body.
   */
  public fun jsonAsMap(jsonAsMap: String) {
    it.property("jsonAsMap", jsonAsMap)
  }

  /**
   * To transform the JSON from a String to a Map in the message body.
   */
  public fun jsonAsMap(jsonAsMap: Boolean) {
    it.property("jsonAsMap", jsonAsMap.toString())
  }

  /**
   * Password for account.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Username for account.
   */
  public fun username(username: String) {
    it.property("username", username)
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
