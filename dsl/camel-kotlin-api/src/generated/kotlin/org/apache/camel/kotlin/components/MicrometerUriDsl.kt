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
 * Collect various metrics directly from Camel routes using the Micrometer library.
 */
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

  /**
   * Type of metrics
   */
  public fun metricsType(metricsType: String) {
    this.metricsType = metricsType
    it.url("$metricsType:$metricsName")
  }

  /**
   * Name of metrics
   */
  public fun metricsName(metricsName: String) {
    this.metricsName = metricsName
    it.url("$metricsType:$metricsName")
  }

  /**
   * Tags of metrics
   */
  public fun tags(tags: String) {
    this.tags = tags
    it.url("$metricsType:$metricsName")
  }

  /**
   * Action expression when using timer type
   */
  public fun action(action: String) {
    it.property("action", action)
  }

  /**
   * Decrement value expression when using counter type
   */
  public fun decrement(decrement: String) {
    it.property("decrement", decrement)
  }

  /**
   * Increment value expression when using counter type
   */
  public fun increment(increment: String) {
    it.property("increment", increment)
  }

  /**
   * Description of metrics
   */
  public fun metricsDescription(metricsDescription: String) {
    it.property("metricsDescription", metricsDescription)
  }

  /**
   * Value expression when using histogram type
   */
  public fun `value`(`value`: String) {
    it.property("value", value)
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
