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
package org.apache.camel.component.splunk;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.splunk.event.SplunkEvent;
import org.apache.camel.component.splunk.support.SplunkDataReader;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Splunk consumer.
 */
public class SplunkConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SplunkConsumer.class);

    private SplunkDataReader dataReader;
    private SplunkEndpoint endpoint;

    public SplunkConsumer(SplunkEndpoint endpoint, Processor processor, ConsumerType consumerType) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        if (consumerType.equals(ConsumerType.NORMAL) || consumerType.equals(ConsumerType.REALTIME)) {
            if (ObjectHelper.isEmpty(endpoint.getConfiguration().getSearch())) {
                throw new RuntimeException("Missing option 'search' with normal or realtime search");
            }
        }
        if (consumerType.equals(ConsumerType.SAVEDSEARCH)
                && ObjectHelper.isEmpty(endpoint.getConfiguration().getSavedSearch())) {
            throw new RuntimeException("Missing option 'savedSearch' with saved search");
        }
        dataReader = new SplunkDataReader(endpoint, consumerType);
    }

    @Override
    protected int poll() throws Exception {
        try {
            if (endpoint.getConfiguration().isStreaming()) {
                dataReader.read(splunkEvent -> {
                    final Exchange exchange = createExchange(true);
                    Message message = exchange.getIn();
                    message.setBody(splunkEvent);

                    // use default consumer callback
                    AsyncCallback cb = defaultConsumerCallback(exchange, true);
                    getAsyncProcessor().process(exchange, cb);
                });
                // Return 0: no exchanges returned by poll, as exchanges have been returned asynchronously
                return 0;
            } else {
                List<SplunkEvent> events = dataReader.read();

                // okay we have some response from splunk so lets mark the consumer as ready
                forceConsumerAsReady();

                Queue<Exchange> exchanges = createExchanges(events);
                return processBatch(CastUtils.cast(exchanges));
            }
        } catch (Exception e) {
            endpoint.reset(e);
            getExceptionHandler().handleException(e);
            return 0;
        }
    }

    protected Queue<Exchange> createExchanges(List<SplunkEvent> splunkEvents) {
        LOG.trace("Received {} messages in this poll", splunkEvents.size());
        Queue<Exchange> answer = new LinkedList<>();
        for (SplunkEvent splunkEvent : splunkEvents) {
            Exchange exchange = createExchange(true);
            Message message = exchange.getIn();
            message.setBody(splunkEvent);
            answer.add(exchange);
        }
        return answer;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            Exchange exchange = ObjectHelper.cast(Exchange.class, exchanges.poll());
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);
            try {
                LOG.trace("Processing exchange [{}]...", exchange);
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
        return total;
    }

}
