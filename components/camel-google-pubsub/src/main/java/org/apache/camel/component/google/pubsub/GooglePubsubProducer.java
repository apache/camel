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
package org.apache.camel.component.google.pubsub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.client.util.Strings;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PublishResponse;
import com.google.api.services.pubsub.model.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic PubSub Producer
 */
public class GooglePubsubProducer extends DefaultProducer {

    private Logger logger;

    public GooglePubsubProducer(GooglePubsubEndpoint endpoint) throws Exception {
        super(endpoint);

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        logger = LoggerFactory.getLogger(loggerId);
    }

    /**
     * The incoming message is expected to be either
     * - a List of Exchanges (aggregated)
     * - an Exchange
     */
    @Override
    public void process(Exchange exchange) throws Exception {

        List<Exchange> entryList = prepareExchangeList(exchange);

        if (entryList == null || entryList.size() == 0) {
            logger.warn("The incoming message is either null or empty. Triggered by an aggregation timeout?");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("uploader thread/id: "
                                 + Thread.currentThread().getId()
                                 + " / " + exchange.getExchangeId()
                                 + " . api call completed.");
        }

        sendMessages(entryList);
    }

    /**
     * The method converts a single incoming message into a List
     *
     * @param exchange
     * @return
     */
    private static List<Exchange> prepareExchangeList(Exchange exchange) {

        List<Exchange> entryList = null;

        if (null == exchange.getProperty(Exchange.GROUPED_EXCHANGE)) {
            entryList = new ArrayList<>();
            entryList.add(exchange);
        } else {
            entryList = (List<Exchange>) exchange.getProperty(Exchange.GROUPED_EXCHANGE);
        }

        return entryList;
    }

    private void sendMessages(List<Exchange> exchanges) throws Exception {

        GooglePubsubEndpoint endpoint = (GooglePubsubEndpoint) getEndpoint();
        String topicName = String.format("projects/%s/topics/%s", endpoint.getProjectId(), endpoint.getDestinationName());

        List<PubsubMessage> messages = new ArrayList<>();

        for (Exchange exchange : exchanges) {
            PubsubMessage message = new PubsubMessage();

            Object body = exchange.getIn().getBody();

            if (body instanceof String) {
                message.encodeData(((String) body).getBytes("UTF-8"));
            } else if (body instanceof byte[]) {
                message.encodeData((byte[]) body);
            } else {
                message.encodeData(serialize(body));
            }

            Object attributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES);

            if (attributes != null && attributes instanceof Map && ((Map)attributes).size() > 0) {
                message.setAttributes((Map)attributes);
            }

            messages.add(message);
        }

        PublishRequest publishRequest = new PublishRequest().setMessages(messages);

        PublishResponse response = endpoint.getPubsub()
                                           .projects()
                                           .topics()
                                           .publish(topicName, publishRequest)
                                           .execute();

        List<String> sentMessageIds  = response.getMessageIds();

        int i = 0;
        for (Exchange entry : exchanges) {
            entry.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, sentMessageIds.get(i));
            i++;
        }
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }
}
