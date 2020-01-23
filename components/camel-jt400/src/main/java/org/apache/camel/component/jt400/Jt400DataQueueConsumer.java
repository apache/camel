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

import com.ibm.as400.access.BaseDataQueue;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.DataQueueEntry;
import com.ibm.as400.access.KeyedDataQueue;
import com.ibm.as400.access.KeyedDataQueueEntry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scheduled {@link org.apache.camel.Consumer} that polls a data queue for data
 */
public class Jt400DataQueueConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(Jt400DataQueueConsumer.class);

    /**
     * Performs the lifecycle logic of this consumer.
     */
    private final Jt400DataQueueService queueService;

    /**
     * Creates a new consumer instance
     */
    public Jt400DataQueueConsumer(Jt400Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.queueService = new Jt400DataQueueService(endpoint);
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

    @Deprecated
    public Exchange receive() {
        // -1 to indicate a blocking read from data queue
        return receive(-1);
    }

    @Deprecated
    public Exchange receiveNoWait() {
        return receive(0);
    }

    /**
     * Receives an entry from a data queue and returns an {@link Exchange} to
     * send this data If the endpoint's format is set to {@link org.apache.camel.component.jt400.Jt400Configuration.Format#binary},
     * the data queue entry's data will be received/sent as a
     * <code>byte[]</code>. If the endpoint's format is set to
     * {@link org.apache.camel.component.jt400.Jt400Configuration.Format#text}, the data queue entry's data will be received/sent as
     * a <code>String</code>.
     * <p/>
     * The following message headers may be set by the receiver
     * <ul>
     * <li>SENDER_INFORMATION: The Sender Information from the Data Queue</li>
     * <li>KEY: The message key if the endpoint is configured to connect to a <code>KeyedDataQueue</code></li>
     * </ul>
     *
     * @param timeout time to wait when reading from data queue. A value of -1
     *                indicates a blocking read.
     */
    public Exchange receive(long timeout) {
        BaseDataQueue queue = queueService.getDataQueue();
        try {
            if (getEndpoint().isKeyed()) {
                return receive((KeyedDataQueue) queue, timeout);
            } else {
                return receive((DataQueue) queue, timeout);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + queue.getName(), e);
        }
    }

    private Exchange receive(DataQueue queue, long timeout) throws Exception {
        DataQueueEntry entry;
        if (timeout >= 0) {
            int seconds = (int) timeout / 1000;
            LOG.trace("Reading from data queue: {} with {} seconds timeout", queue.getName(), seconds);
            entry = queue.read(seconds);
        } else {
            LOG.trace("Reading from data queue: {} with no timeout", queue.getName());
            entry = queue.read(-1);
        }

        Exchange exchange = getEndpoint().createExchange();
        if (entry != null) {
            exchange.getIn().setHeader(Jt400Endpoint.SENDER_INFORMATION, entry.getSenderInformation());
            if (getEndpoint().getFormat() == Jt400Configuration.Format.binary) {
                exchange.getIn().setBody(entry.getData());
            } else {
                exchange.getIn().setBody(entry.getString());
            }
            return exchange;
        }
        return null;
    }

    private Exchange receive(KeyedDataQueue queue, long timeout) throws Exception {
        String key = getEndpoint().getSearchKey();
        String searchType = getEndpoint().getSearchType().name();
        KeyedDataQueueEntry entry;
        if (timeout >= 0) {
            int seconds = (int) timeout / 1000;
            LOG.trace("Reading from data queue: {} with {} seconds timeout", queue.getName(), seconds);
            entry = queue.read(key, seconds, searchType);
        } else {
            LOG.trace("Reading from data queue: {} with no timeout", queue.getName());
            entry = queue.read(key, -1, searchType);
        }

        Exchange exchange = getEndpoint().createExchange();
        if (entry != null) {
            exchange.getIn().setHeader(Jt400Endpoint.SENDER_INFORMATION, entry.getSenderInformation());
            if (getEndpoint().getFormat() == Jt400Configuration.Format.binary) {
                exchange.getIn().setBody(entry.getData());
                exchange.getIn().setHeader(Jt400Endpoint.KEY, entry.getKey());
            } else {
                exchange.getIn().setBody(entry.getString());
                exchange.getIn().setHeader(Jt400Endpoint.KEY, entry.getKeyString());
            }
            return exchange;
        }
        return null;
    }

}
