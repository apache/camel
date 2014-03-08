/**
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
package org.apache.camel.scala

import org.apache.camel.Message
import java.util.Map
import java.lang.{Class, String}
import javax.activation.DataHandler

class RichMessage(val message: Message) extends Message {

  // Delegate methods
  //-------------------------------------------------------------------------

  def setMessageId(messageId: String) { message.setMessageId(messageId) }

  def setHeaders(headers: Map[String, Object]) { message.setHeaders(headers) }

  def setHeader(name: String, value: Any) { message.setHeader(name, value) }

  def setFault(fault: Boolean) { message.setFault(fault) }

  def setBody[T](body: Any, bodyType : Class[T]) { message.setBody(body, bodyType) }

  def setBody(body: Any) {message.setBody()}

  def setAttachments(attachments: Map[String, DataHandler]) { message.setAttachments(attachments) }

  def removeHeader(name: String) = message.removeHeader(name)

  def removeHeaders(pattern: String) = message.removeHeaders(pattern)

  def removeHeaders(pattern: String, excludePatterns: String*) = message.removeHeaders(pattern, excludePatterns: _*)

  def removeAttachment(id: String) { message.removeAttachment(id) }

  def isFault = message.isFault

  def hasHeaders = message.hasHeaders

  def hasAttachments = message.hasAttachments

  def getMessageId = message.getMessageId

  def getMandatoryBody[T](bodyType : Class[T]) = message.getMandatoryBody(bodyType)

  def getMandatoryBody = message.getMandatoryBody

  def getHeaders = message.getHeaders

  def getHeader[T](name: String, headerType : Class[T]) = message.getHeader(name, headerType)

  def getHeader[T](name: String, defaultValue: Any, headerType : Class[T]) = message.getHeader(name, defaultValue, headerType)

  def getHeader(name: String, defaultValue: Any) = message.getHeader(name, defaultValue)

  def getHeader(name: String) = message.getHeader(name)

  def getExchange = message.getExchange

  def getBody[T](bodyType : Class[T]) = message.getBody(bodyType)

  def getBody = message.getBody

  def getAttachments = message.getAttachments

  def getAttachmentNames = message.getAttachmentNames

  def getAttachment(id: String) = message.getAttachment(id)

  @Deprecated
  def createExchangeId = message.createExchangeId

  def copyFrom(message: Message) {message.copyFrom(message)}

  def copy = new RichMessage(message.copy)

  def addAttachment(id: String, content: DataHandler) { message.addAttachment(id, content) }
}
