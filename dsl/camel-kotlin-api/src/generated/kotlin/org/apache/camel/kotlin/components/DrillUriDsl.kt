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

public fun UriDsl.drill(i: DrillUriDsl.() -> Unit) {
  DrillUriDsl(this).apply(i)
}

@CamelDslMarker
public class DrillUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("drill")
  }

  private var host: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host")
  }

  public fun clusterId(clusterId: String) {
    it.property("clusterId", clusterId)
  }

  public fun directory(directory: String) {
    it.property("directory", directory)
  }

  public fun mode(mode: String) {
    it.property("mode", mode)
  }

  public fun port(port: String) {
    it.property("port", port)
  }

  public fun port(port: Int) {
    it.property("port", port.toString())
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
