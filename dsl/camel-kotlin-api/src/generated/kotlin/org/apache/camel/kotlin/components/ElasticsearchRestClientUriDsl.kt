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
 * Perform queries and other operations on Elasticsearch or OpenSearch (uses low-level client).
 */
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

  /**
   * Cluster Name
   */
  public fun clusterName(clusterName: String) {
    this.clusterName = clusterName
    it.url("$clusterName")
  }

  /**
   * Connection timeout
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Connection timeout
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * List of host Addresses, multiple hosts can be separated by comma.
   */
  public fun hostAddressesList(hostAddressesList: String) {
    it.property("hostAddressesList", hostAddressesList)
  }

  /**
   * Index Name
   */
  public fun indexName(indexName: String) {
    it.property("indexName", indexName)
  }

  /**
   * Operation
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Socket timeout
   */
  public fun socketTimeout(socketTimeout: String) {
    it.property("socketTimeout", socketTimeout)
  }

  /**
   * Socket timeout
   */
  public fun socketTimeout(socketTimeout: Int) {
    it.property("socketTimeout", socketTimeout.toString())
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
   * Enabling Sniffer
   */
  public fun enableSniffer(enableSniffer: String) {
    it.property("enableSniffer", enableSniffer)
  }

  /**
   * Enabling Sniffer
   */
  public fun enableSniffer(enableSniffer: Boolean) {
    it.property("enableSniffer", enableSniffer.toString())
  }

  /**
   * Rest Client of type org.elasticsearch.client.RestClient. This is only for advanced usage
   */
  public fun restClient(restClient: String) {
    it.property("restClient", restClient)
  }

  /**
   * Sniffer after failure delay (in millis)
   */
  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: String) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay)
  }

  /**
   * Sniffer after failure delay (in millis)
   */
  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: Int) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay.toString())
  }

  /**
   * Sniffer interval (in millis)
   */
  public fun snifferInterval(snifferInterval: String) {
    it.property("snifferInterval", snifferInterval)
  }

  /**
   * Sniffer interval (in millis)
   */
  public fun snifferInterval(snifferInterval: Int) {
    it.property("snifferInterval", snifferInterval.toString())
  }

  /**
   * Certificate Path
   */
  public fun certificatePath(certificatePath: String) {
    it.property("certificatePath", certificatePath)
  }

  /**
   * Password
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Username
   */
  public fun user(user: String) {
    it.property("user", user)
  }
}
