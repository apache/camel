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
 * Send DataSet jobs to an Apache Flink cluster.
 */
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

  /**
   * Type of the endpoint (dataset, datastream).
   */
  public fun endpointType(endpointType: String) {
    this.endpointType = endpointType
    it.url("$endpointType")
  }

  /**
   * Indicates if results should be collected or counted.
   */
  public fun collect(collect: String) {
    it.property("collect", collect)
  }

  /**
   * Indicates if results should be collected or counted.
   */
  public fun collect(collect: Boolean) {
    it.property("collect", collect.toString())
  }

  /**
   * DataSet to compute against.
   */
  public fun dataSet(dataSet: String) {
    it.property("dataSet", dataSet)
  }

  /**
   * Function performing action against a DataSet.
   */
  public fun dataSetCallback(dataSetCallback: String) {
    it.property("dataSetCallback", dataSetCallback)
  }

  /**
   * DataStream to compute against.
   */
  public fun dataStream(dataStream: String) {
    it.property("dataStream", dataStream)
  }

  /**
   * Function performing action against a DataStream.
   */
  public fun dataStreamCallback(dataStreamCallback: String) {
    it.property("dataStreamCallback", dataStreamCallback)
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
}
