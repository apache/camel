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
import org.apache.camel.model.dataformat.ZipDeflaterDataFormat

/**
 * Compress and decompress streams using java.util.zip.Deflater and java.util.zip.Inflater.
 */
public fun DataFormatDsl.zipDeflater(i: ZipDeflaterDataFormatDsl.() -> Unit) {
  def = ZipDeflaterDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class ZipDeflaterDataFormatDsl {
  public val def: ZipDeflaterDataFormat

  init {
    def = ZipDeflaterDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * To specify a specific compression between 0-9. -1 is default compression, 0 is no compression,
   * and 9 is the best compression.
   */
  public fun compressionLevel(compressionLevel: Int) {
    def.compressionLevel = compressionLevel.toString()
  }

  /**
   * To specify a specific compression between 0-9. -1 is default compression, 0 is no compression,
   * and 9 is the best compression.
   */
  public fun compressionLevel(compressionLevel: String) {
    def.compressionLevel = compressionLevel
  }
}
