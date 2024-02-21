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

  public fun httpUri(httpUri: String) {
    this.httpUri = httpUri
    it.url("$httpUri")
  }

  public fun operationName(operationName: String) {
    it.property("operationName", operationName)
  }

  public fun proxyHost(proxyHost: String) {
    it.property("proxyHost", proxyHost)
  }

  public fun query(query: String) {
    it.property("query", query)
  }

  public fun queryFile(queryFile: String) {
    it.property("queryFile", queryFile)
  }

  public fun queryHeader(queryHeader: String) {
    it.property("queryHeader", queryHeader)
  }

  public fun variables(variables: String) {
    it.property("variables", variables)
  }

  public fun variablesHeader(variablesHeader: String) {
    it.property("variablesHeader", variablesHeader)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun accessToken(accessToken: String) {
    it.property("accessToken", accessToken)
  }

  public fun jwtAuthorizationType(jwtAuthorizationType: String) {
    it.property("jwtAuthorizationType", jwtAuthorizationType)
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun username(username: String) {
    it.property("username", username)
  }
}
