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
 * Stitch is a cloud ETL service that integrates various data sources into a central data warehouse
 * through various integrations.
 */
public fun UriDsl.stitch(i: StitchUriDsl.() -> Unit) {
  StitchUriDsl(this).apply(i)
}

@CamelDslMarker
public class StitchUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("stitch")
  }

  private var tableName: String = ""

  /**
   * The name of the destination table the data is being pushed to. Table names must be unique in
   * each destination schema, or loading issues will occur. Note: The number of characters in the table
   * name should be within the destination's allowed limits or data will rejected.
   */
  public fun tableName(tableName: String) {
    this.tableName = tableName
    it.url("$tableName")
  }

  /**
   * A collection of comma separated strings representing the Primary Key fields in the source
   * table. Stitch use these Primary Keys to de-dupe data during loading If not provided, the table
   * will be loaded in an append-only manner.
   */
  public fun keyNames(keyNames: String) {
    it.property("keyNames", keyNames)
  }

  /**
   * Stitch account region, e.g: europe
   */
  public fun region(region: String) {
    it.property("region", region)
  }

  /**
   * A schema that describes the record(s)
   */
  public fun stitchSchema(stitchSchema: String) {
    it.property("stitchSchema", stitchSchema)
  }

  /**
   * ConnectionProvider contain configuration for the HttpClient like Maximum connection limit ..
   * etc, you can inject this ConnectionProvider and the StitchClient will initialize HttpClient with
   * this ConnectionProvider
   */
  public fun connectionProvider(connectionProvider: String) {
    it.property("connectionProvider", connectionProvider)
  }

  /**
   * Reactor Netty HttpClient, you can injected it if you want to have custom HttpClient
   */
  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
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
   * Set a custom StitchClient that implements org.apache.camel.component.stitch.client.StitchClient
   * interface
   */
  public fun stitchClient(stitchClient: String) {
    it.property("stitchClient", stitchClient)
  }

  /**
   * Stitch access token for the Stitch Import API
   */
  public fun token(token: String) {
    it.property("token", token)
  }
}
