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

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderNotFoundException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A {@link org.apache.camel.Consumer Consumer} which consumes messages from JavaMail using a
 * {@link javax.mail.Transport Transport} and dispatches them to the {@link Processor}
 *
 * @version $Revision$
 */
public class MailConsumer extends ScheduledPollConsumer<MailExchange> {
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;
    private static final transient Log LOG = LogFactory.getLog(MailConsumer.class);

    private final MailEndpoint endpoint;
    private final JavaMailSenderImpl sender;
    private Folder folder;
    private Store store;

    public MailConsumer(MailEndpoint endpoint, Processor processor, JavaMailSenderImpl sender) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.sender = sender;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (folder != null && folder.isOpen()) {
            folder.close(true);
        }
        if (store != null && store.isConnected()) {
            store.close();
        }

        super.doStop();
    }

    protected void poll() throws Exception {
        ensureIsConnected();

        if (store == null || folder == null) {
            throw new IllegalStateException("MailConsumer did not connect properly to the MailStore: "
                                            + endpoint.getConfiguration().getMailStoreLogInformation());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Polling mailfolder: " + endpoint.getConfiguration().getMailStoreLogInformation());
        }

        if (endpoint.getConfiguration().getFetchSize() == 0) {
            LOG.warn("Fetch size is 0 meaning the configuration is set to poll no new messages at all. Camel will skip this poll.");
            return;
        }

        // ensure folder is open
        if (!folder.isOpen()) {
            folder.open(Folder.READ_WRITE);
        }

        try {
            int count = folder.getMessageCount();
            if (count > 0) {
                Message[] messages;

                // should we process all messages or only unseen messages
                if (endpoint.getConfiguration().isProcessOnlyUnseenMessages()) {
                    messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                } else {
                    messages = folder.getMessages();
                }

                processMessages(messages);

            } else if (count == -1) {
                throw new MessagingException("Folder: " + folder.getFullName() + " is closed");
            }
        } finally {
            // need to ensure we release resources
            try {
                if (folder.isOpen()) {
                    folder.close(true);
                }
            } catch (MessagingException e) {
                // some mail servers will lock the folder so we ignore in this case (CAMEL-1263)
                LOG.debug("Could not close mailbox folder: " + folder.getName(), e);
            }
        }
    }

    protected void ensureIsConnected() throws MessagingException {
        MailConfiguration config = endpoint.getConfiguration();

        if (store == null || !store.isConnected()) {
            store = sender.getSession().getStore(config.getProtocol());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connecting to MailStore " + endpoint.getConfiguration().getMailStoreLogInformation());
            }
            store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
        }

        if (folder == null) {
            folder = store.getFolder(config.getFolderName());
            if (folder == null || !folder.exists()) {
                throw new FolderNotFoundException(folder, "Folder not found or invalid: " + config.getFolderName());
            }
        }
    }

    /**
     * Process all the messages
     */
    protected void processMessages(Message[] messages) throws Exception {
        int fetchSize = endpoint.getConfiguration().getFetchSize();
        int count = fetchSize == -1 ? messages.length : Math.min(fetchSize, messages.length);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching " + count + " messages. Total " + messages.length + " messages.");
        }

        for (int i = 0; i < count; i++) {
            Message message = messages[i];
            if (!message.getFlags().contains(Flags.Flag.DELETED)) {
                processMessage(message);
                flagMessageProcessed(message);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping message as it was flagged as deleted: " + MailUtils.dumpMessage(message));
                }
            }
        }
    }

    /**
     * Strategy to process the mail message.
     */
    protected void processMessage(Message message) throws Exception {
        MailExchange exchange = endpoint.createExchange(message);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing message: " + MailUtils.dumpMessage(message));
        }
        getProcessor().process(exchange);
    }

    /**
     * Strategy to flag the message after being processed.
     */
    protected void flagMessageProcessed(Message message) throws MessagingException {
        if (endpoint.getConfiguration().isDeleteProcessedMessages()) {
            message.setFlag(Flags.Flag.DELETED, true);
        } else {
            message.setFlag(Flags.Flag.SEEN, true);
        }
    }
}
