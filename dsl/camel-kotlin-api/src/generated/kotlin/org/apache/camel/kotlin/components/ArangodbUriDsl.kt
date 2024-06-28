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
 * Perform operations on ArangoDb when used as a Document Database, or as a Graph Database
 */
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

  /**
   * database name
   */
  public fun database(database: String) {
    this.database = database
    it.url("$database")
  }

  /**
   * Collection name, when using ArangoDb as a Document Database. Set the documentCollection name
   * when using the CRUD operation on the document database collections (SAVE_DOCUMENT ,
   * FIND_DOCUMENT_BY_KEY, UPDATE_DOCUMENT, DELETE_DOCUMENT).
   */
  public fun documentCollection(documentCollection: String) {
    it.property("documentCollection", documentCollection)
  }

  /**
   * Collection name of vertices, when using ArangoDb as a Graph Database. Set the edgeCollection
   * name to perform CRUD operation on edges using these operations : SAVE_VERTEX, FIND_VERTEX_BY_KEY,
   * UPDATE_VERTEX, DELETE_VERTEX. The graph attribute is mandatory.
   */
  public fun edgeCollection(edgeCollection: String) {
    it.property("edgeCollection", edgeCollection)
  }

  /**
   * Graph name, when using ArangoDb as a Graph Database. Combine this attribute with one of the two
   * attributes vertexCollection and edgeCollection.
   */
  public fun graph(graph: String) {
    it.property("graph", graph)
  }

  /**
   * ArangoDB host. If host and port are default, this field is Optional.
   */
  public fun host(host: String) {
    it.property("host", host)
  }

  /**
   * Operations to perform on ArangoDb. For the operation AQL_QUERY, no need to specify a collection
   * or graph.
   */
  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  /**
   * ArangoDB exposed port. If host and port are default, this field is Optional.
   */
  public fun port(port: String) {
    it.property("port", port)
  }

  /**
   * ArangoDB exposed port. If host and port are default, this field is Optional.
   */
  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  /**
   * Collection name of vertices, when using ArangoDb as a Graph Database. Set the vertexCollection
   * name to perform CRUD operation on vertices using these operations : SAVE_EDGE, FIND_EDGE_BY_KEY,
   * UPDATE_EDGE, DELETE_EDGE. The graph attribute is mandatory.
   */
  public fun vertexCollection(vertexCollection: String) {
    it.property("vertexCollection", vertexCollection)
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
   * To use an existing ArangDB client.
   */
  public fun arangoDB(arangoDB: String) {
    it.property("arangoDB", arangoDB)
  }

  /**
   * To use an existing Vertx instance in the ArangoDB client.
   */
  public fun vertx(vertx: String) {
    it.property("vertx", vertx)
  }

  /**
   * ArangoDB password. If user and password are default, this field is Optional.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * ArangoDB user. If user and password are default, this field is Optional.
   */
  public fun user(user: String) {
    it.property("user", user)
  }
}
