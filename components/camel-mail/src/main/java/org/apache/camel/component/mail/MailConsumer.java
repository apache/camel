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

import java.util.ArrayList;
import java.util.List;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderNotFoundException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
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
public class MailConsumer extends ScheduledPollConsumer implements BatchConsumer {
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
                if (endpoint.getConfiguration().isUnseen()) {
                    messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                } else {
                    messages = folder.getMessages();
                }

                List<Exchange> exchanges = createExchanges(messages);
                processBatch(exchanges);

            } else if (count == -1) {
                throw new MessagingException("Folder: " + folder.getFullName() + " is closed");
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            // need to ensure we release resources
            try {
                if (folder.isOpen()) {
                    folder.close(true);
                }
            } catch (Exception e) {
                // some mail servers will lock the folder so we ignore in this case (CAMEL-1263)
                LOG.debug("Could not close mailbox folder: " + folder.getName(), e);
            }
        }
    }

    public void processBatch(List<Exchange> exchanges) throws Exception {
        int total = exchanges.size();
        for (int index = 0; index < total && isRunAllowed(); index++) {
            // only loop if we are started (allowed to run)
            MailExchange exchange = (MailExchange) exchanges.get(index);
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // process the current exchange
            processExchange(exchange);
            if (!exchange.isFailed()) {
                processCommit(exchange);
            } else {
                processRollback(exchange);
            }
        }
    }

    protected List<Exchange> createExchanges(Message[] messages) throws MessagingException {
        List<Exchange> answer = new ArrayList<Exchange>();

        int fetchSize = endpoint.getConfiguration().getFetchSize();
        int count = fetchSize == -1 ? messages.length : Math.min(fetchSize, messages.length);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching " + count + " messages. Total " + messages.length + " messages.");
        }

        for (int i = 0; i < count; i++) {
            Message message = messages[i];
            if (!message.getFlags().contains(Flags.Flag.DELETED)) {
                MailExchange exchange = endpoint.createExchange(message);
                answer.add(exchange);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping message as it was flagged as deleted: " + MailUtils.dumpMessage(message));
                }
            }
        }

        return answer;
    }

    /**
     * Strategy to process the mail message.
     */
    protected void processExchange(MailExchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing message: " + MailUtils.dumpMessage(exchange.getIn().getMessage()));
        }
        getProcessor().process(exchange);
    }

    /**
     * Strategy to flag the message after being processed.
     */
    protected void processCommit(MailExchange exchange) throws MessagingException {
        Message message = exchange.getIn().getMessage();

        if (endpoint.getConfiguration().isDelete()) {
            LOG.debug("Exchange processed, so flagging message as DELETED");
            message.setFlag(Flags.Flag.DELETED, true);
        } else {
            LOG.debug("Exchange processed, so flagging message as SEEN");
            message.setFlag(Flags.Flag.SEEN, true);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     */
    protected void processRollback(MailExchange exchange) throws MessagingException {
        LOG.warn("Exchange failed, so rolling back message status: " + exchange);
    }

    private void ensureIsConnected() throws MessagingException {
        MailConfiguration config = endpoint.getConfiguration();

        boolean connected = false;
        try {
            if (store != null && store.isConnected()) {
                connected = true;
            }
        } catch (Exception e) {
            LOG.debug("Exception while testing for is connected to MailStore: "
                    + endpoint.getConfiguration().getMailStoreLogInformation()
                    + ". Caused by: " + e.getMessage(), e);
        }

        if (!connected) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Connecting to MailStore: " + endpoint.getConfiguration().getMailStoreLogInformation());
            }
            store = sender.getSession().getStore(config.getProtocol());
            store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
        }

        if (folder == null) {
            folder = store.getFolder(config.getFolderName());
            if (folder == null || !folder.exists()) {
                throw new FolderNotFoundException(folder, "Folder not found or invalid: " + config.getFolderName());
            }
        }
    }

}
