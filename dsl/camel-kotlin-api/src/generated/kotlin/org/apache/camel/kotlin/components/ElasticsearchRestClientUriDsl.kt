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

public fun UriDsl.`elasticsearch-rest-client`(i: ElasticsearchRestClientUriDsl.() -> Unit) {
  ElasticsearchRestClientUriDsl(this).apply(i)
}

@CamelDslMarker
public class ElasticsearchRestClientUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("elasticsearch-rest-client")
  }

  private var clusterName: String = ""

  public fun clusterName(clusterName: String) {
    this.clusterName = clusterName
    it.url("$clusterName")
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun hostAddressesList(hostAddressesList: String) {
    it.property("hostAddressesList", hostAddressesList)
  }

  public fun indexName(indexName: String) {
    it.property("indexName", indexName)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun socketTimeout(socketTimeout: String) {
    it.property("socketTimeout", socketTimeout)
  }

  public fun socketTimeout(socketTimeout: Int) {
    it.property("socketTimeout", socketTimeout.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun enableSniffer(enableSniffer: String) {
    it.property("enableSniffer", enableSniffer)
  }

  public fun enableSniffer(enableSniffer: Boolean) {
    it.property("enableSniffer", enableSniffer.toString())
  }

  public fun restClient(restClient: String) {
    it.property("restClient", restClient)
  }

  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: String) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay)
  }

  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: Int) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay.toString())
  }

  public fun snifferInterval(snifferInterval: String) {
    it.property("snifferInterval", snifferInterval)
  }

  public fun snifferInterval(snifferInterval: Int) {
    it.property("snifferInterval", snifferInterval.toString())
  }

  public fun certificatePath(certificatePath: String) {
    it.property("certificatePath", certificatePath)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun user(user: String) {
    it.property("user", user)
  }
}
