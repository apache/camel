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
package org.apache.camel.kotlin.dataformats

import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.BarcodeDataFormat

/**
 * Transform strings to various 1D/2D barcode bitmap formats and back.
 */
public fun DataFormatDsl.barcode(i: BarcodeDataFormatDsl.() -> Unit) {
  def = BarcodeDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class BarcodeDataFormatDsl {
  public val def: BarcodeDataFormat

  init {
    def = BarcodeDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Barcode format such as QR-Code
   */
  public fun barcodeFormat(barcodeFormat: String) {
    def.barcodeFormat = barcodeFormat
  }

  /**
   * Image type of the barcode such as png
   */
  public fun imageType(imageType: String) {
    def.imageType = imageType
  }

  /**
   * Width of the barcode
   */
  public fun width(width: Int) {
    def.width = width.toString()
  }

  /**
   * Width of the barcode
   */
  public fun width(width: String) {
    def.width = width
  }

  /**
   * Height of the barcode
   */
  public fun height(height: Int) {
    def.height = height.toString()
  }

  /**
   * Height of the barcode
   */
  public fun height(height: String) {
    def.height = height
  }
}
