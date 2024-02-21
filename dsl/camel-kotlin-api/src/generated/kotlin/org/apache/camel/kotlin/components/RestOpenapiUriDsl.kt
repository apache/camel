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

public fun UriDsl.`rest-openapi`(i: RestOpenapiUriDsl.() -> Unit) {
  RestOpenapiUriDsl(this).apply(i)
}

@CamelDslMarker
public class RestOpenapiUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("rest-openapi")
  }

  private var specificationUri: String = ""

  private var operationId: String = ""

  public fun specificationUri(specificationUri: String) {
    this.specificationUri = specificationUri
    it.url("$specificationUri#$operationId")
  }

  public fun operationId(operationId: String) {
    this.operationId = operationId
    it.url("$specificationUri#$operationId")
  }

  public fun basePath(basePath: String) {
    it.property("basePath", basePath)
  }

  public fun componentName(componentName: String) {
    it.property("componentName", componentName)
  }

  public fun consumes(consumes: String) {
    it.property("consumes", consumes)
  }

  public fun host(host: String) {
    it.property("host", host)
  }

  public fun produces(produces: String) {
    it.property("produces", produces)
  }

  public fun requestValidationCustomizer(requestValidationCustomizer: String) {
    it.property("requestValidationCustomizer", requestValidationCustomizer)
  }

  public fun requestValidationEnabled(requestValidationEnabled: String) {
    it.property("requestValidationEnabled", requestValidationEnabled)
  }

  public fun requestValidationEnabled(requestValidationEnabled: Boolean) {
    it.property("requestValidationEnabled", requestValidationEnabled.toString())
  }

  public fun requestValidationLevels(requestValidationLevels: String) {
    it.property("requestValidationLevels", requestValidationLevels)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
