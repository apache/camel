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
package org.apache.camel.component.mail;

import javax.mail.Message;
import javax.mail.search.SearchTerm;

import com.sun.mail.imap.SortTerm;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Endpoint for Camel Mail.
 */
@UriEndpoint(scheme = "imap,imaps,pop3,pop3s,smtp,smtps", title = "IMAP,IMAPS,POP3,POP3S,SMTP,SMTPS", syntax = "imap:host:port", consumerClass = MailConsumer.class, label = "mail")
public class MailEndpoint extends ScheduledPollEndpoint {
    @UriParam
    private MailConfiguration configuration;
    @UriParam
    private MailBinding binding;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new MailHeaderFilterStrategy();
    @UriParam
    private ContentTypeResolver contentTypeResolver;
    @UriParam
    private int maxMessagesPerPoll;
    @UriParam
    private SearchTerm searchTerm;
    @UriParam
    private SortTerm[] sortTerm;
    @UriParam
    private MailBoxPostProcessAction postProcessAction;

    public MailEndpoint() {
    }

    public MailEndpoint(String uri, MailComponent component, MailConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Deprecated
    public MailEndpoint(String endpointUri, MailConfiguration configuration) {
        super(endpointUri);
        this.configuration = configuration;
    }

    public MailEndpoint(String endpointUri) {
        this(endpointUri, new MailConfiguration());
    }

    public Producer createProducer() throws Exception {
        JavaMailSender sender = configuration.getJavaMailSender();
        if (sender == null) {
            // use default mail sender
            sender = configuration.createJavaMailSender();
        }
        return createProducer(sender);
    }

    /**
     * Creates a producer using the given sender
     */
    public Producer createProducer(JavaMailSender sender) throws Exception {
        return new MailProducer(this, sender);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        if (configuration.getProtocol().startsWith("smtp")) {
            throw new IllegalArgumentException("Protocol " + configuration.getProtocol()
                    + " cannot be used for a MailConsumer. Please use another protocol such as pop3 or imap.");
        }

        // must use java mail sender impl as we need to get hold of a mail session
        JavaMailSender sender = configuration.createJavaMailSender();
        return createConsumer(processor, sender);
    }

    /**
     * Creates a consumer using the given processor and sender
     */
    public Consumer createConsumer(Processor processor, JavaMailSender sender) throws Exception {
        MailConsumer answer = new MailConsumer(this, processor, sender);

        answer.setHandleFailedMessage(configuration.isHandleFailedMessage());
        answer.setSkipFailedMessage(configuration.isSkipFailedMessage());

        // ScheduledPollConsumer default delay is 500 millis and that is too often for polling a mailbox,
        // so we override with a new default value. End user can override this value by providing a consumer.delay parameter
        answer.setDelay(MailConsumer.DEFAULT_CONSUMER_DELAY);

        answer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        configureConsumer(answer);

        return answer;
    }

    public boolean isSingleton() {
        return false;
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        return createExchange(pattern, null);
    }

    public Exchange createExchange(Message message) {
        return createExchange(getExchangePattern(), message);
    }

    private Exchange createExchange(ExchangePattern pattern, Message message) {
        Exchange exchange = new DefaultExchange(this, pattern);
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(new MailMessage(message, getConfiguration().isMapMailMessage()));
        return exchange;
    }

    // Properties
    // -------------------------------------------------------------------------

    public MailBinding getBinding() {
        if (binding == null) {
            binding = new MailBinding(headerFilterStrategy, contentTypeResolver);
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

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter headers.
     */
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
     * Specifies the maximum number of messages to gather per poll. By default, no maximum is set.
     * Can be used to set a limit of e.g. 1000 to avoid downloading thousands of files when the server starts up.
     * Set a value of 0 or negative to disable this option.
     */
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public SearchTerm getSearchTerm() {
        return searchTerm;
    }

    /**
     * Refers to a {@link javax.mail.search.SearchTerm} which allows to filter mails based on search criteria such as subject, body, from, sent after a certain date etc.
     */
    public void setSearchTerm(SearchTerm searchTerm) {
        this.searchTerm = searchTerm;
    }

    public SortTerm[] getSortTerm() {
        return sortTerm == null ? null : sortTerm.clone();
    }

    /**
     * Sorting order for messages. Only natively supported for IMAP. Emulated to some degree when using POP3
     * or when IMAP server does not have the SORT capability.
     */
    public void setSortTerm(SortTerm[] sortTerm) {
        this.sortTerm = sortTerm == null ? null : sortTerm.clone();
    }

    public MailBoxPostProcessAction getPostProcessAction() {
        return postProcessAction;
    }

    /**
     * Refers to an {@link MailBoxPostProcessAction} for doing post processing tasks on the mailbox once the normal processing ended.
     */
    public void setPostProcessAction(MailBoxPostProcessAction postProcessAction) {
        this.postProcessAction = postProcessAction;
    }
}
