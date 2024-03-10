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
 * Manage and monitor Camel routes.
 */
public fun UriDsl.controlbus(i: ControlbusUriDsl.() -> Unit) {
  ControlbusUriDsl(this).apply(i)
}

@CamelDslMarker
public class ControlbusUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("controlbus")
  }

  private var command: String = ""

  private var language: String = ""

  /**
   * Command can be either route or language
   */
  public fun command(command: String) {
    this.command = command
    it.url("$command:$language")
  }

  /**
   * Allows you to specify the name of a Language to use for evaluating the message body. If there
   * is any result from the evaluation, then the result is put in the message body.
   */
  public fun language(language: String) {
    this.language = language
    it.url("$command:$language")
  }

  /**
   * To denote an action that can be either: start, stop, or status. To either start or stop a
   * route, or to get the status of the route as output in the message body. You can use suspend and
   * resume to either suspend or resume a route. You can use stats to get performance statics returned
   * in XML format; the routeId option can be used to define which route to get the performance stats
   * for, if routeId is not defined, then you get statistics for the entire CamelContext. The restart
   * action will restart the route. And the fail action will stop and mark the route as failed (stopped
   * due to an exception)
   */
  public fun action(action: String) {
    it.property("action", action)
  }

  /**
   * Whether to execute the control bus task asynchronously. Important: If this option is enabled,
   * then any result from the task is not set on the Exchange. This is only possible if executing tasks
   * synchronously.
   */
  public fun async(async: String) {
    it.property("async", async)
  }

  /**
   * Whether to execute the control bus task asynchronously. Important: If this option is enabled,
   * then any result from the task is not set on the Exchange. This is only possible if executing tasks
   * synchronously.
   */
  public fun async(async: Boolean) {
    it.property("async", async.toString())
  }

  /**
   * Logging level used for logging when task is done, or if any exceptions occurred during
   * processing the task.
   */
  public fun loggingLevel(loggingLevel: String) {
    it.property("loggingLevel", loggingLevel)
  }

  /**
   * The delay in millis to use when restarting a route.
   */
  public fun restartDelay(restartDelay: String) {
    it.property("restartDelay", restartDelay)
  }

  /**
   * The delay in millis to use when restarting a route.
   */
  public fun restartDelay(restartDelay: Int) {
    it.property("restartDelay", restartDelay.toString())
  }

  /**
   * To specify a route by its id. The special keyword current indicates the current route.
   */
  public fun routeId(routeId: String) {
    it.property("routeId", routeId)
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
