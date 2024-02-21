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

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun from(from: String) {
    it.property("from", from)
  }

  public fun from(from: Int) {
    it.property("from", from.toString())
  }

  public fun hostAddresses(hostAddresses: String) {
    it.property("hostAddresses", hostAddresses)
  }

  public fun indexName(indexName: String) {
    it.property("indexName", indexName)
  }

  public fun maxRetryTimeout(maxRetryTimeout: String) {
    it.property("maxRetryTimeout", maxRetryTimeout)
  }

  public fun maxRetryTimeout(maxRetryTimeout: Int) {
    it.property("maxRetryTimeout", maxRetryTimeout.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun scrollKeepAliveMs(scrollKeepAliveMs: String) {
    it.property("scrollKeepAliveMs", scrollKeepAliveMs)
  }

  public fun scrollKeepAliveMs(scrollKeepAliveMs: Int) {
    it.property("scrollKeepAliveMs", scrollKeepAliveMs.toString())
  }

  public fun size(size: String) {
    it.property("size", size)
  }

  public fun size(size: Int) {
    it.property("size", size.toString())
  }

  public fun socketTimeout(socketTimeout: String) {
    it.property("socketTimeout", socketTimeout)
  }

  public fun socketTimeout(socketTimeout: Int) {
    it.property("socketTimeout", socketTimeout.toString())
  }

  public fun useScroll(useScroll: String) {
    it.property("useScroll", useScroll)
  }

  public fun useScroll(useScroll: Boolean) {
    it.property("useScroll", useScroll.toString())
  }

  public fun waitForActiveShards(waitForActiveShards: String) {
    it.property("waitForActiveShards", waitForActiveShards)
  }

  public fun waitForActiveShards(waitForActiveShards: Int) {
    it.property("waitForActiveShards", waitForActiveShards.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun documentClass(documentClass: String) {
    it.property("documentClass", documentClass)
  }

  public fun enableSniffer(enableSniffer: String) {
    it.property("enableSniffer", enableSniffer)
  }

  public fun enableSniffer(enableSniffer: Boolean) {
    it.property("enableSniffer", enableSniffer.toString())
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

  public fun enableSSL(enableSSL: String) {
    it.property("enableSSL", enableSSL)
  }

  public fun enableSSL(enableSSL: Boolean) {
    it.property("enableSSL", enableSSL.toString())
  }
}
