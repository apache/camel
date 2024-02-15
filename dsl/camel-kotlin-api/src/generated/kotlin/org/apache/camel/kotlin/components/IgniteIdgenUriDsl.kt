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

public fun UriDsl.`ignite-idgen`(i: IgniteIdgenUriDsl.() -> Unit) {
  IgniteIdgenUriDsl(this).apply(i)
}

@CamelDslMarker
public class IgniteIdgenUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("ignite-idgen")
  }

  private var name: String = ""

  public fun name(name: String) {
    this.name = name
    it.url("$name")
  }

  public fun batchSize(batchSize: String) {
    it.property("batchSize", batchSize)
  }

  public fun batchSize(batchSize: Int) {
    it.property("batchSize", batchSize.toString())
  }

  public fun initialValue(initialValue: String) {
    it.property("initialValue", initialValue)
  }

  public fun initialValue(initialValue: Int) {
    it.property("initialValue", initialValue.toString())
  }

  public fun operation(operation: String) {
    it.property("operation", operation)
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: String) {
    it.property("propagateIncomingBodyIfNoReturnValue", propagateIncomingBodyIfNoReturnValue)
  }

  public fun propagateIncomingBodyIfNoReturnValue(propagateIncomingBodyIfNoReturnValue: Boolean) {
    it.property("propagateIncomingBodyIfNoReturnValue",
        propagateIncomingBodyIfNoReturnValue.toString())
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: String) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects)
  }

  public fun treatCollectionsAsCacheObjects(treatCollectionsAsCacheObjects: Boolean) {
    it.property("treatCollectionsAsCacheObjects", treatCollectionsAsCacheObjects.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
