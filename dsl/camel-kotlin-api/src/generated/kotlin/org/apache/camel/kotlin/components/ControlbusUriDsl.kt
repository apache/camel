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

  public fun command(command: String) {
    this.command = command
    it.url("$command:$language")
  }

  public fun language(language: String) {
    this.language = language
    it.url("$command:$language")
  }

  public fun action(action: String) {
    it.property("action", action)
  }

  public fun async(async: String) {
    it.property("async", async)
  }

  public fun async(async: Boolean) {
    it.property("async", async.toString())
  }

  public fun loggingLevel(loggingLevel: String) {
    it.property("loggingLevel", loggingLevel)
  }

  public fun restartDelay(restartDelay: String) {
    it.property("restartDelay", restartDelay)
  }

  public fun restartDelay(restartDelay: Int) {
    it.property("restartDelay", restartDelay.toString())
  }

  public fun routeId(routeId: String) {
    it.property("routeId", routeId)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
