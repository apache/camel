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
 * Perform operations against Apache Lucene Solr.
 */
public fun UriDsl.solr(i: SolrUriDsl.() -> Unit) {
  SolrUriDsl(this).apply(i)
}

@CamelDslMarker
public class SolrUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("solr")
  }

  private var url: String = ""

  /**
   * Hostname and port for the Solr server(s). Multiple hosts can be specified, separated with a
   * comma. See the solrClient parameter for more information on the SolrClient used to connect to
   * Solr.
   */
  public fun url(url: String) {
    this.url = url
    it.url("$url")
  }

  /**
   * If true, each producer operation will be automatically followed by a commit
   */
  public fun autoCommit(autoCommit: String) {
    it.property("autoCommit", autoCommit)
  }

  /**
   * If true, each producer operation will be automatically followed by a commit
   */
  public fun autoCommit(autoCommit: Boolean) {
    it.property("autoCommit", autoCommit.toString())
  }

  /**
   * Sets the connection timeout on the SolrClient
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * Sets the connection timeout on the SolrClient
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * maxConnectionsPerHost on the underlying HttpConnectionManager
   */
  public fun defaultMaxConnectionsPerHost(defaultMaxConnectionsPerHost: String) {
    it.property("defaultMaxConnectionsPerHost", defaultMaxConnectionsPerHost)
  }

  /**
   * maxConnectionsPerHost on the underlying HttpConnectionManager
   */
  public fun defaultMaxConnectionsPerHost(defaultMaxConnectionsPerHost: Int) {
    it.property("defaultMaxConnectionsPerHost", defaultMaxConnectionsPerHost.toString())
  }

  /**
   * Sets the http client to be used by the solrClient. This is only applicable when solrClient is
   * not set.
   */
  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  /**
   * Sets the idle timeout on the SolrClient
   */
  public fun idleTimeout(idleTimeout: String) {
    it.property("idleTimeout", idleTimeout)
  }

  /**
   * Sets the idle timeout on the SolrClient
   */
  public fun idleTimeout(idleTimeout: Int) {
    it.property("idleTimeout", idleTimeout.toString())
  }

  /**
   * Maximum number of retries to attempt in the event of transient errors
   */
  public fun maxRetries(maxRetries: String) {
    it.property("maxRetries", maxRetries)
  }

  /**
   * Maximum number of retries to attempt in the event of transient errors
   */
  public fun maxRetries(maxRetries: Int) {
    it.property("maxRetries", maxRetries.toString())
  }

  /**
   * maxTotalConnection on the underlying HttpConnectionManager
   */
  public fun maxTotalConnections(maxTotalConnections: String) {
    it.property("maxTotalConnections", maxTotalConnections)
  }

  /**
   * maxTotalConnection on the underlying HttpConnectionManager
   */
  public fun maxTotalConnections(maxTotalConnections: Int) {
    it.property("maxTotalConnections", maxTotalConnections.toString())
  }

  /**
   * Set the request handler to be used
   */
  public fun requestHandler(requestHandler: String) {
    it.property("requestHandler", requestHandler)
  }

  /**
   * Uses the provided solr client to connect to solr. When this parameter is not specified, camel
   * applies the following rules to determine the SolrClient: 1) when zkHost or zkChroot (=zookeeper
   * root) parameter is set, then the CloudSolrClient is used. 2) when multiple hosts are specified in
   * the uri (separated with a comma), then the CloudSolrClient (uri scheme is 'solrCloud') or the
   * LBHttpSolrClient (uri scheme is not 'solrCloud') is used. 3) when the solr operation is
   * INSERT_STREAMING, then the ConcurrentUpdateSolrClient is used. 4) otherwise, the HttpSolrClient is
   * used. Note: A CloudSolrClient should point to zookeeper endpoint(s); other clients point to Solr
   * endpoint(s). The SolrClient can also be set via the exchange header 'CamelSolrClient'.
   */
  public fun solrClient(solrClient: String) {
    it.property("solrClient", solrClient)
  }

  /**
   * Sets the queue size for the ConcurrentUpdateSolrClient
   */
  public fun streamingQueueSize(streamingQueueSize: String) {
    it.property("streamingQueueSize", streamingQueueSize)
  }

  /**
   * Sets the queue size for the ConcurrentUpdateSolrClient
   */
  public fun streamingQueueSize(streamingQueueSize: Int) {
    it.property("streamingQueueSize", streamingQueueSize.toString())
  }

  /**
   * Sets the number of threads for the ConcurrentUpdateSolrClient
   */
  public fun streamingThreadCount(streamingThreadCount: String) {
    it.property("streamingThreadCount", streamingThreadCount)
  }

  /**
   * Sets the number of threads for the ConcurrentUpdateSolrClient
   */
  public fun streamingThreadCount(streamingThreadCount: Int) {
    it.property("streamingThreadCount", streamingThreadCount.toString())
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
   * Set the default collection for SolrCloud
   */
  public fun collection(collection: String) {
    it.property("collection", collection)
  }

  /**
   * Set the chroot of the zookeeper connection (include the leading slash; e.g. '/mychroot')
   */
  public fun zkChroot(zkChroot: String) {
    it.property("zkChroot", zkChroot)
  }

  /**
   * Set the ZooKeeper host(s) urls which the CloudSolrClient uses, e.g.
   * zkHost=localhost:2181,localhost:2182. Optionally add the chroot, e.g.
   * zkHost=localhost:2181,localhost:2182/rootformysolr. In case the first part of the url path
   * (='contextroot') is set to 'solr' (e.g. 'localhost:2181/solr' or 'localhost:2181/solr/..'), then
   * that path is not considered as zookeeper chroot for backward compatibility reasons (this behaviour
   * can be overridden via zkChroot parameter).
   */
  public fun zkHost(zkHost: String) {
    it.property("zkHost", zkHost)
  }

  /**
   * Server side must support gzip or deflate for this to have any effect
   */
  public fun allowCompression(allowCompression: String) {
    it.property("allowCompression", allowCompression)
  }

  /**
   * Server side must support gzip or deflate for this to have any effect
   */
  public fun allowCompression(allowCompression: Boolean) {
    it.property("allowCompression", allowCompression.toString())
  }

  /**
   * Indicates whether redirects are used to get to the Solr server
   */
  public fun followRedirects(followRedirects: String) {
    it.property("followRedirects", followRedirects)
  }

  /**
   * Indicates whether redirects are used to get to the Solr server
   */
  public fun followRedirects(followRedirects: Boolean) {
    it.property("followRedirects", followRedirects.toString())
  }

  /**
   * Sets password for basic auth plugin enabled servers
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * Sets username for basic auth plugin enabled servers
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
