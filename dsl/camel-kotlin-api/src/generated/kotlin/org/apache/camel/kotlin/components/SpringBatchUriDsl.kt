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

public fun UriDsl.`spring-batch`(i: SpringBatchUriDsl.() -> Unit) {
  SpringBatchUriDsl(this).apply(i)
}

@CamelDslMarker
public class SpringBatchUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("spring-batch")
  }

  private var jobName: String = ""

  public fun jobName(jobName: String) {
    this.jobName = jobName
    it.url("$jobName")
  }

  public fun jobFromHeader(jobFromHeader: String) {
    it.property("jobFromHeader", jobFromHeader)
  }

  public fun jobFromHeader(jobFromHeader: Boolean) {
    it.property("jobFromHeader", jobFromHeader.toString())
  }

  public fun jobLauncher(jobLauncher: String) {
    it.property("jobLauncher", jobLauncher)
  }

  public fun jobRegistry(jobRegistry: String) {
    it.property("jobRegistry", jobRegistry)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
