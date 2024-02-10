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

  public fun connectionBean(connectionBean: String) {
    this.connectionBean = connectionBean
    it.url("$connectionBean")
  }

  public fun autoCreateDatabase(autoCreateDatabase: String) {
    it.property("autoCreateDatabase", autoCreateDatabase)
  }

  public fun autoCreateDatabase(autoCreateDatabase: Boolean) {
    it.property("autoCreateDatabase", autoCreateDatabase.toString())
  }

  public fun batch(batch: String) {
    it.property("batch", batch)
  }

  public fun batch(batch: Boolean) {
    it.property("batch", batch.toString())
  }

  public fun checkDatabaseExistence(checkDatabaseExistence: String) {
    it.property("checkDatabaseExistence", checkDatabaseExistence)
  }

  public fun checkDatabaseExistence(checkDatabaseExistence: Boolean) {
    it.property("checkDatabaseExistence", checkDatabaseExistence.toString())
  }

  public fun databaseName(databaseName: String) {
    it.property("databaseName", databaseName)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun retentionPolicy(retentionPolicy: String) {
    it.property("retentionPolicy", retentionPolicy)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
