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

public fun UriDsl.flink(i: FlinkUriDsl.() -> Unit) {
  FlinkUriDsl(this).apply(i)
}

@CamelDslMarker
public class FlinkUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("flink")
  }

  private var endpointType: String = ""

  public fun endpointType(endpointType: String) {
    this.endpointType = endpointType
    it.url("$endpointType")
  }

  public fun collect(collect: String) {
    it.property("collect", collect)
  }

  public fun collect(collect: Boolean) {
    it.property("collect", collect.toString())
  }

  public fun dataSet(dataSet: String) {
    it.property("dataSet", dataSet)
  }

  public fun dataSetCallback(dataSetCallback: String) {
    it.property("dataSetCallback", dataSetCallback)
  }

  public fun dataStream(dataStream: String) {
    it.property("dataStream", dataStream)
  }

  public fun dataStreamCallback(dataStreamCallback: String) {
    it.property("dataStreamCallback", dataStreamCallback)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
