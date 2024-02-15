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
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.DataFormatDsl
import org.apache.camel.model.dataformat.MimeMultipartDataFormat

public fun DataFormatDsl.mimeMultipart(i: MimemultipartDataFormatDsl.() -> Unit) {
  def = MimemultipartDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class MimemultipartDataFormatDsl {
  public val def: MimeMultipartDataFormat

  init {
    def = MimeMultipartDataFormat()}

  public fun id(id: String) {
    def.id = id
  }

  public fun multipartSubType(multipartSubType: String) {
    def.multipartSubType = multipartSubType
  }

  public fun multipartWithoutAttachment(multipartWithoutAttachment: Boolean) {
    def.multipartWithoutAttachment = multipartWithoutAttachment.toString()
  }

  public fun multipartWithoutAttachment(multipartWithoutAttachment: String) {
    def.multipartWithoutAttachment = multipartWithoutAttachment
  }

  public fun headersInline(headersInline: Boolean) {
    def.headersInline = headersInline.toString()
  }

  public fun headersInline(headersInline: String) {
    def.headersInline = headersInline
  }

  public fun includeHeaders(includeHeaders: String) {
    def.includeHeaders = includeHeaders
  }

  public fun binaryContent(binaryContent: Boolean) {
    def.binaryContent = binaryContent.toString()
  }

  public fun binaryContent(binaryContent: String) {
    def.binaryContent = binaryContent
  }
}
