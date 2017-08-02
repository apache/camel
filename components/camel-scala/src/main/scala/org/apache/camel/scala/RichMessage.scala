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

import org.apache.camel.Attachment
import org.apache.camel.Message
import javax.activation.DataHandler
import java.util
import java.util.function.Supplier

class RichMessage(val message: Message) extends Message {

  // Delegate methods
  //-------------------------------------------------------------------------

  def addAttachment(id: String, content: DataHandler) = message.addAttachment(id, content)

  def addAttachmentObject(id: String, content: Attachment) = message.addAttachmentObject(id, content)

  def copy = new RichMessage(message.copy)

  def copyAttachments(other: Message) = message.copyAttachments(other)

  def copyFrom(other: Message) = message.copyFrom(other)

  def copyFromWithNewBody(other: Message, newBody: Any) = message.copyFromWithNewBody(other, newBody)

  @Deprecated
  def createExchangeId = message.createExchangeId

  def getAttachment(id: String) = message.getAttachment(id)

  def getAttachmentObject(id: String) = message.getAttachmentObject(id)

  def getAttachmentNames = message.getAttachmentNames

  def getAttachments = message.getAttachments

  def getAttachmentObjects = message.getAttachmentObjects

  def getBody = message.getBody

  def getBody[T](bodyType: Class[T]) = message.getBody(bodyType)

  def getExchange = message.getExchange

  def getHeader(name: String) = message.getHeader(name)

  def getHeader(name: String, defaultValue: Any) = message.getHeader(name, defaultValue)

  def getHeader(name: String, defaultValueSupplier: Supplier[Object]) = message.getHeader(name, defaultValueSupplier)

  def getHeader[T](name: String, defaultValue: Any, headerType: Class[T]) = message.getHeader(name, defaultValue, headerType)

  def getHeader[T](name: String, defaultValueSupplier: Supplier[Object], headerType: Class[T]) = message.getHeader(name, defaultValueSupplier, headerType)

  def getHeader[T](name: String, headerType: Class[T]) = message.getHeader(name, headerType)

  def getHeaders = message.getHeaders

  def getMandatoryBody = message.getMandatoryBody

  def getMandatoryBody[T](bodyType: Class[T]) = message.getMandatoryBody(bodyType)

  def getMessageId = message.getMessageId

  def hasAttachments = message.hasAttachments

  def hasHeaders = message.hasHeaders

  def isFault = message.isFault

  def removeAttachment(id: String) = message.removeAttachment(id)

  def removeHeader(name: String) = message.removeHeader(name)

  def removeHeaders(pattern: String) = message.removeHeaders(pattern)

  def removeHeaders(pattern: String, excludePatterns: String*) = message.removeHeaders(pattern, excludePatterns: _*)

  def setAttachments(attachments: util.Map[String, DataHandler]) = message.setAttachments(attachments)

  def setAttachmentObjects(attachments: util.Map[String, Attachment]) = message.setAttachmentObjects(attachments)

  def setBody(body: Any) = message.setBody(body)

  def setBody[T](body: Any, bodyType: Class[T]) = message.setBody(body, bodyType)

  def setFault(fault: Boolean) = message.setFault(fault)

  def setHeader(name: String, value: Any) = message.setHeader(name, value)

  def setHeaders(headers: util.Map[String, Object]) = message.setHeaders(headers)

  def setMessageId(messageId: String) = message.setMessageId(messageId)
}
