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
 * Interact with InfluxDB v1, a time series database.
 */
public fun UriDsl.influxdb(i: InfluxdbUriDsl.() -> Unit) {
  InfluxdbUriDsl(this).apply(i)
}

@CamelDslMarker
public class InfluxdbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("influxdb")
  }

  private var connectionBean: String = ""

  /**
   * Connection to the influx database, of class InfluxDB.class
   */
  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  /**
   * Define if we want to auto create the database if it's not present
   */
  public fun autoCreateDatabase(autoCreateDatabase: String) {
    it.property("autoCreateDatabase", autoCreateDatabase)
  }

  /**
   * Define if we want to auto create the database if it's not present
   */
  public fun autoCreateDatabase(autoCreateDatabase: Boolean) {
    it.property("autoCreateDatabase", autoCreateDatabase.toString())
  }

  /**
   * Define if this operation is a batch operation or not
   */
  public fun batch(batch: String) {
    it.property("batch", batch)
  }

  /**
   * Define if this operation is a batch operation or not
   */
  public fun batch(batch: Boolean) {
    it.property("batch", batch.toString())
  }

  /**
   * Define if we want to check the database existence while starting the endpoint
   */
  public fun checkDatabaseExistence(checkDatabaseExistence: String) {
    it.property("checkDatabaseExistence", checkDatabaseExistence)
  }

  /**
   * Define if we want to check the database existence while starting the endpoint
   */
  public fun checkDatabaseExistence(checkDatabaseExistence: Boolean) {
    it.property("checkDatabaseExistence", checkDatabaseExistence.toString())
  }

  /**
   * The name of the database where the time series will be stored
   */
  public fun databaseName(databaseName: String) {
    it.property("databaseName", databaseName)
  }

  /**
   * Define if this operation is an insert or a query
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * Define the query in case of operation query
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * The string that defines the retention policy to the data created by the endpoint
   */
  public fun retentionPolicy(retentionPolicy: String) {
    it.property("retentionPolicy", retentionPolicy)
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
