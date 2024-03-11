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
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

/**
 * Create, modify or extract content from PDF documents.
 */
public fun UriDsl.pdf(i: PdfUriDsl.() -> Unit) {
  PdfUriDsl(this).apply(i)
}

@CamelDslMarker
public class PdfUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("pdf")
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
   * Font
   */
  public fun font(font: String) {
    it.property("font", font)
  }

  /**
   * Font size in pixels
   */
  public fun fontSize(fontSize: String) {
    it.property("fontSize", fontSize)
  }

  /**
   * Font size in pixels
   */
  public fun fontSize(fontSize: Double) {
    it.property("fontSize", fontSize.toString())
  }

  /**
   * Margin bottom in pixels
   */
  public fun marginBottom(marginBottom: String) {
    it.property("marginBottom", marginBottom)
  }

  /**
   * Margin bottom in pixels
   */
  public fun marginBottom(marginBottom: Int) {
    it.property("marginBottom", marginBottom.toString())
  }

  /**
   * Margin left in pixels
   */
  public fun marginLeft(marginLeft: String) {
    it.property("marginLeft", marginLeft)
  }

  /**
   * Margin left in pixels
   */
  public fun marginLeft(marginLeft: Int) {
    it.property("marginLeft", marginLeft.toString())
  }

  /**
   * Margin right in pixels
   */
  public fun marginRight(marginRight: String) {
    it.property("marginRight", marginRight)
  }

  /**
   * Margin right in pixels
   */
  public fun marginRight(marginRight: Int) {
    it.property("marginRight", marginRight.toString())
  }

  /**
   * Margin top in pixels
   */
  public fun marginTop(marginTop: String) {
    it.property("marginTop", marginTop)
  }

  /**
   * Margin top in pixels
   */
  public fun marginTop(marginTop: Int) {
    it.property("marginTop", marginTop.toString())
  }

  /**
   * Page size
   */
  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  /**
   * Text processing to use. autoFormatting: Text is getting sliced by words, then max amount of
   * words that fits in the line will be written into pdf document. With this strategy all words that
   * doesn't fit in the line will be moved to the new line. lineTermination: Builds set of classes for
   * line-termination writing strategy. Text getting sliced by line termination symbol and then it will
   * be written regardless it fits in the line or not.
   */
  public fun textProcessingFactory(textProcessingFactory: String) {
    it.property("textProcessingFactory", textProcessingFactory)
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
