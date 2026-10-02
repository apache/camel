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
package org.apache.camel.component.sjms.consumer;

import java.util.List;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchEndpointMessageListener {

    public static final String SJMS_BATCH_SIZE_HEADER = "CamelSjmsBatchSize";

    private static final Logger LOG = LoggerFactory.getLogger(BatchEndpointMessageListener.class);

    private final SjmsEndpoint endpoint;
    private final Processor processor;
    private final AggregationStrategy aggregationStrategy;

    public BatchEndpointMessageListener(SjmsEndpoint endpoint, Processor processor, AggregationStrategy aggregationStrategy) {
        this.endpoint = endpoint;
        this.processor = processor;

        this.aggregationStrategy = aggregationStrategy;
    }

    private Exchange aggregate(List<Message> rawMessages, Session session) {
        Exchange result = null;
        for (Message m : rawMessages) {
            Exchange e = endpoint.createExchange(m, session);
            // Populate the headers and body of the Exchange in message
            e.getIn().getHeaders();
            e.getIn().getBody();

            result = aggregationStrategy.aggregate(result, e);
        }
            // Force eager materialization of JMS message headers and body into the
            // Camel Exchange, before the session is committed/closed after dispatch.
            e.getIn().getHeaders();
            e.getIn().getBody();
        return result;
    }

    void onBatch(List<Message> rawMessages, Session session) throws Exception {
        Exchange batchExchange = null;
        Exception failure = null;
        try {
            batchExchange = aggregate(rawMessages, session);
            if (batchExchange != null) {
                batchExchange.setProperty(SjmsConstants.JMS_SESSION, session);
                batchExchange.getMessage().setHeader(SJMS_BATCH_SIZE_HEADER, rawMessages.size());
                processor.process(batchExchange);
            }
        } catch (Exception e) {
            failure = e;
        }

        boolean failed = failure != null
                || (batchExchange != null && (batchExchange.isFailed() || batchExchange.isRollbackOnly()));
        Message lastMessage = rawMessages.get(rawMessages.size() - 1);

        if (!failed) {
            SjmsHelper.commitIfNeeded(session, lastMessage);
        } else {
        if (rawMessages.isEmpty()) {
            return;
        }
        Message lastMessage = rawMessages.get(rawMessages.size() - 1);
            if (cause != null) {
                LOG.warn("Batch of {} message(s) failed processing on {}: {}", rawMessages.size(),
                        endpoint.getEndpointUri(), cause.getMessage(), cause);
            }
            SjmsHelper.rollbackIfNeeded(session);
        }
    }
}
