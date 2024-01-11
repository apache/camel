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

  public fun tableName(tableName: String) {
    this.tableName = tableName
    it.url("$tableName")
  }

  public fun keyNames(keyNames: String) {
    it.property("keyNames", keyNames)
  }

  public fun region(region: String) {
    it.property("region", region)
  }

  public fun stitchSchema(stitchSchema: String) {
    it.property("stitchSchema", stitchSchema)
  }

  public fun connectionProvider(connectionProvider: String) {
    it.property("connectionProvider", connectionProvider)
  }

  public fun httpClient(httpClient: String) {
    it.property("httpClient", httpClient)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun stitchClient(stitchClient: String) {
    it.property("stitchClient", stitchClient)
  }

  public fun token(token: String) {
    it.property("token", token)
  }
}
