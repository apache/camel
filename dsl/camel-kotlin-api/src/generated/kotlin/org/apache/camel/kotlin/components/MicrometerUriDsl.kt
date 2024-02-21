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

public fun UriDsl.micrometer(i: MicrometerUriDsl.() -> Unit) {
  MicrometerUriDsl(this).apply(i)
}

@CamelDslMarker
public class MicrometerUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("micrometer")
  }

  private var metricsType: String = ""

  private var metricsName: String = ""

  private var tags: String = ""

  public fun metricsType(metricsType: String) {
    this.metricsType = metricsType
    it.url("$metricsType:$metricsName")
  }

  public fun metricsName(metricsName: String) {
    this.metricsName = metricsName
    it.url("$metricsType:$metricsName")
  }

  public fun tags(tags: String) {
    this.tags = tags
    it.url("$metricsType:$metricsName")
  }

  public fun action(action: String) {
    it.property("action", action)
  }

  public fun decrement(decrement: String) {
    it.property("decrement", decrement)
  }

  public fun increment(increment: String) {
    it.property("increment", increment)
  }

  public fun metricsDescription(metricsDescription: String) {
    it.property("metricsDescription", metricsDescription)
  }

  public fun `value`(`value`: String) {
    it.property("value", value)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
