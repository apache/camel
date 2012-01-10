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

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderNotFoundException;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * A {@link org.apache.camel.Consumer Consumer} which consumes messages from JavaMail using a
 * {@link javax.mail.Transport Transport} and dispatches them to the {@link Processor}
 */
public class MailConsumer extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {
    public static final String POP3_UID = "CamelPop3Uid";
    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;
    private static final transient Logger LOG = LoggerFactory.getLogger(MailConsumer.class);

    private final JavaMailSenderImpl sender;
    private Folder folder;
    private Store store;
    private int maxMessagesPerPoll;
    private volatile ShutdownRunningTask shutdownRunningTask;
    private volatile int pendingExchanges;

    public MailConsumer(MailEndpoint endpoint, Processor processor, JavaMailSenderImpl sender) {
        super(endpoint, processor);
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

    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        int polledMessages = 0;

        ensureIsConnected();

        if (store == null || folder == null) {
            throw new IllegalStateException("MailConsumer did not connect properly to the MailStore: "
                    + getEndpoint().getConfiguration().getMailStoreLogInformation());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Polling mailfolder: " + getEndpoint().getConfiguration().getMailStoreLogInformation());
        }

        if (getEndpoint().getConfiguration().getFetchSize() == 0) {
            LOG.warn("Fetch size is 0 meaning the configuration is set to poll no new messages at all. Camel will skip this poll.");
            return 0;
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
                if (getEndpoint().getConfiguration().isUnseen()) {
                    messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                } else {
                    messages = folder.getMessages();
                }

                polledMessages = processBatch(CastUtils.cast(createExchanges(messages)));
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

        return polledMessages;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was " + total + " messages in this poll.");
            }
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // must use the original message in case we need to workaround a charset issue when extracting mail content
            final Message mail = exchange.getIn(MailMessage.class).getOriginalMessage();

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    processCommit(mail, exchange);
                }

                public void onFailure(Exchange exchange) {
                    processRollback(mail, exchange);
                }

                @Override
                public String toString() {
                    return "MailConsumerOnCompletion";
                }
            });

            // process the exchange
            processExchange(exchange);
        }

        return total;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    public int getPendingExchangesSize() {
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            return pendingExchanges;
        } else {
            return 0;
        }
    }

    public void prepareShutdown() {
        // noop
    }

    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    protected Queue<Exchange> createExchanges(Message[] messages) throws MessagingException {
        Queue<Exchange> answer = new LinkedList<Exchange>();

        int fetchSize = getEndpoint().getConfiguration().getFetchSize();
        int count = fetchSize == -1 ? messages.length : Math.min(fetchSize, messages.length);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching " + count + " messages. Total " + messages.length + " messages.");
        }

        for (int i = 0; i < count; i++) {
            Message message = messages[i];
            if (!message.getFlags().contains(Flags.Flag.DELETED)) {
                Exchange exchange = getEndpoint().createExchange(message);

                // If the protocol is POP3 we need to remember the uid on the exchange
                // so we can find the mail message again later to be able to delete it
                if (getEndpoint().getConfiguration().getProtocol().startsWith("pop3")) {
                    String uid = generatePop3Uid(message);
                    if (uid != null) {
                        exchange.setProperty(POP3_UID, uid);
                        LOG.trace("POP3 mail message using uid {}", uid);
                    }
                }
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
    protected void processExchange(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            MailMessage msg = (MailMessage) exchange.getIn();
            LOG.debug("Processing message: " + MailUtils.dumpMessage(msg.getMessage()));
        }
        getProcessor().process(exchange);
    }

    /**
     * Strategy to flag the message after being processed.
     *
     * @param mail the mail message
     * @param exchange the exchange
     */
    protected void processCommit(Message mail, Exchange exchange) {
        try {
            // ensure folder is open
            if (!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
            }

            // If the protocol is POP3, the message needs to be synced with the folder via the UID.
            // Otherwise setting the DELETE/SEEN flag won't delete the message.
            String uid = (String) exchange.removeProperty(POP3_UID);
            if (uid != null) {
                int count = folder.getMessageCount();
                Message found = null;
                LOG.trace("Looking for POP3Message with UID {} from folder with {} mails", uid, count);
                for (int i = 1; i <= count; ++i) {
                    Message msg = folder.getMessage(i);
                    if (uid.equals(generatePop3Uid(msg))) {
                        LOG.debug("Found POP3Message with UID {} from folder with {} mails", uid, count);
                        found = msg;
                        break;
                    }
                }

                if (found == null) {
                    boolean delete = getEndpoint().getConfiguration().isDelete();
                    LOG.warn("POP3message not found in folder. Message cannot be marked as " + (delete ? "DELETED" : "SEEN"));
                } else {
                    mail = found;
                }
            }

            if (getEndpoint().getConfiguration().isDelete()) {
                LOG.trace("Exchange processed, so flagging message as DELETED");
                mail.setFlag(Flags.Flag.DELETED, true);
            } else {
                LOG.trace("Exchange processed, so flagging message as SEEN");
                mail.setFlag(Flags.Flag.SEEN, true);
            }
        } catch (MessagingException e) {
            LOG.warn("Error occurred during flagging message as DELETED/SEEN", e);
            exchange.setException(e);
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param mail the mail message
     * @param exchange the exchange
     */
    protected void processRollback(Message mail, Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            LOG.warn("Exchange failed, so rolling back message status: " + exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: " + exchange);
        }
    }

    /**
     * Generates an UID of the POP3Message
     *
     * @param message the POP3Message
     * @return the generated uid
     */
    protected String generatePop3Uid(Message message) {
        String uid = null;

        // create an UID based on message headers on the POP3Message, that ought to be unique
        StringBuilder buffer = new StringBuilder();
        try {
            Enumeration it = message.getAllHeaders();
            while (it.hasMoreElements()) {
                Header header = (Header) it.nextElement();
                buffer.append(header.getName())
                    .append("=")
                    .append(header.getValue())
                    .append("\n");
            }
            if (buffer.length() > 0) {
                LOG.debug("Generating UID from the following:\n" + buffer);
                uid = UUID.nameUUIDFromBytes(buffer.toString().getBytes()).toString();
            }
        } catch (MessagingException e) {
            LOG.warn("Cannot reader headers from mail message. This exception will be ignored.", e);
        }

        return uid;
    }

    private void ensureIsConnected() throws MessagingException {
        MailConfiguration config = getEndpoint().getConfiguration();

        boolean connected = false;
        try {
            if (store != null && store.isConnected()) {
                connected = true;
            }
        } catch (Exception e) {
            LOG.debug("Exception while testing for is connected to MailStore: "
                    + getEndpoint().getConfiguration().getMailStoreLogInformation()
                    + ". Caused by: " + e.getMessage(), e);
        }

        if (!connected) {
            // ensure resources get recreated on reconnection
            store = null;
            folder = null;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Connecting to MailStore: " + getEndpoint().getConfiguration().getMailStoreLogInformation());
            }
            store = sender.getSession().getStore(config.getProtocol());
            store.connect(config.getHost(), config.getPort(), config.getUsername(), config.getPassword());
        }

        if (folder == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting folder " + config.getFolderName());
            }
            folder = store.getFolder(config.getFolderName());
            if (folder == null || !folder.exists()) {
                throw new FolderNotFoundException(folder, "Folder not found or invalid: " + config.getFolderName());
            }
        }
    }

    @Override
    public MailEndpoint getEndpoint() {
        return (MailEndpoint) super.getEndpoint();
    }

}
