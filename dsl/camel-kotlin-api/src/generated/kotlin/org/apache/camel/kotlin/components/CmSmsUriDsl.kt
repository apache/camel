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
 * Send SMS messages via CM SMS Gateway.
 */
public fun UriDsl.`cm-sms`(i: CmSmsUriDsl.() -> Unit) {
  CmSmsUriDsl(this).apply(i)
}

@CamelDslMarker
public class CmSmsUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("cm-sms")
  }

  private var host: String = ""

  /**
   * SMS Provider HOST with scheme
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host")
  }

  /**
   * This is the sender name. The maximum length is 11 characters.
   */
  public fun defaultFrom(defaultFrom: String) {
    it.property("defaultFrom", defaultFrom)
  }

  /**
   * If it is a multipart message forces the max number. Message can be truncated. Technically the
   * gateway will first check if a message is larger than 160 characters, if so, the message will be
   * cut into multiple 153 characters parts limited by these parameters.
   */
  public fun defaultMaxNumberOfParts(defaultMaxNumberOfParts: String) {
    it.property("defaultMaxNumberOfParts", defaultMaxNumberOfParts)
  }

  /**
   * If it is a multipart message forces the max number. Message can be truncated. Technically the
   * gateway will first check if a message is larger than 160 characters, if so, the message will be
   * cut into multiple 153 characters parts limited by these parameters.
   */
  public fun defaultMaxNumberOfParts(defaultMaxNumberOfParts: Int) {
    it.property("defaultMaxNumberOfParts", defaultMaxNumberOfParts.toString())
  }

  /**
   * The unique token to use
   */
  public fun productToken(productToken: String) {
    it.property("productToken", productToken)
  }

  /**
   * Whether to test the connection to the SMS Gateway on startup
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: String) {
    it.property("testConnectionOnStartup", testConnectionOnStartup)
  }

  /**
   * Whether to test the connection to the SMS Gateway on startup
   */
  public fun testConnectionOnStartup(testConnectionOnStartup: Boolean) {
    it.property("testConnectionOnStartup", testConnectionOnStartup.toString())
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
