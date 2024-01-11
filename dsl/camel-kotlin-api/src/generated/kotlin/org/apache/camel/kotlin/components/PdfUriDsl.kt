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

  public fun operation(operation: String) {
    this.operation = operation
    it.url("$operation")
  }

  public fun font(font: String) {
    it.property("font", font)
  }

  public fun fontSize(fontSize: String) {
    it.property("fontSize", fontSize)
  }

  public fun fontSize(fontSize: Double) {
    it.property("fontSize", fontSize.toString())
  }

  public fun marginBottom(marginBottom: String) {
    it.property("marginBottom", marginBottom)
  }

  public fun marginBottom(marginBottom: Int) {
    it.property("marginBottom", marginBottom.toString())
  }

  public fun marginLeft(marginLeft: String) {
    it.property("marginLeft", marginLeft)
  }

  public fun marginLeft(marginLeft: Int) {
    it.property("marginLeft", marginLeft.toString())
  }

  public fun marginRight(marginRight: String) {
    it.property("marginRight", marginRight)
  }

  public fun marginRight(marginRight: Int) {
    it.property("marginRight", marginRight.toString())
  }

  public fun marginTop(marginTop: String) {
    it.property("marginTop", marginTop)
  }

  public fun marginTop(marginTop: Int) {
    it.property("marginTop", marginTop.toString())
  }

  public fun pageSize(pageSize: String) {
    it.property("pageSize", pageSize)
  }

  public fun textProcessingFactory(textProcessingFactory: String) {
    it.property("textProcessingFactory", textProcessingFactory)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }
}
