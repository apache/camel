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
 * Interact with InfluxDB v2, a time series database.
 */
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

  /**
   * Connection to the Influx database, of class com.influxdb.client.InfluxDBClient.class.
   */
  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  /**
   * Define if we want to auto create the bucket if it's not present.
   */
  public fun autoCreateBucket(autoCreateBucket: String) {
    it.property("autoCreateBucket", autoCreateBucket)
  }

  /**
   * Define if we want to auto create the bucket if it's not present.
   */
  public fun autoCreateBucket(autoCreateBucket: Boolean) {
    it.property("autoCreateBucket", autoCreateBucket.toString())
  }

  /**
   * Define if we want to auto create the organization if it's not present.
   */
  public fun autoCreateOrg(autoCreateOrg: String) {
    it.property("autoCreateOrg", autoCreateOrg)
  }

  /**
   * Define if we want to auto create the organization if it's not present.
   */
  public fun autoCreateOrg(autoCreateOrg: Boolean) {
    it.property("autoCreateOrg", autoCreateOrg.toString())
  }

  /**
   * The name of the bucket where the time series will be stored.
   */
  public fun bucket(bucket: String) {
    it.property("bucket", bucket)
  }

  /**
   * Define if this operation is an insert of ping.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * The name of the organization where the time series will be stored.
   */
  public fun org(org: String) {
    it.property("org", org)
  }

  /**
   * Define the retention policy to the data created by the endpoint.
   */
  public fun retentionPolicy(retentionPolicy: String) {
    it.property("retentionPolicy", retentionPolicy)
  }

  /**
   * The format or precision of time series timestamps.
   */
  public fun writePrecision(writePrecision: String) {
    it.property("writePrecision", writePrecision)
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
