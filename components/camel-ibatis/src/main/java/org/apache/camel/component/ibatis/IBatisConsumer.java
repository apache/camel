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
package org.apache.camel.component.ibatis;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumer to read data from databaase.
 *
 * @see org.apache.camel.component.ibatis.strategy.IBatisProcessingStrategy
 */
public class IBatisConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(IBatisConsumer.class);

    private static final class DataHolder {
        private Exchange exchange;
        private Object data;
        private DataHolder() {
        }
    }
    
    /**
     * Statement to run after data has been processed in the route
     */
    private String onConsume;

    /**
     * Process resultset individually or as a list
     */
    private boolean useIterator = true;

    /**
     * Whether allow empty resultset to be routed to the next hop
     */
    private boolean routeEmptyResultSet;

    private int maxMessagesPerPoll;
    

    public IBatisConsumer(IBatisEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
    }

    public IBatisEndpoint getEndpoint() {
        return (IBatisEndpoint) super.getEndpoint();
    }

    /**
     * Polls the database
     */
    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        // poll data from the database
        IBatisEndpoint endpoint = getEndpoint();
        LOG.trace("Polling: {}", endpoint);
        List<Object> data = endpoint.getProcessingStrategy().poll(this, getEndpoint());

        // create a list of exchange objects with the data
        Queue<DataHolder> answer = new LinkedList<DataHolder>();
        if (useIterator) {
            for (Object item : data) {
                Exchange exchange = createExchange(item);
                DataHolder holder = new DataHolder();
                holder.exchange = exchange;
                holder.data = item;
                answer.add(holder);
            }
        } else {
            if (!data.isEmpty() || routeEmptyResultSet) {
                Exchange exchange = createExchange(data);
                DataHolder holder = new DataHolder();
                holder.exchange = exchange;
                holder.data = data;
                answer.add(holder);
            }
        }

        // process all the exchanges in this batch
        return processBatch(CastUtils.cast(answer));
    }

    public int processBatch(Queue<Object> exchanges) throws Exception {
        final IBatisEndpoint endpoint = getEndpoint();

        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there were " + total + " messages in this poll.");
            total = maxMessagesPerPoll;
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            DataHolder holder = ObjectHelper.cast(DataHolder.class, exchanges.poll());
            Exchange exchange = holder.exchange;
            Object data = holder.data;

            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // process the current exchange
            LOG.debug("Processing exchange: {}", exchange);
            getProcessor().process(exchange);

            try {
                if (onConsume != null) {
                    endpoint.getProcessingStrategy().commit(endpoint, exchange, data, onConsume);
                }
            } catch (Exception e) {
                handleException(e);
            }
        }

        return total;
    }

    private Exchange createExchange(Object data) {
        final IBatisEndpoint endpoint = getEndpoint();
        final Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);

        Message msg = exchange.getIn();
        msg.setBody(data);
        msg.setHeader(IBatisConstants.IBATIS_STATEMENT_NAME, endpoint.getStatement());

        return exchange;
    }

    /**
     * Gets the statement(s) to run after successful processing.
     * Use comma to separate multiple statements.
     */
    public String getOnConsume() {
        return onConsume;
    }

    /**
     * Sets the statement to run after successful processing.
     * Use comma to separate multiple statements.
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    /**
     * Indicates how resultset should be delivered to the route
     */
    public boolean isUseIterator() {
        return useIterator;
    }

    /**
     * Sets how resultset should be delivered to route.
     * Indicates delivery as either a list or individual object.
     * defaults to true.
     */
    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    /**
     * Indicates whether empty resultset should be allowed to be sent to the next hop or not
     */
    public boolean isRouteEmptyResultSet() {
        return routeEmptyResultSet;
    }

    /**
     * Sets whether empty resultset should be allowed to be sent to the next hop.
     * defaults to false. So the empty resultset will be filtered out.
     */
    public void setRouteEmptyResultSet(boolean routeEmptyResultSet) {
        this.routeEmptyResultSet = routeEmptyResultSet;
    }
}
