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

/**
 * Marshal Camel messages with attachments into MIME-Multipart messages and back.
 */
public fun DataFormatDsl.mimeMultipart(i: MimeMultipartDataFormatDsl.() -> Unit) {
  def = MimeMultipartDataFormatDsl().apply(i).def
}

@CamelDslMarker
public class MimeMultipartDataFormatDsl {
  public val def: MimeMultipartDataFormat

  init {
    def = MimeMultipartDataFormat()}

  /**
   * The id of this node
   */
  public fun id(id: String) {
    def.id = id
  }

  /**
   * Specify the subtype of the MIME Multipart. Default is mixed.
   */
  public fun multipartSubType(multipartSubType: String) {
    def.multipartSubType = multipartSubType
  }

  /**
   * Defines whether a message without attachment is also marshaled into a MIME Multipart (with only
   * one body part). Default is false.
   */
  public fun multipartWithoutAttachment(multipartWithoutAttachment: Boolean) {
    def.multipartWithoutAttachment = multipartWithoutAttachment.toString()
  }

  /**
   * Defines whether a message without attachment is also marshaled into a MIME Multipart (with only
   * one body part). Default is false.
   */
  public fun multipartWithoutAttachment(multipartWithoutAttachment: String) {
    def.multipartWithoutAttachment = multipartWithoutAttachment
  }

  /**
   * Defines whether the MIME-Multipart headers are part of the message body (true) or are set as
   * Camel headers (false). Default is false.
   */
  public fun headersInline(headersInline: Boolean) {
    def.headersInline = headersInline.toString()
  }

  /**
   * Defines whether the MIME-Multipart headers are part of the message body (true) or are set as
   * Camel headers (false). Default is false.
   */
  public fun headersInline(headersInline: String) {
    def.headersInline = headersInline
  }

  /**
   * A regex that defines which Camel headers are also included as MIME headers into the MIME
   * multipart. This will only work if headersInline is set to true. Default is to include no headers
   */
  public fun includeHeaders(includeHeaders: String) {
    def.includeHeaders = includeHeaders
  }

  /**
   * Defines whether the content of binary parts in the MIME multipart is binary (true) or Base-64
   * encoded (false) Default is false.
   */
  public fun binaryContent(binaryContent: Boolean) {
    def.binaryContent = binaryContent.toString()
  }

  /**
   * Defines whether the content of binary parts in the MIME multipart is binary (true) or Base-64
   * encoded (false) Default is false.
   */
  public fun binaryContent(binaryContent: String) {
    def.binaryContent = binaryContent
  }
}
