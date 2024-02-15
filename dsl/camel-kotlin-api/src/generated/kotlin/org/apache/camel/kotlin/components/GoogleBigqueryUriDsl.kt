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

public fun UriDsl.`google-bigquery`(i: GoogleBigqueryUriDsl.() -> Unit) {
  GoogleBigqueryUriDsl(this).apply(i)
}

@CamelDslMarker
public class GoogleBigqueryUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("google-bigquery")
  }

  private var projectId: String = ""

  private var datasetId: String = ""

  private var tableId: String = ""

  public fun projectId(projectId: String) {
    this.projectId = projectId
    it.url("$projectId:$datasetId:$tableId")
  }

  public fun datasetId(datasetId: String) {
    this.datasetId = datasetId
    it.url("$projectId:$datasetId:$tableId")
  }

  public fun tableId(tableId: String) {
    this.tableId = tableId
    it.url("$projectId:$datasetId:$tableId")
  }

  public fun connectionFactory(connectionFactory: String) {
    it.property("connectionFactory", connectionFactory)
  }

  public fun useAsInsertId(useAsInsertId: String) {
    it.property("useAsInsertId", useAsInsertId)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun serviceAccountKey(serviceAccountKey: String) {
    it.property("serviceAccountKey", serviceAccountKey)
  }
}
