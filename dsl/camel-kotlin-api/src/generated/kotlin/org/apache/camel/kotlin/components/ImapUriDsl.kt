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

/**
 * Send and receive emails using imap, pop3 and smtp protocols.
 */
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

  /**
   * The mail server host name
   */
  public fun host(host: String) {
    this.host = host
    it.url("$host:$port")
  }

  /**
   * The port number of the mail server
   */
  public fun port(port: String) {
    this.port = port
    it.url("$host:$port")
  }

  /**
   * The port number of the mail server
   */
  public fun port(port: Int) {
    this.port = port.toString()
    it.url("$host:$port")
  }

  /**
   * Whether the consumer should close the folder after polling. Setting this option to false and
   * having disconnect=false as well, then the consumer keeps the folder open between polls.
   */
  public fun closeFolder(closeFolder: String) {
    it.property("closeFolder", closeFolder)
  }

  /**
   * Whether the consumer should close the folder after polling. Setting this option to false and
   * having disconnect=false as well, then the consumer keeps the folder open between polls.
   */
  public fun closeFolder(closeFolder: Boolean) {
    it.property("closeFolder", closeFolder.toString())
  }

  /**
   * After processing a mail message, it can be copied to a mail folder with the given name. You can
   * override this configuration value with a header with the key copyTo, allowing you to copy messages
   * to folder names configured at runtime.
   */
  public fun copyTo(copyTo: String) {
    it.property("copyTo", copyTo)
  }

  /**
   * If set to true, the MimeUtility.decodeText method will be used to decode the filename. This is
   * similar to setting JVM system property mail.mime.encodefilename.
   */
  public fun decodeFilename(decodeFilename: String) {
    it.property("decodeFilename", decodeFilename)
  }

  /**
   * If set to true, the MimeUtility.decodeText method will be used to decode the filename. This is
   * similar to setting JVM system property mail.mime.encodefilename.
   */
  public fun decodeFilename(decodeFilename: Boolean) {
    it.property("decodeFilename", decodeFilename.toString())
  }

  /**
   * Deletes the messages after they have been processed. This is done by setting the DELETED flag
   * on the mail message. If false, the SEEN flag is set instead. You can override this configuration
   * option by setting a header with the key delete to determine if the mail should be deleted or not.
   */
  public fun delete(delete: String) {
    it.property("delete", delete)
  }

  /**
   * Deletes the messages after they have been processed. This is done by setting the DELETED flag
   * on the mail message. If false, the SEEN flag is set instead. You can override this configuration
   * option by setting a header with the key delete to determine if the mail should be deleted or not.
   */
  public fun delete(delete: Boolean) {
    it.property("delete", delete.toString())
  }

  /**
   * Whether the consumer should disconnect after polling. If enabled, this forces Camel to connect
   * on each poll.
   */
  public fun disconnect(disconnect: String) {
    it.property("disconnect", disconnect)
  }

  /**
   * Whether the consumer should disconnect after polling. If enabled, this forces Camel to connect
   * on each poll.
   */
  public fun disconnect(disconnect: Boolean) {
    it.property("disconnect", disconnect.toString())
  }

  /**
   * If the mail consumer cannot retrieve a given mail message, then this option allows handling the
   * caused exception by the consumer's error handler. By enabling the bridge error handler on the
   * consumer, then the Camel routing error handler can handle the exception instead. The default
   * behavior would be the consumer throws an exception and no mails from the batch would be able to be
   * routed by Camel.
   */
  public fun handleFailedMessage(handleFailedMessage: String) {
    it.property("handleFailedMessage", handleFailedMessage)
  }

  /**
   * If the mail consumer cannot retrieve a given mail message, then this option allows handling the
   * caused exception by the consumer's error handler. By enabling the bridge error handler on the
   * consumer, then the Camel routing error handler can handle the exception instead. The default
   * behavior would be the consumer throws an exception and no mails from the batch would be able to be
   * routed by Camel.
   */
  public fun handleFailedMessage(handleFailedMessage: Boolean) {
    it.property("handleFailedMessage", handleFailedMessage.toString())
  }

  /**
   * Specifies the maximum number of messages to gather per poll. By default, no maximum is set. Can
   * be used to set a limit of e.g. 1000 to avoid downloading thousands of files when the server starts
   * up. Set a value of 0 or negative to disable this option.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: String) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll)
  }

  /**
   * Specifies the maximum number of messages to gather per poll. By default, no maximum is set. Can
   * be used to set a limit of e.g. 1000 to avoid downloading thousands of files when the server starts
   * up. Set a value of 0 or negative to disable this option.
   */
  public fun maxMessagesPerPoll(maxMessagesPerPoll: Int) {
    it.property("maxMessagesPerPoll", maxMessagesPerPoll.toString())
  }

  /**
   * This option enables transparent MIME decoding and unfolding for mail headers.
   */
  public fun mimeDecodeHeaders(mimeDecodeHeaders: String) {
    it.property("mimeDecodeHeaders", mimeDecodeHeaders)
  }

  /**
   * This option enables transparent MIME decoding and unfolding for mail headers.
   */
  public fun mimeDecodeHeaders(mimeDecodeHeaders: Boolean) {
    it.property("mimeDecodeHeaders", mimeDecodeHeaders.toString())
  }

  /**
   * After processing a mail message, it can be moved to a mail folder with the given name. You can
   * override this configuration value with a header with the key moveTo, allowing you to move messages
   * to folder names configured at runtime.
   */
  public fun moveTo(moveTo: String) {
    it.property("moveTo", moveTo)
  }

  /**
   * Will mark the jakarta.mail.Message as peeked before processing the mail message. This applies
   * to IMAPMessage messages types only. By using peek, the mail will not be eagerly marked as SEEN on
   * the mail server, which allows us to roll back the mail message if there is a processing error in
   * Camel.
   */
  public fun peek(peek: String) {
    it.property("peek", peek)
  }

  /**
   * Will mark the jakarta.mail.Message as peeked before processing the mail message. This applies
   * to IMAPMessage messages types only. By using peek, the mail will not be eagerly marked as SEEN on
   * the mail server, which allows us to roll back the mail message if there is a processing error in
   * Camel.
   */
  public fun peek(peek: Boolean) {
    it.property("peek", peek.toString())
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: String) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle)
  }

  /**
   * If the polling consumer did not poll any files, you can enable this option to send an empty
   * message (no body) instead.
   */
  public fun sendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle: Boolean) {
    it.property("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle.toString())
  }

  /**
   * If the mail consumer cannot retrieve a given mail message, then this option allows skipping the
   * message and move on to retrieve the next mail message. The default behavior would be the consumer
   * throws an exception and no mails from the batch would be able to be routed by Camel.
   */
  public fun skipFailedMessage(skipFailedMessage: String) {
    it.property("skipFailedMessage", skipFailedMessage)
  }

  /**
   * If the mail consumer cannot retrieve a given mail message, then this option allows skipping the
   * message and move on to retrieve the next mail message. The default behavior would be the consumer
   * throws an exception and no mails from the batch would be able to be routed by Camel.
   */
  public fun skipFailedMessage(skipFailedMessage: Boolean) {
    it.property("skipFailedMessage", skipFailedMessage.toString())
  }

  /**
   * Whether to limit by unseen mails only.
   */
  public fun unseen(unseen: String) {
    it.property("unseen", unseen)
  }

  /**
   * Whether to limit by unseen mails only.
   */
  public fun unseen(unseen: Boolean) {
    it.property("unseen", unseen.toString())
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: String) {
    it.property("bridgeErrorHandler", bridgeErrorHandler)
  }

  /**
   * Allows for bridging the consumer to the Camel routing Error Handler, which mean any exceptions
   * (if possible) occurred while the Camel consumer is trying to pickup incoming messages, or the
   * likes, will now be processed as a message and handled by the routing Error Handler. Important:
   * This is only possible if the 3rd party component allows Camel to be alerted if an exception was
   * thrown. Some components handle this internally only, and therefore bridgeErrorHandler is not
   * possible. In other situations we may improve the Camel component to hook into the 3rd party
   * component and make this possible for future releases. By default the consumer will use the
   * org.apache.camel.spi.ExceptionHandler to deal with exceptions, that will be logged at WARN or
   * ERROR level and ignored.
   */
  public fun bridgeErrorHandler(bridgeErrorHandler: Boolean) {
    it.property("bridgeErrorHandler", bridgeErrorHandler.toString())
  }

  /**
   * To let the consumer use a custom ExceptionHandler. Notice if the option bridgeErrorHandler is
   * enabled then this option is not in use. By default the consumer will deal with exceptions, that
   * will be logged at WARN or ERROR level and ignored.
   */
  public fun exceptionHandler(exceptionHandler: String) {
    it.property("exceptionHandler", exceptionHandler)
  }

  /**
   * Sets the exchange pattern when the consumer creates an exchange.
   */
  public fun exchangePattern(exchangePattern: String) {
    it.property("exchangePattern", exchangePattern)
  }

  /**
   * Whether to fail processing the mail if the mail message contains attachments with duplicate
   * file names. If set to false, then the duplicate attachment is skipped and a WARN is logged. If set
   * to true, then an exception is thrown failing to process the mail message.
   */
  public fun failOnDuplicateFileAttachment(failOnDuplicateFileAttachment: String) {
    it.property("failOnDuplicateFileAttachment", failOnDuplicateFileAttachment)
  }

  /**
   * Whether to fail processing the mail if the mail message contains attachments with duplicate
   * file names. If set to false, then the duplicate attachment is skipped and a WARN is logged. If set
   * to true, then an exception is thrown failing to process the mail message.
   */
  public fun failOnDuplicateFileAttachment(failOnDuplicateFileAttachment: Boolean) {
    it.property("failOnDuplicateFileAttachment", failOnDuplicateFileAttachment.toString())
  }

  /**
   * Sets the maximum number of messages to consume during a poll. This can be used to avoid
   * overloading a mail server, if a mailbox folder contains a lot of messages. The default value of -1
   * means no fetch size and all messages will be consumed. Setting the value to 0 is a special corner
   * case, where Camel will not consume any messages at all.
   */
  public fun fetchSize(fetchSize: String) {
    it.property("fetchSize", fetchSize)
  }

  /**
   * Sets the maximum number of messages to consume during a poll. This can be used to avoid
   * overloading a mail server, if a mailbox folder contains a lot of messages. The default value of -1
   * means no fetch size and all messages will be consumed. Setting the value to 0 is a special corner
   * case, where Camel will not consume any messages at all.
   */
  public fun fetchSize(fetchSize: Int) {
    it.property("fetchSize", fetchSize.toString())
  }

  /**
   * The folder to poll.
   */
  public fun folderName(folderName: String) {
    it.property("folderName", folderName)
  }

  /**
   * Set this to 'uuid' to set a UUID for the filename of the attachment if no filename was set
   */
  public fun generateMissingAttachmentNames(generateMissingAttachmentNames: String) {
    it.property("generateMissingAttachmentNames", generateMissingAttachmentNames)
  }

  /**
   * Set the strategy to handle duplicate filenames of attachments never: attachments that have a
   * filename which is already present in the attachments will be ignored unless
   * failOnDuplicateFileAttachment is set to true. uuidPrefix: this will prefix the duplicate
   * attachment filenames each with an uuid and underscore (uuid_filename.fileextension). uuidSuffix:
   * this will suffix the duplicate attachment filenames each with an underscore and uuid
   * (filename_uuid.fileextension).
   */
  public fun handleDuplicateAttachmentNames(handleDuplicateAttachmentNames: String) {
    it.property("handleDuplicateAttachmentNames", handleDuplicateAttachmentNames)
  }

  /**
   * A pluggable MailUidGenerator that allows to use custom logic to generate UUID of the mail
   * message.
   */
  public fun mailUidGenerator(mailUidGenerator: String) {
    it.property("mailUidGenerator", mailUidGenerator)
  }

  /**
   * Specifies whether Camel should map the received mail message to Camel body/headers/attachments.
   * If set to true, the body of the mail message is mapped to the body of the Camel IN message, the
   * mail headers are mapped to IN headers, and the attachments to Camel IN attachment message. If this
   * option is set to false, then the IN message contains a raw jakarta.mail.Message. You can retrieve
   * this raw message by calling exchange.getIn().getBody(jakarta.mail.Message.class).
   */
  public fun mapMailMessage(mapMailMessage: String) {
    it.property("mapMailMessage", mapMailMessage)
  }

  /**
   * Specifies whether Camel should map the received mail message to Camel body/headers/attachments.
   * If set to true, the body of the mail message is mapped to the body of the Camel IN message, the
   * mail headers are mapped to IN headers, and the attachments to Camel IN attachment message. If this
   * option is set to false, then the IN message contains a raw jakarta.mail.Message. You can retrieve
   * this raw message by calling exchange.getIn().getBody(jakarta.mail.Message.class).
   */
  public fun mapMailMessage(mapMailMessage: Boolean) {
    it.property("mapMailMessage", mapMailMessage.toString())
  }

  /**
   * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom
   * implementation to control error handling usually occurred during the poll operation before an
   * Exchange have been created and being routed in Camel.
   */
  public fun pollStrategy(pollStrategy: String) {
    it.property("pollStrategy", pollStrategy)
  }

  /**
   * Refers to an MailBoxPostProcessAction for doing post processing tasks on the mailbox once the
   * normal processing ended.
   */
  public fun postProcessAction(postProcessAction: String) {
    it.property("postProcessAction", postProcessAction)
  }

  /**
   * Sets the BCC email address. Separate multiple email addresses with comma.
   */
  public fun bcc(bcc: String) {
    it.property("bcc", bcc)
  }

  /**
   * Sets the CC email address. Separate multiple email addresses with comma.
   */
  public fun cc(cc: String) {
    it.property("cc", cc)
  }

  /**
   * The from email address
   */
  public fun from(from: String) {
    it.property("from", from)
  }

  /**
   * The Reply-To recipients (the receivers of the response mail). Separate multiple email addresses
   * with a comma.
   */
  public fun replyTo(replyTo: String) {
    it.property("replyTo", replyTo)
  }

  /**
   * The Subject of the message being sent. Note: Setting the subject in the header takes precedence
   * over this option.
   */
  public fun subject(subject: String) {
    it.property("subject", subject)
  }

  /**
   * Sets the destination email address. Separate multiple email addresses with comma.
   */
  public fun to(to: String) {
    it.property("to", to)
  }

  /**
   * To use a custom org.apache.camel.component.mail.JavaMailSender for sending emails.
   */
  public fun javaMailSender(javaMailSender: String) {
    it.property("javaMailSender", javaMailSender)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: String) {
    it.property("lazyStartProducer", lazyStartProducer)
  }

  /**
   * Whether the producer should be started lazy (on the first message). By starting lazy you can
   * use this to allow CamelContext and routes to startup in situations where a producer may otherwise
   * fail during starting and cause the route to fail being started. By deferring this startup to be
   * lazy then the startup failure can be handled during routing messages via Camel's routing error
   * handlers. Beware that when the first message is processed then creating and starting the producer
   * may take a little time and prolong the total processing time of the processing.
   */
  public fun lazyStartProducer(lazyStartProducer: Boolean) {
    it.property("lazyStartProducer", lazyStartProducer.toString())
  }

  /**
   * Sets additional java mail properties, that will append/override any default properties that are
   * set based on all the other options. This is useful if you need to add some special options but
   * want to keep the others as is.
   */
  public fun additionalJavaMailProperties(additionalJavaMailProperties: String) {
    it.property("additionalJavaMailProperties", additionalJavaMailProperties)
  }

  /**
   * Specifies the key to an IN message header that contains an alternative email body. For example,
   * if you send emails in text/html format and want to provide an alternative mail body for non-HTML
   * email clients, set the alternative mail body with this key as a header.
   */
  public fun alternativeBodyHeader(alternativeBodyHeader: String) {
    it.property("alternativeBodyHeader", alternativeBodyHeader)
  }

  /**
   * To use a custom AttachmentsContentTransferEncodingResolver to resolve what
   * content-type-encoding to use for attachments.
   */
  public
      fun attachmentsContentTransferEncodingResolver(attachmentsContentTransferEncodingResolver: String) {
    it.property("attachmentsContentTransferEncodingResolver",
        attachmentsContentTransferEncodingResolver)
  }

  /**
   * The authenticator for login. If set then the password and username are ignored. It can be used
   * for tokens which can expire and therefore must be read dynamically.
   */
  public fun authenticator(authenticator: String) {
    it.property("authenticator", authenticator)
  }

  /**
   * Sets the binding used to convert from a Camel message to and from a Mail message
   */
  public fun binding(binding: String) {
    it.property("binding", binding)
  }

  /**
   * The connection timeout in milliseconds.
   */
  public fun connectionTimeout(connectionTimeout: String) {
    it.property("connectionTimeout", connectionTimeout)
  }

  /**
   * The connection timeout in milliseconds.
   */
  public fun connectionTimeout(connectionTimeout: Int) {
    it.property("connectionTimeout", connectionTimeout.toString())
  }

  /**
   * The mail message content type. Use text/html for HTML mails.
   */
  public fun contentType(contentType: String) {
    it.property("contentType", contentType)
  }

  /**
   * Resolver to determine Content-Type for file attachments.
   */
  public fun contentTypeResolver(contentTypeResolver: String) {
    it.property("contentTypeResolver", contentTypeResolver)
  }

  /**
   * Enable debug mode on the underlying mail framework. The SUN Mail framework logs the debug
   * messages to System.out by default.
   */
  public fun debugMode(debugMode: String) {
    it.property("debugMode", debugMode)
  }

  /**
   * Enable debug mode on the underlying mail framework. The SUN Mail framework logs the debug
   * messages to System.out by default.
   */
  public fun debugMode(debugMode: Boolean) {
    it.property("debugMode", debugMode.toString())
  }

  /**
   * To use a custom org.apache.camel.spi.HeaderFilterStrategy to filter headers.
   */
  public fun headerFilterStrategy(headerFilterStrategy: String) {
    it.property("headerFilterStrategy", headerFilterStrategy)
  }

  /**
   * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the
   * charset is unsupported, then charset=XXX (where XXX represents the unsupported charset) is removed
   * from the content-type, and it relies on the platform default instead.
   */
  public fun ignoreUnsupportedCharset(ignoreUnsupportedCharset: String) {
    it.property("ignoreUnsupportedCharset", ignoreUnsupportedCharset)
  }

  /**
   * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the
   * charset is unsupported, then charset=XXX (where XXX represents the unsupported charset) is removed
   * from the content-type, and it relies on the platform default instead.
   */
  public fun ignoreUnsupportedCharset(ignoreUnsupportedCharset: Boolean) {
    it.property("ignoreUnsupportedCharset", ignoreUnsupportedCharset.toString())
  }

  /**
   * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the
   * charset is unsupported, then charset=XXX (where XXX represents the unsupported charset) is removed
   * from the content-type, and it relies on the platform default instead.
   */
  public fun ignoreUriScheme(ignoreUriScheme: String) {
    it.property("ignoreUriScheme", ignoreUriScheme)
  }

  /**
   * Option to let Camel ignore unsupported charset in the local JVM when sending mails. If the
   * charset is unsupported, then charset=XXX (where XXX represents the unsupported charset) is removed
   * from the content-type, and it relies on the platform default instead.
   */
  public fun ignoreUriScheme(ignoreUriScheme: Boolean) {
    it.property("ignoreUriScheme", ignoreUriScheme.toString())
  }

  /**
   * Sets the java mail options. Will clear any default properties and only use the properties
   * provided for this method.
   */
  public fun javaMailProperties(javaMailProperties: String) {
    it.property("javaMailProperties", javaMailProperties)
  }

  /**
   * Specifies the mail session that camel should use for all mail interactions. Useful in scenarios
   * where mail sessions are created and managed by some other resource, such as a JavaEE container.
   * When using a custom mail session, then the hostname and port from the mail session will be used
   * (if configured on the session).
   */
  public fun session(session: String) {
    it.property("session", session)
  }

  /**
   * Whether to use disposition inline or attachment.
   */
  public fun useInlineAttachments(useInlineAttachments: String) {
    it.property("useInlineAttachments", useInlineAttachments)
  }

  /**
   * Whether to use disposition inline or attachment.
   */
  public fun useInlineAttachments(useInlineAttachments: Boolean) {
    it.property("useInlineAttachments", useInlineAttachments.toString())
  }

  /**
   * A pluggable repository org.apache.camel.spi.IdempotentRepository which allows to cluster
   * consuming from the same mailbox, and let the repository coordinate whether a mail message is valid
   * for the consumer to process. By default no repository is in use.
   */
  public fun idempotentRepository(idempotentRepository: String) {
    it.property("idempotentRepository", idempotentRepository)
  }

  /**
   * When using idempotent repository, then when the mail message has been successfully processed
   * and is committed, should the message id be removed from the idempotent repository (default) or be
   * kept in the repository. By default its assumed the message id is unique and has no value to be
   * kept in the repository, because the mail message will be marked as seen/moved or deleted to
   * prevent it from being consumed again. And therefore having the message id stored in the idempotent
   * repository has little value. However this option allows to store the message id, for whatever
   * reason you may have.
   */
  public fun idempotentRepositoryRemoveOnCommit(idempotentRepositoryRemoveOnCommit: String) {
    it.property("idempotentRepositoryRemoveOnCommit", idempotentRepositoryRemoveOnCommit)
  }

  /**
   * When using idempotent repository, then when the mail message has been successfully processed
   * and is committed, should the message id be removed from the idempotent repository (default) or be
   * kept in the repository. By default its assumed the message id is unique and has no value to be
   * kept in the repository, because the mail message will be marked as seen/moved or deleted to
   * prevent it from being consumed again. And therefore having the message id stored in the idempotent
   * repository has little value. However this option allows to store the message id, for whatever
   * reason you may have.
   */
  public fun idempotentRepositoryRemoveOnCommit(idempotentRepositoryRemoveOnCommit: Boolean) {
    it.property("idempotentRepositoryRemoveOnCommit", idempotentRepositoryRemoveOnCommit.toString())
  }

  /**
   * Refers to a jakarta.mail.search.SearchTerm which allows to filter mails based on search
   * criteria such as subject, body, from, sent after a certain date etc.
   */
  public fun searchTerm(searchTerm: String) {
    it.property("searchTerm", searchTerm)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: String) {
    it.property("backoffErrorThreshold", backoffErrorThreshold)
  }

  /**
   * The number of subsequent error polls (failed due some error) that should happen before the
   * backoffMultipler should kick-in.
   */
  public fun backoffErrorThreshold(backoffErrorThreshold: Int) {
    it.property("backoffErrorThreshold", backoffErrorThreshold.toString())
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: String) {
    it.property("backoffIdleThreshold", backoffIdleThreshold)
  }

  /**
   * The number of subsequent idle polls that should happen before the backoffMultipler should
   * kick-in.
   */
  public fun backoffIdleThreshold(backoffIdleThreshold: Int) {
    it.property("backoffIdleThreshold", backoffIdleThreshold.toString())
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: String) {
    it.property("backoffMultiplier", backoffMultiplier)
  }

  /**
   * To let the scheduled polling consumer backoff if there has been a number of subsequent
   * idles/errors in a row. The multiplier is then the number of polls that will be skipped before the
   * next actual attempt is happening again. When this option is in use then backoffIdleThreshold
   * and/or backoffErrorThreshold must also be configured.
   */
  public fun backoffMultiplier(backoffMultiplier: Int) {
    it.property("backoffMultiplier", backoffMultiplier.toString())
  }

  /**
   * Milliseconds before the next poll.
   */
  public fun delay(delay: String) {
    it.property("delay", delay)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: String) {
    it.property("greedy", greedy)
  }

  /**
   * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the
   * previous run polled 1 or more messages.
   */
  public fun greedy(greedy: Boolean) {
    it.property("greedy", greedy.toString())
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: String) {
    it.property("initialDelay", initialDelay)
  }

  /**
   * Milliseconds before the first poll starts.
   */
  public fun initialDelay(initialDelay: Int) {
    it.property("initialDelay", initialDelay.toString())
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: String) {
    it.property("repeatCount", repeatCount)
  }

  /**
   * Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only
   * fire once. If you set it to 5, it will only fire five times. A value of zero or negative means
   * fire forever.
   */
  public fun repeatCount(repeatCount: Int) {
    it.property("repeatCount", repeatCount.toString())
  }

  /**
   * The consumer logs a start/complete log line when it polls. This option allows you to configure
   * the logging level for that.
   */
  public fun runLoggingLevel(runLoggingLevel: String) {
    it.property("runLoggingLevel", runLoggingLevel)
  }

  /**
   * Allows for configuring a custom/shared thread pool to use for the consumer. By default each
   * consumer has its own single threaded thread pool.
   */
  public fun scheduledExecutorService(scheduledExecutorService: String) {
    it.property("scheduledExecutorService", scheduledExecutorService)
  }

  /**
   * To use a cron scheduler from either camel-spring or camel-quartz component. Use value spring or
   * quartz for built in scheduler
   */
  public fun scheduler(scheduler: String) {
    it.property("scheduler", scheduler)
  }

  /**
   * To configure additional properties when using a custom scheduler or any of the Quartz, Spring
   * based scheduler.
   */
  public fun schedulerProperties(schedulerProperties: String) {
    it.property("schedulerProperties", schedulerProperties)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: String) {
    it.property("startScheduler", startScheduler)
  }

  /**
   * Whether the scheduler should be auto started.
   */
  public fun startScheduler(startScheduler: Boolean) {
    it.property("startScheduler", startScheduler.toString())
  }

  /**
   * Time unit for initialDelay and delay options.
   */
  public fun timeUnit(timeUnit: String) {
    it.property("timeUnit", timeUnit)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: String) {
    it.property("useFixedDelay", useFixedDelay)
  }

  /**
   * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
   */
  public fun useFixedDelay(useFixedDelay: Boolean) {
    it.property("useFixedDelay", useFixedDelay.toString())
  }

  /**
   * The password for login. See also setAuthenticator(MailAuthenticator).
   */
  public fun password(password: String) {
    it.property("password", password)
  }

  /**
   * To configure security using SSLContextParameters.
   */
  public fun sslContextParameters(sslContextParameters: String) {
    it.property("sslContextParameters", sslContextParameters)
  }

  /**
   * The username for login. See also setAuthenticator(MailAuthenticator).
   */
  public fun username(username: String) {
    it.property("username", username)
  }

  /**
   * Sorting order for messages. Only natively supported for IMAP. Emulated to some degree when
   * using POP3 or when IMAP server does not have the SORT capability.
   */
  public fun sortTerm(sortTerm: String) {
    it.property("sortTerm", sortTerm)
  }
}
