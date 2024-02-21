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
import kotlin.Int
import kotlin.String
import kotlin.Unit
import org.apache.camel.kotlin.CamelDslMarker
import org.apache.camel.kotlin.UriDsl

public fun UriDsl.imap(i: ImapUriDsl.() -> Unit) {
  ImapUriDsl(this).apply(i)
}

@CamelDslMarker
public class ImapUriDsl(
  it: UriDsl,
) {
  private val it: UriDsl

  init {
    this.it = it
    this.it.component("imap")
  }

  private var host: String = ""

  private var port: String = ""

  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  public fun closeFolder(closeFolder: String) {
    it.property("closeFolder", closeFolder)
  }

  public fun closeFolder(closeFolder: Boolean) {
    it.property("closeFolder", closeFolder.toString())
  }

  public fun copyTo(copyTo: String) {
    it.property("copyTo", copyTo)
  }

  public fun decodeFilename(decodeFilename: String) {
    it.property("decodeFilename", decodeFilename)
  }

  public fun decodeFilename(decodeFilename: Boolean) {
    it.property("decodeFilename", decodeFilename.toString())
  }

  public fun delete(delete: String) {
    it.property("delete", delete)
  }

  public fun delete(delete: Boolean) {
    it.property("delete", delete.toString())
  }

  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  public fun handleFailedMessage(handleFailedMessage: String) {
    it.property("handleFailedMessage", handleFailedMessage)
  }

  public fun handleFailedMessage(handleFailedMessage: Boolean) {
    it.property("handleFailedMessage", handleFailedMessage.toString())
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  public fun mimeDecodeHeaders(mimeDecodeHeaders: String) {
    it.property("mimeDecodeHeaders", mimeDecodeHeaders)
  }

  public fun mimeDecodeHeaders(mimeDecodeHeaders: Boolean) {
    it.property("mimeDecodeHeaders", mimeDecodeHeaders.toString())
  }

  public fun moveTo(moveTo: String) {
    it.property("moveTo", moveTo)
  }

  public fun peek(peek: String) {
    it.property("peek", peek)
  }

  public fun peek(peek: Boolean) {
    it.property("peek", peek.toString())
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  public fun skipFailedMessage(skipFailedMessage: String) {
    it.property("skipFailedMessage", skipFailedMessage)
  }

  public fun skipFailedMessage(skipFailedMessage: Boolean) {
    it.property("skipFailedMessage", skipFailedMessage.toString())
  }

  public fun unseen(unseen: String) {
    it.property("unseen", unseen)
  }

  public fun unseen(unseen: Boolean) {
    it.property("unseen", unseen.toString())
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  public fun failOnDuplicateFileAttachment(failOnDuplicateFileAttachment: String) {
    it.property("failOnDuplicateFileAttachment", failOnDuplicateFileAttachment)
  }

  public fun failOnDuplicateFileAttachment(failOnDuplicateFileAttachment: Boolean) {
    it.property("failOnDuplicateFileAttachment", failOnDuplicateFileAttachment.toString())
  }

  public fun fetchSize(fetchSize: String) {
    it.property("fetchSize", fetchSize)
  }

  public fun fetchSize(fetchSize: Int) {
    it.property("fetchSize", fetchSize.toString())
  }

  public fun folderName(folderName: String) {
    it.property("folderName", folderName)
  }

  public fun generateMissingAttachmentNames(generateMissingAttachmentNames: String) {
    it.property("generateMissingAttachmentNames", generateMissingAttachmentNames)
  }

  public fun handleDuplicateAttachmentNames(handleDuplicateAttachmentNames: String) {
    it.property("handleDuplicateAttachmentNames", handleDuplicateAttachmentNames)
  }

  public fun mailUidGenerator(mailUidGenerator: String) {
    it.property("mailUidGenerator", mailUidGenerator)
  }

  public fun mapMailMessage(mapMailMessage: String) {
    it.property("mapMailMessage", mapMailMessage)
  }

  public fun mapMailMessage(mapMailMessage: Boolean) {
    it.property("mapMailMessage", mapMailMessage.toString())
  }

  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  public fun postProcessAction(postProcessAction: String) {
    it.property("postProcessAction", postProcessAction)
  }

  public fun bcc(bcc: String) {
    it.property("bcc", bcc)
  }

  public fun cc(cc: String) {
    it.property("cc", cc)
  }

  public fun from(from: String) {
    it.property("from", from)
  }

  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  public fun to(to: String) {
    it.property("to", to)
  }

  public fun javaMailSender(javaMailSender: String) {
    it.property("javaMailSender", javaMailSender)
  }

  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  public fun additionalJavaMailProperties(additionalJavaMailProperties: String) {
    it.property("additionalJavaMailProperties", additionalJavaMailProperties)
  }

  public fun alternativeBodyHeader(alternativeBodyHeader: String) {
    it.property("alternativeBodyHeader", alternativeBodyHeader)
  }

  public
      fun attachmentsContentTransferEncodingResolver(attachmentsContentTransferEncodingResolver: String) {
    it.property("attachmentsContentTransferEncodingResolver",
        attachmentsContentTransferEncodingResolver)
  }

  public fun authenticator(authenticator: String) {
    it.property("authenticator", authenticator)
  }

  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  public fun contentType(contentType: String) {
    it.property("contentType", contentType)
  }

  public fun contentTypeResolver(contentTypeResolver: String) {
    it.property("contentTypeResolver", contentTypeResolver)
  }

  public fun debugMode(debugMode: String) {
    it.property("debugMode", debugMode)
  }

  public fun debugMode(debugMode: Boolean) {
    it.property("debugMode", debugMode.toString())
  }

  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  public fun ignoreUnsupportedCharset(ignoreUnsupportedCharset: String) {
    it.property("ignoreUnsupportedCharset", ignoreUnsupportedCharset)
  }

  public fun ignoreUnsupportedCharset(ignoreUnsupportedCharset: Boolean) {
    it.property("ignoreUnsupportedCharset", ignoreUnsupportedCharset.toString())
  }

  public fun ignoreUriScheme(ignoreUriScheme: String) {
    it.property("ignoreUriScheme", ignoreUriScheme)
  }

  public fun ignoreUriScheme(ignoreUriScheme: Boolean) {
    it.property("ignoreUriScheme", ignoreUriScheme.toString())
  }

  public fun javaMailProperties(javaMailProperties: String) {
    it.property("javaMailProperties", javaMailProperties)
  }

  public fun session(session: String) {
    it.property("session", session)
  }

  public fun useInlineAttachments(useInlineAttachments: String) {
    it.property("useInlineAttachments", useInlineAttachments)
  }

  public fun useInlineAttachments(useInlineAttachments: Boolean) {
    it.property("useInlineAttachments", useInlineAttachments.toString())
  }

  public fun idempotentRepository(idempotentRepository: String) {
    it.property("idempotentRepository", idempotentRepository)
  }

  public fun idempotentRepositoryRemoveOnCommit(idempotentRepositoryRemoveOnCommit: String) {
    it.property("idempotentRepositoryRemoveOnCommit", idempotentRepositoryRemoveOnCommit)
  }

  public fun idempotentRepositoryRemoveOnCommit(idempotentRepositoryRemoveOnCommit: Boolean) {
    it.property("idempotentRepositoryRemoveOnCommit", idempotentRepositoryRemoveOnCommit.toString())
  }

  public fun searchTerm(searchTerm: String) {
    it.property("searchTerm", searchTerm)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  public fun password(password: String) {
    it.property("password", password)
  }

  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  public fun username(username: String) {
    it.property("username", username)
  }

  public fun sortTerm(sortTerm: String) {
    it.property("sortTerm", sortTerm)
  }
}
