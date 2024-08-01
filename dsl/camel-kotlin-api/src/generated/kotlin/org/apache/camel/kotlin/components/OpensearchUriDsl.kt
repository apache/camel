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
 * Send requests to OpenSearch via Java Client API.
 */
public fun UriDsl.opensearch(i: OpensearchUriDsl.() -> Unit) {
  OpensearchUriDsl(this).apply(i)
}

@CamelDslMarker
public class OpensearchUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("opensearch")
  }

  private var clusterName: String = ""

  /**
   * Name of the cluster
   */
  public fun clusterName(clusterName: String) {
    this.clusterName = clusterName
    it.url("$clusterName")
  }

  /**
   * The time in ms to wait before connection will time out.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * The time in ms to wait before connection will time out.
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * Disconnect after it finish calling the producer
   */
  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  /**
   * Disconnect after it finish calling the producer
   */
  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  /**
   * Starting index of the response.
   */
  public fun from(from: String) {
    it.property("from", from)
  }

  /**
   * Starting index of the response.
   */
  public fun from(from: Int) {
    it.property("from", from.toString())
  }

  /**
   * Comma separated list with ip:port formatted remote transport addresses to use.
   */
  public fun hostAddresses(hostAddresses: String) {
    it.property("hostAddresses", hostAddresses)
  }

  /**
   * The name of the index to act against
   */
  public fun indexName(indexName: String) {
    it.property("indexName", indexName)
  }

  /**
   * The time in ms before retry
   */
  public fun maxRetryTimeout(maxRetryTimeout: String) {
    it.property("maxRetryTimeout", maxRetryTimeout)
  }

  /**
   * The time in ms before retry
   */
  public fun maxRetryTimeout(maxRetryTimeout: Int) {
    it.property("maxRetryTimeout", maxRetryTimeout.toString())
  }

  /**
   * What operation to perform
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Time in ms during which OpenSearch will keep search context alive
   */
  public fun scrollKeepAliveMs(scrollKeepAliveMs: String) {
    it.property("scrollKeepAliveMs", scrollKeepAliveMs)
  }

  /**
   * Time in ms during which OpenSearch will keep search context alive
   */
  public fun scrollKeepAliveMs(scrollKeepAliveMs: Int) {
    it.property("scrollKeepAliveMs", scrollKeepAliveMs.toString())
  }

  /**
   * Size of the response.
   */
  public fun size(size: String) {
    it.property("size", size)
  }

  /**
   * Size of the response.
   */
  public fun size(size: Int) {
    it.property("size", size.toString())
  }

  /**
   * The timeout in ms to wait before the socket will time out.
   */
  public fun socketTimeout(socketTimeout: String) {
    it.property("socketTimeout", socketTimeout)
  }

  /**
   * The timeout in ms to wait before the socket will time out.
   */
  public fun socketTimeout(socketTimeout: Int) {
    it.property("socketTimeout", socketTimeout.toString())
  }

  /**
   * Enable scroll usage
   */
  public fun useScroll(useScroll: String) {
    it.property("useScroll", useScroll)
  }

  /**
   * Enable scroll usage
   */
  public fun useScroll(useScroll: Boolean) {
    it.property("useScroll", useScroll.toString())
  }

  /**
   * Index creation waits for the write consistency number of shards to be available
   */
  public fun waitForActiveShards(waitForActiveShards: String) {
    it.property("waitForActiveShards", waitForActiveShards)
  }

  /**
   * Index creation waits for the write consistency number of shards to be available
   */
  public fun waitForActiveShards(waitForActiveShards: Int) {
    it.property("waitForActiveShards", waitForActiveShards.toString())
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
   * The class to use when deserializing the documents.
   */
  public fun documentClass(documentClass: String) {
    it.property("documentClass", documentClass)
  }

  /**
   * Enable automatically discover nodes from a running OpenSearch cluster. If this option is used
   * in conjunction with Spring Boot, then it's managed by the Spring Boot configuration (see: Disable
   * Sniffer in Spring Boot).
   */
  public fun enableSniffer(enableSniffer: String) {
    it.property("enableSniffer", enableSniffer)
  }

  /**
   * Enable automatically discover nodes from a running OpenSearch cluster. If this option is used
   * in conjunction with Spring Boot, then it's managed by the Spring Boot configuration (see: Disable
   * Sniffer in Spring Boot).
   */
  public fun enableSniffer(enableSniffer: Boolean) {
    it.property("enableSniffer", enableSniffer.toString())
  }

  /**
   * The delay of a sniff execution scheduled after a failure (in milliseconds)
   */
  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: String) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay)
  }

  /**
   * The delay of a sniff execution scheduled after a failure (in milliseconds)
   */
  public fun sniffAfterFailureDelay(sniffAfterFailureDelay: Int) {
    it.property("sniffAfterFailureDelay", sniffAfterFailureDelay.toString())
  }

  /**
   * The interval between consecutive ordinary sniff executions in milliseconds. Will be honoured
   * when sniffOnFailure is disabled or when there are no failures between consecutive sniff executions
   */
  public fun snifferInterval(snifferInterval: String) {
    it.property("snifferInterval", snifferInterval)
  }

  /**
   * The interval between consecutive ordinary sniff executions in milliseconds. Will be honoured
   * when sniffOnFailure is disabled or when there are no failures between consecutive sniff executions
   */
  public fun snifferInterval(snifferInterval: Int) {
    it.property("snifferInterval", snifferInterval.toString())
  }

  /**
   * The certificate that can be used to access the ES Cluster. It can be loaded by default from
   * classpath, but you can prefix with classpath:, file:, or http: to load the resource from different
   * systems.
   */
  public fun certificatePath(certificatePath: String) {
    it.property("certificatePath", certificatePath)
  }

  /**
   * Enable SSL
   */
  public fun enableSSL(enableSSL: String) {
    it.property("enableSSL", enableSSL)
  }

  /**
   * Enable SSL
   */
  public fun enableSSL(enableSSL: Boolean) {
    it.property("enableSSL", enableSSL.toString())
  }
}
