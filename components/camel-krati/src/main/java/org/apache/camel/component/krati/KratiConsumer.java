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
package org.apache.camel.component.krati;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import krati.store.DataStore;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Krati consumer.
 */
public class KratiConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KratiConsumer.class);

    protected final KratiEndpoint endpoint;
    protected DataStore<Object, Object> dataStore;

    public KratiConsumer(KratiEndpoint endpoint, Processor processor, DataStore<Object, Object> dataStore) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.dataStore = dataStore;
    }

    @Override
    protected int poll() throws Exception {
        shutdownRunningTask = null;
        pendingExchanges = 0;
        int max = getMaxMessagesPerPoll() > 0 ? getMaxMessagesPerPoll() : Integer.MAX_VALUE;

        Queue<Exchange> queue = new LinkedList<Exchange>();

        Iterator<Object> keyIterator = dataStore.keyIterator();
        int index = 0;
        while (keyIterator.hasNext() && index < max) {
            Object key = keyIterator.next();
            Object value = dataStore.get(key);
            Exchange exchange = endpoint.createExchange();
            exchange.setProperty(KratiConstants.KEY, key);
            exchange.getIn().setBody(value);
            queue.add(exchange);
            index++;
        }

        // did we cap at max?
        if (index == max && keyIterator.hasNext()) {
            log.debug("Limiting to maximum messages to poll {} as there were more messages in this poll.", max);
        }

        return queue.isEmpty() ? 0 : processBatch(CastUtils.cast(queue));
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // add on completion to handle after work when the exchange is done
            exchange.addOnCompletion(new Synchronization() {
                public void onComplete(Exchange exchange) {
                    try {
                        dataStore.delete(exchange.getProperty(KratiConstants.KEY));
                    } catch (Exception e) {
                        LOG.warn("Failed to remove from datastore. This exception is ignored.", e);
                    }
                }

                public void onFailure(Exchange exchange) {
                  // noop
                }
            });

            LOG.trace("Processing exchange [{}]...", exchange);
            getProcessor().process(exchange);
        }

        return total;
    }
}
