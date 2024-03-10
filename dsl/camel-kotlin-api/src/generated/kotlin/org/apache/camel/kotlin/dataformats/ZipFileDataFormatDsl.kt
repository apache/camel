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

import kotlin.Boolean
import kotlin.Long
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.ZipFileDataFormat

/**
 * Compression and decompress streams using java.util.zip.ZipStream.
 */
public fun DataFormatDsl.zipFile(i: ZipFileDataFormatDsl.() -> Unit) {
  def = ZipFileDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class ZipFileDataFormatDsl {
  public val def: ZipFileDataFormat

  init {
    def = ZipFileDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * If the zip file has more than one entry, the setting this option to true, allows working with
   * the splitter EIP, to split the data using an iterator in a streaming mode.
   */
  public fun usingIterator(usingIterator: Boolean) {
    def.usingIterator = usingIterator.toString()
  }

  /**
   * If the zip file has more than one entry, the setting this option to true, allows working with
   * the splitter EIP, to split the data using an iterator in a streaming mode.
   */
  public fun usingIterator(usingIterator: String) {
    def.usingIterator = usingIterator
  }

  /**
   * If the zip file has more than one entry, setting this option to true, allows to get the
   * iterator even if the directory is empty
   */
  public fun allowEmptyDirectory(allowEmptyDirectory: Boolean) {
    def.allowEmptyDirectory = allowEmptyDirectory.toString()
  }

  /**
   * If the zip file has more than one entry, setting this option to true, allows to get the
   * iterator even if the directory is empty
   */
  public fun allowEmptyDirectory(allowEmptyDirectory: String) {
    def.allowEmptyDirectory = allowEmptyDirectory
  }

  /**
   * If the file name contains path elements, setting this option to true, allows the path to be
   * maintained in the zip file.
   */
  public fun preservePathElements(preservePathElements: Boolean) {
    def.preservePathElements = preservePathElements.toString()
  }

  /**
   * If the file name contains path elements, setting this option to true, allows the path to be
   * maintained in the zip file.
   */
  public fun preservePathElements(preservePathElements: String) {
    def.preservePathElements = preservePathElements
  }

  /**
   * Set the maximum decompressed size of a zip file (in bytes). The default value if not specified
   * corresponds to 1 gigabyte. An IOException will be thrown if the decompressed size exceeds this
   * amount. Set to -1 to disable setting a maximum decompressed size.
   */
  public fun maxDecompressedSize(maxDecompressedSize: Long) {
    def.maxDecompressedSize = maxDecompressedSize.toString()
  }

  /**
   * Set the maximum decompressed size of a zip file (in bytes). The default value if not specified
   * corresponds to 1 gigabyte. An IOException will be thrown if the decompressed size exceeds this
   * amount. Set to -1 to disable setting a maximum decompressed size.
   */
  public fun maxDecompressedSize(maxDecompressedSize: String) {
    def.maxDecompressedSize = maxDecompressedSize
  }
}
