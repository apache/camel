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
package org.apache.camel.component.jt400;

import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.MessageQueue;
import com.ibm.as400.access.QueuedMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scheduled {@link org.apache.camel.Consumer} that polls a message queue for new messages
 */
public class Jt400MsgQueueConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Jt400MsgQueueConsumer.class);

    /**
     * Performs the lifecycle logic of this consumer.
     */
    private final Jt400MsgQueueService queueService;

    private byte[] messageKey;

    /**
     * Creates a new consumer instance
     */
    public Jt400MsgQueueConsumer(Jt400Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.queueService = new Jt400MsgQueueService(endpoint);
        this.messageKey = null;
    }

    @Override
    public Jt400Endpoint getEndpoint() {
        return (Jt400Endpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Exchange exchange = receive(getEndpoint().getReadTimeout());
        if (exchange != null) {
            getProcessor().process(exchange);
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    protected void doStart() throws Exception {
        queueService.start();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        queueService.stop();
    }

    /**
     * Receives an entry from a message queue and returns an {@link Exchange} to send this data, which will be
     * received/sent as a <code>String</code>.
     * <p/>
     * The following message headers may be set by the receiver
     * <ul>
     * <li>SENDER_INFORMATION: The Sender Information from the message</li>
     * <li>jt400.MESSAGE_ID: The message identifier</li>
     * <li>jt400.MESSAGE_FILE: The message file</li>
     * <li>jt400.MESSAGE_TYPE: The message type (corresponds to constants defined in the AS400Message class)</li>
     * </ul>
     *
     * @param timeout time to wait when reading from message queue. A value of -1 indicates an infinite wait time.
     */
    public Exchange receive(long timeout) {
        MessageQueue queue = queueService.getMsgQueue();
        try {
            return receive(queue, timeout);
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to read from message queue: " + queue.getPath(), e);
        }
    }

    private synchronized Exchange receive(MessageQueue queue, long timeout) throws Exception {
        QueuedMessage entry;
        int seconds = (timeout >= 0) ? (int) timeout / 1000 : -1;
        LOG.trace("Reading from message queue: {} with {} seconds timeout", queue.getPath(),
                -1 == seconds ? "infinite" : seconds);

        Jt400Configuration.MessageAction messageAction = getEndpoint().getMessageAction();
        entry = queue.receive(messageKey, //message key
                seconds,    //timeout
                messageAction.getJt400Value(),  // message action
                null == messageKey ? MessageQueue.ANY : MessageQueue.NEXT); // types of messages

        if (null == entry) {
            return null;
        }
        // Need to tuck away the message key if the message action is SAME, otherwise
        // we'll just keep retrieving the same message over and over
        if (Jt400Configuration.MessageAction.SAME == messageAction) {
            this.messageKey = entry.getKey();
        }

        Exchange exchange = createExchange(true);
        exchange.getIn().setHeader(Jt400Constants.SENDER_INFORMATION,
                entry.getFromJobNumber() + "/" + entry.getUser() + "/" + entry.getFromJobName());
        setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_ID, entry.getID());
        setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_FILE, entry.getFileName());
        setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_TYPE, entry.getType());
        setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_SEVERITY, entry.getSeverity());
        setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE, entry);
        if (AS400Message.INQUIRY == entry.getType()) {
            setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_DFT_RPY, entry.getDefaultReply());
            if (getEndpoint().isSendingReply()) {
                setHeaderIfValueNotNull(exchange.getIn(), Jt400Constants.MESSAGE_REPLYTO_KEY, entry.getKey());
            }
        }
        exchange.getIn().setBody(entry.getText());
        return exchange;
    }

    private static void setHeaderIfValueNotNull(final Message message, final String header, final Object value) {
        if (null == value) {
            return;
        }
        message.setHeader(header, value);
    }
}
