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

public fun UriDsl.influxdb2(i: Influxdb2UriDsl.() -> Unit) {
  Influxdb2UriDsl(this).apply(i)
}

@CamelDslMarker
public class Influxdb2UriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("influxdb2")
  }

  private var connectionBean: String = ""

  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  public fun autoCreateOrg(autoCreateOrg: String) {
    it.property("autoCreateOrg", autoCreateOrg)
  }

  public fun autoCreateOrg(autoCreateOrg: Boolean) {
    it.property("autoCreateOrg", autoCreateOrg.toString())
  }

  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun org(org: String) {
    it.property("org", org)
  }

  public fun retentionPolicy(retentionPolicy: String) {
    it.property("retentionPolicy", retentionPolicy)
  }

  public fun writePrecision(writePrecision: String) {
    it.property("writePrecision", writePrecision)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
