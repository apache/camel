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
package org.apache.camel.component.google.pubsub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.client.util.Strings;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic PubSub Producer
 */
public class GooglePubsubProducer extends DefaultProducer {

    public Logger logger;

    public GooglePubsubProducer(GooglePubsubEndpoint endpoint) throws Exception {
        super(endpoint);

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        logger = LoggerFactory.getLogger(loggerId);
    }

    /**
     * The incoming message is expected to be either - a List of Exchanges
     * (aggregated) - an Exchange
     */
    @Override
    public void process(Exchange exchange) throws Exception {

        List<Exchange> entryList = prepareExchangeList(exchange);

        if (entryList == null || entryList.size() == 0) {
            logger.warn("The incoming message is either null or empty. Triggered by an aggregation timeout?");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("uploader thread/id: " + Thread.currentThread().getId() + " / " + exchange.getExchangeId() + " . api call completed.");
        }

        sendMessages(entryList);
    }

    /**
     * The method converts a single incoming message into a List
     */
    private static List<Exchange> prepareExchangeList(Exchange exchange) {

        List<Exchange> entryList = null;

        if (null == exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
            entryList = new ArrayList<>();
            entryList.add(exchange);
        } else {
            entryList = (List<Exchange>)exchange.getProperty(Exchange.GROUPED_EXCHANGE);
        }

        return entryList;
    }

    private void sendMessages(List<Exchange> exchanges) throws Exception {

        GooglePubsubEndpoint endpoint = (GooglePubsubEndpoint) getEndpoint();
        String topicName = String.format("projects/%s/topics/%s", endpoint.getProjectId(), endpoint.getDestinationName());
        List<ApiFuture<String>> messageIdFutures = new ArrayList<>();
        try {
            Publisher publisher = endpoint.getPublisher(topicName);

            for (Exchange exchange : exchanges) {
                Map<String, String> attributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);

                ByteString data = ByteString.copyFromUtf8(exchange.getIn().getBody(String.class));
                PubsubMessage message = PubsubMessage.newBuilder().putAllAttributes(attributes).setData(data).build();

                ApiFuture<String> messageIdFuture = publisher.publish(message);
                messageIdFutures.add(messageIdFuture);
            }
        } finally {
            List<String> messageIds = ApiFutures.allAsList(messageIdFutures).get();

            if (!messageIds.isEmpty()) {
                int i = 0;
                for (Exchange entry : exchanges) {
                    entry.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, messageIds.get(i));
                    i++;
                }
            }
        }
    }
}
