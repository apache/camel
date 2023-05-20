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
package org.apache.camel.component.mail;

import jakarta.mail.Message;
import jakarta.mail.search.SearchTerm;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.eclipse.angus.mail.imap.SortTerm;

import static org.apache.camel.component.mail.MailConstants.MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER;
import static org.apache.camel.component.mail.MailConstants.MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER;

/**
 * Send and receive emails using imap, pop3 and smtp protocols.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "imap,imaps,pop3,pop3s,smtp,smtps", title = "IMAP,IMAPS,POP3,POP3S,SMTP,SMTPS",
             syntax = "imap:host:port", alternativeSyntax = "imap:username:password@host:port",
             category = { Category.MAIL }, headersClass = MailConstants.class)
public class MailEndpoint extends ScheduledPollEndpoint implements HeaderFilterStrategyAware {

    @UriParam(defaultValue = "" + MailConsumer.DEFAULT_CONSUMER_DELAY, javaType = "java.time.Duration",
              label = "consumer,scheduler",
              description = "Milliseconds before the next poll.")
    private long delay = MailConsumer.DEFAULT_CONSUMER_DELAY;

    @UriParam
    private MailConfiguration configuration;
    @UriParam(label = "advanced")
    private MailBinding binding;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy = new MailHeaderFilterStrategy();
    @UriParam(label = "advanced")
    private ContentTypeResolver contentTypeResolver;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;
    @UriParam(label = "consumer,filter", prefix = "searchTerm.", multiValue = true)
    private SearchTerm searchTerm;
    @UriParam(label = "consumer,sort")
    private SortTerm[] sortTerm;
    @UriParam(label = "consumer,advanced")
    private MailBoxPostProcessAction postProcessAction;
    @UriParam(label = "consumer,filter")
    private IdempotentRepository idempotentRepository;
    @UriParam(label = "consumer,filter", defaultValue = "true")
    private boolean idempotentRepositoryRemoveOnCommit = true;
    @UriParam(label = "consumer,advanced")
    private MailUidGenerator mailUidGenerator = new DefaultMailUidGenerator();

    public MailEndpoint() {
        this(null, null, null);
    }

    public MailEndpoint(String endpointUri) {
        this(endpointUri, null, new MailConfiguration());
    }

    public MailEndpoint(String uri, MailComponent component, MailConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a mailbox,
        // so we override with a new default value. End user can override this value by providing a delay parameter
        setDelay(MailConsumer.DEFAULT_CONSUMER_DELAY);
    }

    @Override
    public Producer createProducer() throws Exception {
        JavaMailSender sender = configuration.getJavaMailSender();
        if (sender == null) {
            // use default mail sender
            sender = configuration.createJavaMailSender(getCamelContext());
        }
        return createProducer(sender);
    }

    /**
     * Creates a producer using the given sender
     */
    public Producer createProducer(JavaMailSender sender) {
        return new MailProducer(this, sender);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (configuration.getProtocol().startsWith("smtp")) {
            throw new IllegalArgumentException(
                    "Protocol " + configuration.getProtocol()
                                               + " cannot be used for a MailConsumer. Please use another protocol such as pop3 or imap.");
        }

        // must use java mail sender impl as we need to get hold of a mail session
        JavaMailSender sender = configuration.createJavaMailSender(getCamelContext());
        return createConsumer(processor, sender);
    }

    /**
     * Creates a consumer using the given processor and sender
     */
    public Consumer createConsumer(Processor processor, JavaMailSender sender) throws Exception {
        MailConsumer answer = new MailConsumer(this, processor, sender);
        answer.setHandleFailedMessage(configuration.isHandleFailedMessage());
        answer.setSkipFailedMessage(configuration.isSkipFailedMessage());
        answer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        configureConsumer(answer);
        return answer;
    }

    public Exchange createExchange(Message message) {
        Exchange exchange = super.createExchange();
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(new MailMessage(exchange, message, getConfiguration().isMapMailMessage()));
        return exchange;
    }

    // Properties
    // -------------------------------------------------------------------------

    public MailBinding getBinding() {
        if (binding == null) {
            boolean decode = getConfiguration() != null && getConfiguration().isDecodeFilename();
            boolean mapMailMessage = getConfiguration() != null && getConfiguration().isMapMailMessage();
            boolean failDuplicate = getConfiguration() != null && getConfiguration().isFailOnDuplicateFileAttachment();
            String generateMissingAttachmentNames = getConfiguration() != null
                    ? getConfiguration().getGenerateMissingAttachmentNames() : MAIL_GENERATE_MISSING_ATTACHMENT_NAMES_NEVER;
            String handleDuplicateAttachmentNames = getConfiguration() != null
                    ? getConfiguration().getHandleDuplicateAttachmentNames() : MAIL_HANDLE_DUPLICATE_ATTACHMENT_NAMES_NEVER;
            binding = new MailBinding(
                    headerFilterStrategy, contentTypeResolver, decode, mapMailMessage, failDuplicate,
                    generateMissingAttachmentNames,
                    handleDuplicateAttachmentNames);
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a Mail message
     */
    public void setBinding(MailBinding binding) {
        this.binding = binding;
    }

    public MailConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the Mail configuration
     */
    public void setConfiguration(MailConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter headers.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public ContentTypeResolver getContentTypeResolver() {
        return contentTypeResolver;
    }

    /**
     * Resolver to determine Content-Type for file attachments.
     */
    public void setContentTypeResolver(ContentTypeResolver contentTypeResolver) {
        this.contentTypeResolver = contentTypeResolver;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Specifies the maximum number of messages to gather per poll. By default, no maximum is set. Can be used to set a
     * limit of e.g. 1000 to avoid downloading thousands of files when the server starts up. Set a value of 0 or
     * negative to disable this option.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public SearchTerm getSearchTerm() {
        return searchTerm;
    }

    /**
     * Refers to a {@link jakarta.mail.search.SearchTerm} which allows to filter mails based on search criteria such as
     * subject, body, from, sent after a certain date etc.
     */
    public void setSearchTerm(SearchTerm searchTerm) {
        this.searchTerm = searchTerm;
    }

    public SortTerm[] getSortTerm() {
        return sortTerm == null ? null : sortTerm.clone();
    }

    /**
     * Sorting order for messages. Only natively supported for IMAP. Emulated to some degree when using POP3 or when
     * IMAP server does not have the SORT capability.
     */
    public void setSortTerm(SortTerm[] sortTerm) {
        this.sortTerm = sortTerm == null ? null : sortTerm.clone();
    }

    public MailBoxPostProcessAction getPostProcessAction() {
        return postProcessAction;
    }

    /**
     * Refers to an {@link MailBoxPostProcessAction} for doing post processing tasks on the mailbox once the normal
     * processing ended.
     */
    public void setPostProcessAction(MailBoxPostProcessAction postProcessAction) {
        this.postProcessAction = postProcessAction;
    }

    public IdempotentRepository getIdempotentRepository() {
        return idempotentRepository;
    }

    /**
     * A pluggable repository org.apache.camel.spi.IdempotentRepository which allows to cluster consuming from the same
     * mailbox, and let the repository coordinate whether a mail message is valid for the consumer to process.
     * <p/>
     * By default no repository is in use.
     */
    public void setIdempotentRepository(IdempotentRepository idempotentRepository) {
        this.idempotentRepository = idempotentRepository;
    }

    public boolean isIdempotentRepositoryRemoveOnCommit() {
        return idempotentRepositoryRemoveOnCommit;
    }

    /**
     * When using idempotent repository, then when the mail message has been successfully processed and is committed,
     * should the message id be removed from the idempotent repository (default) or be kept in the repository.
     * <p/>
     * By default its assumed the message id is unique and has no value to be kept in the repository, because the mail
     * message will be marked as seen/moved or deleted to prevent it from being consumed again. And therefore having the
     * message id stored in the idempotent repository has little value. However this option allows to store the message
     * id, for whatever reason you may have.
     */
    public void setIdempotentRepositoryRemoveOnCommit(boolean idempotentRepositoryRemoveOnCommit) {
        this.idempotentRepositoryRemoveOnCommit = idempotentRepositoryRemoveOnCommit;
    }

    public MailUidGenerator getMailUidGenerator() {
        return mailUidGenerator;
    }

    /**
     * A pluggable {@link MailUidGenerator} that allows to use custom logic to generate UUID of the mail message.
     */
    public void setMailUidGenerator(MailUidGenerator mailUidGenerator) {
        this.mailUidGenerator = mailUidGenerator;
    }

    /**
     * Milliseconds before the next poll.
     */
    @Override
    public void setDelay(long delay) {
        super.setDelay(delay);
        this.delay = delay;
    }
}
