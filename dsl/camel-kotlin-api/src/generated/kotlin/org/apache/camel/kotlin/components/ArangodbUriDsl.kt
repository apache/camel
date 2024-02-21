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

public fun UriDsl.arangodb(i: ArangodbUriDsl.() -> Unit) {
  ArangodbUriDsl(this).apply(i)
}

@CamelDslMarker
public class ArangodbUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("arangodb")
  }

  private var database: String = ""

  public fun database(database: String) {
    this.database = database
    it.url("$database")
  }

  public fun documentCollection(documentCollection: String) {
    it.property("documentCollection", documentCollection)
  }

  public fun edgeCollection(edgeCollection: String) {
    it.property("edgeCollection", edgeCollection)
  }

  public fun graph(graph: String) {
    it.property("graph", graph)
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun port(port: String) {
    it.property("port", port)
  }

  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  public fun vertexCollection(vertexCollection: String) {
    it.property("vertexCollection", vertexCollection)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun arangoDB(arangoDB: String) {
    it.property("arangoDB", arangoDB)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun user(user: String) {
    it.property("user", user)
  }
}
