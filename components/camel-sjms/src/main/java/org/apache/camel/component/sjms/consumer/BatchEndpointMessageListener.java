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

import java.util.ArrayList;
import java.util.List;

import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

public class BatchEndpointMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(BatchEndpointMessageListener.class);

    private final SjmsConsumer consumer;
    private final SjmsEndpoint endpoint;
    private final Processor processor;

    public BatchEndpointMessageListener(SjmsConsumer consumer, SjmsEndpoint endpoint, Processor processor) {
        this.consumer = consumer;
        this.endpoint = endpoint;
        this.processor = processor;
    }

    private Exchange aggregate(List<Message> rawMessages, Session session) {
        List<Exchange> exchanges = new ArrayList<>(rawMessages.size());
        for (Message m : rawMessages) {
            Exchange e = endpoint.createExchange(m, session);
            // Force eager materialization of JMS message headers and body into the
            // Camel Exchange, before the session is committed/closed after dispatch.
            e.getIn().getHeaders();
            e.getIn().getBody();

            exchanges.add(e);
        }

        Exchange batchExchange = consumer.createExchange(false);
        batchExchange.getIn().setBody(exchanges);
        batchExchange.setProperty(SjmsConstants.JMS_SESSION, session);
        batchExchange.getIn().setHeader(SjmsConstants.SJMS_BATCH_SIZE_HEADER, rawMessages.size());

        return batchExchange;
    }

    void onBatch(List<Message> rawMessages, Session session) throws Exception {
        LOG.trace("onBatch START");

        LOG.debug("{} consumer received batch message: {}", endpoint, rawMessages);
        Exchange batchExchange = null;
        RuntimeCamelException rce = null;
        try {
            batchExchange = aggregate(rawMessages, session);
            try {
                processor.process(batchExchange);
            } catch (Exception e) {
                batchExchange.setException(e);
            }

            // same evaluation as EndpointMessageListenerAsyncCallback.done(), sync branch only
            if (batchExchange.isRollbackOnly()) {
                rce = wrapRuntimeCamelException(new RollbackExchangeException(batchExchange));
            } else if (batchExchange.isFailed()) {
                rce = wrapRuntimeCamelException(batchExchange.getException());
            }
        } catch (Exception e) {
            // aggregate() failed, so there is no exchange to inspect
            rce = wrapRuntimeCamelException(e);
        } finally {
            if (batchExchange != null) {
                // only after the outcome has been extracted
                consumer.releaseExchange(batchExchange, false);
            }
        }

        if (rce != null) {
            LOG.trace("onBatch END throwing exception: {}", rce.getMessage());
            throw rce;
        }
    }
}
