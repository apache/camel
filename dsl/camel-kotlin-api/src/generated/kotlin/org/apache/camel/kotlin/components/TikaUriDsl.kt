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
 * Parse documents and extract metadata and text using Apache Tika.
 */
public fun UriDsl.tika(i: TikaUriDsl.() -> Unit) {
  TikaUriDsl(this).apply(i)
}

@CamelDslMarker
public class TikaUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("tika")
  }

  private var operation: String = ""

  /**
   * Operation type
   */
  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  /**
   * Tika Parse Output Encoding
   */
  public fun tikaParseOutputEncoding(tikaParseOutputEncoding: String) {
    it.property("tikaParseOutputEncoding", tikaParseOutputEncoding)
  }

  /**
   * Tika Output Format. Supported output formats. xml: Returns Parsed Content as XML. html: Returns
   * Parsed Content as HTML. text: Returns Parsed Content as Text. textMain: Uses the boilerpipe
   * library to automatically extract the main content from a web page.
   */
  public fun tikaParseOutputFormat(tikaParseOutputFormat: String) {
    it.property("tikaParseOutputFormat", tikaParseOutputFormat)
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

  /**
   * Tika Config
   */
  public fun tikaConfig(tikaConfig: String) {
    it.property("tikaConfig", tikaConfig)
  }

  /**
   * Tika Config Url
   */
  public fun tikaConfigUri(tikaConfigUri: String) {
    it.property("tikaConfigUri", tikaConfigUri)
  }
}
