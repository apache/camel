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

public fun UriDsl.`sql-stored`(i: SqlStoredUriDsl.() -> Unit) {
  SqlStoredUriDsl(this).apply(i)
}

@CamelDslMarker
public class SqlStoredUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("sql-stored")
  }

  private var template: String = ""

  public fun template(template: String) {
    this.template = template
    it.url("$template")
  }

  public fun batch(batch: String) {
    it.property("batch", batch)
  }

  public fun batch(batch: Boolean) {
    it.property("batch", batch.toString())
  }

  public fun dataSource(dataSource: String) {
    it.property("dataSource", dataSource)
  }

  public fun function(function: String) {
    it.property("function", function)
  }

  public fun function(function: Boolean) {
    it.property("function", function.toString())
  }

  public fun noop(noop: String) {
    it.property("noop", noop)
  }

  public fun noop(noop: Boolean) {
    it.property("noop", noop.toString())
  }

  public fun outputHeader(outputHeader: String) {
    it.property("outputHeader", outputHeader)
  }

  public fun useMessageBodyForTemplate(useMessageBodyForTemplate: String) {
    it.property("useMessageBodyForTemplate", useMessageBodyForTemplate)
  }

  public fun useMessageBodyForTemplate(useMessageBodyForTemplate: Boolean) {
    it.property("useMessageBodyForTemplate", useMessageBodyForTemplate.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun templateOptions(templateOptions: String) {
    it.property("templateOptions", templateOptions)
  }
}
