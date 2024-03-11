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
 * Send GraphQL queries and mutations to external systems.
 */
public fun UriDsl.graphql(i: GraphqlUriDsl.() -> Unit) {
  GraphqlUriDsl(this).apply(i)
}

@CamelDslMarker
public class GraphqlUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("graphql")
  }

  private var httpUri: String = ""

  /**
   * The GraphQL server URI.
   */
  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("$httpUri")
  }

  /**
   * The query or mutation name.
   */
  public fun operationName(operationName: String) {
    it.property("operationName", operationName)
  }

  /**
   * The proxy host in the format hostname:port.
   */
  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  /**
   * The query text.
   */
  public fun query(query: String) {
    it.property("query", query)
  }

  /**
   * The query file name located in the classpath.
   */
  public fun queryFile(queryFile: String) {
    it.property("queryFile", queryFile)
  }

  /**
   * The name of a header containing the GraphQL query.
   */
  public fun queryHeader(queryHeader: String) {
    it.property("queryHeader", queryHeader)
  }

  /**
   * The JsonObject instance containing the operation variables.
   */
  public fun variables(variables: String) {
    it.property("variables", variables)
  }

  /**
   * The name of a header containing a JsonObject instance containing the operation variables.
   */
  public fun variablesHeader(variablesHeader: String) {
    it.property("variablesHeader", variablesHeader)
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
   * The access token sent in the Authorization header.
   */
  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  /**
   * The JWT Authorization type. Default is Bearer.
   */
  public fun jwtAuthorizationType(jwtAuthorizationType: String) {
    it.property("jwtAuthorizationType", jwtAuthorizationType)
  }

  /**
   * The password for Basic authentication.
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * The username for Basic authentication.
   */
  public fun username(username: String) {
    it.property("username", username)
  }
}
