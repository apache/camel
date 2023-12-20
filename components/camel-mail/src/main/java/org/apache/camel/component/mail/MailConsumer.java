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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Store;
import jakarta.mail.search.SearchTerm;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.IMAPStore;
import org.eclipse.angus.mail.imap.SortTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer Consumer} which consumes messages from JavaMail using a
 * {@link jakarta.mail.Transport Transport} and dispatches them to the {@link Processor}
 */
public class MailConsumer extends ScheduledBatchPollingConsumer {
    public static final String MAIL_MESSAGE_UID = "CamelMailMessageId";

    public static final long DEFAULT_CONSUMER_DELAY = 60 * 1000L;

    private static final Logger LOG = LoggerFactory.getLogger(MailConsumer.class);

    private final JavaMailSender sender;
    private Folder folder;
    private Store store;
    private boolean skipFailedMessage;
    private boolean handleFailedMessage;

    /**
     * Is true if server is an IMAP server and supports IMAP SORT extension.
     */
    private boolean serverCanSort;

    public MailConsumer(MailEndpoint endpoint, Processor processor, JavaMailSender sender) {
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

    /**
     * Returns the max number of messages to be processed. Will return -1 if no maximum is set
     */
    private int getMaxNumberOfMessages() {
        int fetchSize = getEndpoint().getConfiguration().getFetchSize();
        if (hasMessageLimit(fetchSize)) {
            return fetchSize;
        }

        int maximumMessagesPerPoll = (getMaxMessagesPerPoll() == 0) ? -1 : getMaxMessagesPerPoll();
        if (hasMessageLimit(maximumMessagesPerPoll)) {
            return maximumMessagesPerPoll;
        }

        return -1;
    }

    private boolean hasMessageLimit(int limitValue) {
        return limitValue >= 0;
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;
        int polledMessages = 0;

        ensureIsConnected();

        if (store == null || folder == null) {
            throw new IllegalStateException(
                    "MailConsumer did not connect properly to the MailStore: "
                                            + getEndpoint().getConfiguration().getMailStoreLogInformation());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Polling mailbox folder: {}", getEndpoint().getConfiguration().getMailStoreLogInformation());
        }

        if (getEndpoint().getConfiguration().getFetchSize() == 0) {
            LOG.warn(
                    "Fetch size is 0 meaning the configuration is set to poll no new messages at all. Camel will skip this poll.");
            return 0;
        }

        // ensure folder is open
        try {
            if (!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
            }
        } catch (MessagingException e) {
            // some kind of connectivity error, so lets re-create connection
            String msg = "Error opening mail folder due to " + e.getMessage() + ". Will re-create connection on next poll.";
            LOG.warn(msg);
            if (LOG.isDebugEnabled()) {
                LOG.debug(msg, e);
            }
            disconnect();
            return 0; // return since we cannot poll mail messages, but will re-connect on next poll.
        }

        // okay consumer is connected to the mail server
        forceConsumerAsReady();

        try {
            int count = folder.getMessageCount();
            if (count > 0) {
                Queue<Exchange> messages = retrieveMessages();
                polledMessages = processBatch(CastUtils.cast(messages));

                final MailBoxPostProcessAction postProcessor = getEndpoint().getPostProcessAction();
                if (postProcessor != null) {
                    postProcessor.process(folder);
                }
            } else if (count == -1) {
                throw new MessagingException("Folder: " + folder.getFullName() + " is closed");
            }
        } catch (Exception e) {
            handleException(e);
        } finally {
            // need to ensure we release resources, but only if closeFolder or disconnect = true
            if (getEndpoint().getConfiguration().isCloseFolder() || getEndpoint().getConfiguration().isDisconnect()) {
                try {
                    if (folder != null && folder.isOpen()) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Close mailbox folder {} from {}", folder.getName(),
                                    getEndpoint().getConfiguration().getMailStoreLogInformation());
                        }
                        folder.close(true);
                    }
                } catch (Exception e) {
                    // some mail servers will lock the folder so we ignore in this case (CAMEL-1263)
                    LOG.debug("Could not close mailbox folder: {}. This exception is ignored.", folder.getName(), e);
                }
            }
        }

        // should we disconnect, the header can override the configuration
        boolean disconnect = getEndpoint().getConfiguration().isDisconnect();
        if (disconnect) {
            disconnect();
        }

        return polledMessages;
    }

    private void disconnect() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Disconnecting from {}", getEndpoint().getConfiguration().getMailStoreLogInformation());
        }
        try {
            if (store != null) {
                store.close();
            }
        } catch (Exception e) {
            LOG.debug("Could not disconnect from {}. This exception is ignored.",
                    getEndpoint().getConfiguration().getMailStoreLogInformation(), e);
        }
        store = null;
        folder = null;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // must use the original message in case we need to workaround a charset issue when extracting mail content
            final Message mail = exchange.getIn(MailMessage.class).getOriginalMessage();

            // add on completion to handle after work when the exchange is done
            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onComplete(Exchange exchange) {
                    processCommit(mail, exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    processRollback(mail, exchange);
                }

                @Override
                public boolean allowHandover() {
                    // do not allow handover as the commit/rollback logic needs to be executed
                    // on the same session that polled the messages
                    return false;
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

    private void peekMessage(Message mail) {
        // this only applies to IMAP messages which has a setPeek method
        if (mail.getClass().getSimpleName().startsWith("IMAP")) {
            try {
                LOG.trace("Calling setPeek(true) on mail message {}", mail);
                BeanIntrospection beanIntrospection
                        = PluginHelper.getBeanIntrospection(getEndpoint().getCamelContext());
                beanIntrospection.setProperty(getEndpoint().getCamelContext(), mail, "peek", true);
            } catch (Exception e) {
                // ignore
                LOG.trace("Error setting peak property to true on: {}. This exception is ignored.", mail, e);
            }
        }
    }

    /**
     * @return                    Messages from input folder according to the search and sort criteria stored in the
     *                            endpoint
     * @throws MessagingException If message retrieval fails
     */
    private Queue<Exchange> retrieveMessages() throws MessagingException {
        Queue<Exchange> answer = new LinkedList<>();

        Message[] messages;
        final SortTerm[] sortTerm = getEndpoint().getSortTerm();
        final SearchTerm searchTerm = computeSearchTerm();
        if (sortTerm != null && serverCanSort) {
            final IMAPFolder imapFolder = (IMAPFolder) folder;
            if (searchTerm != null) {
                // Sort and search using server capability
                messages = imapFolder.getSortedMessages(sortTerm, searchTerm);
            } else {
                // Only sort using server capability
                messages = imapFolder.getSortedMessages(sortTerm);
            }
        } else {
            if (searchTerm != null) {
                messages = folder.search(searchTerm, retrieveAllMessages());
            } else {
                messages = retrieveAllMessages();
            }
            // Now we can sort (emulate email sort but restrict sort terms)
            if (sortTerm != null) {
                MailSorter.sortMessages(messages, sortTerm);
            }
        }

        int maxMessage = getMaxNumberOfMessages();
        boolean hasMessageLimit = hasMessageLimit(maxMessage);
        for (Message message : messages) {
            if (hasMessageLimit && answer.size() >= maxMessage) {
                break;
            }
            String key = getEndpoint().getMailUidGenerator().generateUuid(getEndpoint(), message);
            if (isValidMessage(key, message)) {
                // need to call setPeek on java-mail to avoid the message being flagged eagerly as SEEN on the server in case
                // we process the message and rollback due an exception
                if (getEndpoint().getConfiguration().isPeek()) {
                    peekMessage(message);
                }
                Exchange exchange = createExchange(new KeyValueHolder<>(key, message));
                if (exchange != null) {
                    answer.add(exchange);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Fetching {} messages. Total {} messages.", answer.size(), messages.length);
        }

        return answer;
    }

    private Message[] retrieveAllMessages() throws MessagingException {
        int total = folder.getMessageCount();
        List<Message> msgs = new ArrayList<>();

        // Note that message * numbers start at 1, not 0
        for (int i = 1; i <= total; i++) {
            try {
                Message msg = folder.getMessage(i);
                msgs.add(msg);
            } catch (Exception e) {
                if (skipFailedMessage) {
                    LOG.debug("Skipping failed message at index {} due {}", i, e.getMessage(), e);
                } else if (handleFailedMessage) {
                    handleException(e);
                } else {
                    throw e;
                }
            }
        }
        return msgs.toArray(new Message[0]);
    }

    private boolean isValidMessage(String key, Message msg) {
        boolean answer = true;

        if (getEndpoint().getIdempotentRepository() != null) {
            if (!getEndpoint().getIdempotentRepository().add(key)) {
                LOG.trace(
                        "This consumer is idempotent and the mail message has been consumed before matching idempotentKey: {}. Will skip this message: {}",
                        key, msg);
                answer = false;
            }
        }

        LOG.debug("Message: {} with key: {} is valid: {}", msg, key, answer);
        return answer;
    }

    /**
     * @return Search term from endpoint (including "seen" check) or null if there is no search term
     */
    private SearchTerm computeSearchTerm() {
        if (getEndpoint().getSearchTerm() != null) {
            return getEndpoint().getSearchTerm();
        } else if (getEndpoint().getConfiguration().isUnseen()) {
            return new SearchTermBuilder().unseen().build();
        }
        return null;
    }

    protected Exchange createExchange(KeyValueHolder<String, Message> holder) throws MessagingException {
        try {
            String key = holder.getKey();
            Message message = holder.getValue();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Mail is of type: {} - {}", ObjectHelper.classCanonicalName(message), message);
            }

            if (!message.getFlags().contains(Flags.Flag.DELETED)) {
                Exchange exchange = createExchange(message);
                if (getEndpoint().getConfiguration().isMapMailMessage()) {
                    // ensure the mail message is mapped, which can be ensured by touching the body/header/attachment
                    LOG.trace("Mapping from jakarta.mail.Message to Camel MailMessage");
                    exchange.getIn().getBody();
                    exchange.getIn().getHeaders();
                    // must also map attachments
                    try {
                        Map<String, Attachment> att = new HashMap<>();
                        getEndpoint().getBinding().extractAttachmentsFromMail(message, att);
                        if (!att.isEmpty()) {
                            exchange.getIn(AttachmentMessage.class).setAttachmentObjects(att);
                        }
                    } catch (MessagingException | IOException e) {
                        // must release exchange before throwing exception
                        releaseExchange(exchange, true);
                        throw new RuntimeCamelException("Error accessing attachments due to: " + e.getMessage(), e);
                    }
                }

                // If the protocol is POP3 we need to remember the uid on the exchange
                // so we can find the mail message again later to be able to delete it
                // we also need to remember the UUID for idempotent repository
                exchange.setProperty(MAIL_MESSAGE_UID, key);

                return exchange;
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping message as it was flagged as deleted: {}", MailUtils.dumpMessage(message));
                }
            }
        } catch (Exception e) {
            if (skipFailedMessage) {
                LOG.debug("Skipping failed message due {}", e.getMessage(), e);
            } else if (handleFailedMessage) {
                handleException(e);
            } else {
                throw e;
            }
        }
        return null;
    }

    /**
     * Strategy to process the mail message.
     */
    protected void processExchange(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            MailMessage msg = (MailMessage) exchange.getIn();
            LOG.debug("Processing message: {}", MailUtils.dumpMessage(msg.getMessage()));
        }
        getProcessor().process(exchange);
    }

    /**
     * Strategy to flag the message after being processed.
     *
     * @param mail     the mail message
     * @param exchange the exchange
     */
    protected void processCommit(Message mail, Exchange exchange) {
        try {
            // ensure folder is open
            if (!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
            }

            String uid = (String) exchange.removeProperty(MAIL_MESSAGE_UID);

            // If the protocol is POP3, the message needs to be synced with the folder via the UID.
            // Otherwise setting the DELETE/SEEN flag won't delete the message.
            if (getEndpoint().getConfiguration().getProtocol().startsWith("pop3")) {
                int count = folder.getMessageCount();
                Message found = null;
                LOG.trace("Looking for POP3Message with UID {} from folder with {} mails", uid, count);
                for (int i = 1; i <= count; ++i) {
                    Message msg = folder.getMessage(i);
                    if (uid.equals(getEndpoint().getMailUidGenerator().generateUuid(getEndpoint(), msg))) {
                        LOG.debug("Found POP3Message with UID {} from folder with {} mails", uid, count);
                        found = msg;
                        break;
                    }
                }

                if (found == null) {
                    boolean delete = getEndpoint().getConfiguration().isDelete();
                    LOG.warn("POP3message not found in folder. Message cannot be marked as {}",
                            delete ? "DELETED" : "SEEN");
                } else {
                    mail = found;
                }
            }

            org.apache.camel.Message in = exchange.getIn();
            MailConfiguration config = getEndpoint().getConfiguration();
            // header values override configuration values
            String copyTo = in.getHeader(MailConstants.MAIL_COPY_TO, config.getCopyTo(), String.class);
            String moveTo = in.getHeader(MailConstants.MAIL_MOVE_TO, config.getMoveTo(), String.class);
            boolean delete = in.getHeader(MailConstants.MAIL_DELETE, config.isDelete(), boolean.class);

            copyOrMoveMessageIfRequired(config, mail, copyTo, false);

            if (delete) {
                LOG.trace("Exchange processed, so flagging message as DELETED");
                copyOrMoveMessageIfRequired(config, mail, moveTo, true);
                mail.setFlag(Flags.Flag.DELETED, true);
            } else {
                LOG.trace("Exchange processed, so flagging message as SEEN");
                mail.setFlag(Flags.Flag.SEEN, true);
                copyOrMoveMessageIfRequired(config, mail, moveTo, true);
            }

            // need to confirm or remove on commit at last
            if (getEndpoint().getIdempotentRepository() != null) {
                if (getEndpoint().isIdempotentRepositoryRemoveOnCommit()) {
                    getEndpoint().getIdempotentRepository().remove(uid);
                } else {
                    getEndpoint().getIdempotentRepository().confirm(uid);
                }
            }

        } catch (MessagingException e) {
            getExceptionHandler().handleException("Error occurred during committing mail message: " + mail, exchange, e);
        }
    }

    private Exchange createExchange(Message message) {
        Exchange exchange = createExchange(true);
        exchange.setProperty(Exchange.BINDING, getEndpoint().getBinding());
        exchange.setIn(new MailMessage(exchange, message, getEndpoint().getConfiguration().isMapMailMessage()));
        return exchange;
    }

    private void copyOrMoveMessageIfRequired(
            MailConfiguration config, Message mail, String destinationFolder, boolean moveMessage)
            throws MessagingException {
        if (config.getProtocol().equals(MailUtils.PROTOCOL_IMAP) || config.getProtocol().equals(MailUtils.PROTOCOL_IMAPS)) {
            if (destinationFolder != null) {
                LOG.trace("IMAP message needs to be {} to {}", moveMessage ? "moved" : "copied", destinationFolder);
                Folder destFolder = store.getFolder(destinationFolder);
                if (!destFolder.exists()) {
                    destFolder.create(Folder.HOLDS_MESSAGES);
                }
                folder.copyMessages(new Message[] { mail }, destFolder);
                if (moveMessage) {
                    mail.setFlag(Flags.Flag.DELETED, true);
                }
                LOG.trace("IMAP message {} {} to {}", mail, moveMessage ? "moved" : "copied", destinationFolder);
            }
        }
    }

    /**
     * Strategy when processing the exchange failed.
     *
     * @param mail     the mail message
     * @param exchange the exchange
     */
    protected void processRollback(Message mail, Exchange exchange) {

        String uid = (String) exchange.removeProperty(MAIL_MESSAGE_UID);

        // need to remove on rollback
        if (getEndpoint().getIdempotentRepository() != null) {
            getEndpoint().getIdempotentRepository().remove(uid);
        }

        Exception cause = exchange.getException();
        if (cause != null) {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange, cause);
        } else {
            LOG.warn("Exchange failed, so rolling back message status: {}", exchange);
        }
    }

    private void ensureIsConnected() throws MessagingException {
        MailConfiguration config = getEndpoint().getConfiguration();

        boolean connected = false;
        try {
            if (store != null && store.isConnected()) {
                connected = true;
            }
        } catch (Exception e) {
            LOG.debug("Exception while testing for is connected to MailStore: {}. Caused by: {}",
                    getEndpoint().getConfiguration().getMailStoreLogInformation(), e.getMessage(), e);
        }

        if (!connected) {
            // ensure resources get recreated on reconnection
            store = null;
            folder = null;

            if (LOG.isDebugEnabled()) {
                LOG.debug("Connecting to MailStore: {}", getEndpoint().getConfiguration().getMailStoreLogInformation());
            }
            store = sender.getSession().getStore(config.getProtocol());
            PasswordAuthentication passwordAuth = config.getPasswordAuthentication();
            store.connect(config.getHost(), config.getPort(), passwordAuth.getUserName(), passwordAuth.getPassword());

            serverCanSort = hasSortCapability(store);
        }

        if (folder == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting folder {}", config.getFolderName());
            }
            folder = store.getFolder(config.getFolderName());
            if (folder == null || !folder.exists()) {
                throw new FolderNotFoundException(folder, "Folder not found or invalid: " + config.getFolderName());
            }
        }
    }

    /**
     * Check whether the email store has the sort capability or not.
     *
     * @param  store              Email store
     * @return                    true if the store is an IMAP store and it has the store capability
     * @throws MessagingException In case capability check fails
     */
    private static boolean hasSortCapability(Store store) throws MessagingException {
        if (store instanceof IMAPStore) {
            IMAPStore imapStore = (IMAPStore) store;
            if (imapStore.hasCapability("SORT*")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public MailEndpoint getEndpoint() {
        return (MailEndpoint) super.getEndpoint();
    }

    public boolean isSkipFailedMessage() {
        return skipFailedMessage;
    }

    public void setSkipFailedMessage(boolean skipFailedMessage) {
        this.skipFailedMessage = skipFailedMessage;
    }

    public boolean isHandleFailedMessage() {
        return handleFailedMessage;
    }

    public void setHandleFailedMessage(boolean handleFailedMessage) {
        this.handleFailedMessage = handleFailedMessage;
    }
}
