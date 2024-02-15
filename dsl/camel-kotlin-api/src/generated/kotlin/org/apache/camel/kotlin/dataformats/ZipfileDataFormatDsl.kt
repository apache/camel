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

public fun DataFormatDsl.zipFile(i: ZipfileDataFormatDsl.() -> Unit) {
  def = ZipfileDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class ZipfileDataFormatDsl {
  public val def: ZipFileDataFormat

  init {
    def = ZipFileDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun usingIterator(usingIterator: Boolean) {
    def.usingIterator = usingIterator.toString()
  }

  public fun usingIterator(usingIterator: String) {
    def.usingIterator = usingIterator
  }

  public fun allowEmptyDirectory(allowEmptyDirectory: Boolean) {
    def.allowEmptyDirectory = allowEmptyDirectory.toString()
  }

  public fun allowEmptyDirectory(allowEmptyDirectory: String) {
    def.allowEmptyDirectory = allowEmptyDirectory
  }

  public fun preservePathElements(preservePathElements: Boolean) {
    def.preservePathElements = preservePathElements.toString()
  }

  public fun preservePathElements(preservePathElements: String) {
    def.preservePathElements = preservePathElements
  }

  public fun maxDecompressedSize(maxDecompressedSize: Long) {
    def.maxDecompressedSize = maxDecompressedSize.toString()
  }

  public fun maxDecompressedSize(maxDecompressedSize: String) {
    def.maxDecompressedSize = maxDecompressedSize
  }
}
