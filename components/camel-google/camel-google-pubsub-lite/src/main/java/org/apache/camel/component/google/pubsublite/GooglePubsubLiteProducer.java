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

package org.apache.camel.component.google.pubsublite;

import static org.apache.camel.component.google.pubsublite.GooglePubsubLiteConstants.ATTRIBUTES;
import static org.apache.camel.component.google.pubsublite.GooglePubsubLiteConstants.ORDERING_KEY;
import static org.apache.camel.component.google.pubsublite.GooglePubsubLiteConstants.RESERVED_GOOGLE_CLIENT_ATTRIBUTE_PREFIX;

import java.util.List;
import java.util.Map;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsublite.cloudpubsub.Publisher;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic PubSub Lite Producer
 */
public class GooglePubsubLiteProducer extends DefaultProducer {

    public Logger logger;

    public GooglePubsubLiteProducer(GooglePubsubLiteEndpoint endpoint) {
        super(endpoint);

        String loggerId = endpoint.getLoggerId();

        if (Strings.isNullOrEmpty(loggerId)) {
            loggerId = this.getClass().getName();
        }

        logger = LoggerFactory.getLogger(loggerId);
    }

    /**
     * The incoming message is expected to be either - a List of Exchanges (aggregated) - an Exchange
     */
    @Override
    public void process(Exchange exchange) throws Exception {

        if (logger.isDebugEnabled()) {
            // TODO Update once baseline is Java 21
            //            logger.debug("uploader thread/id: {} / {}. api call completed.",
            // Thread.currentThread().threadId(),
            //                    exchange.getExchangeId());
            logger.debug(
                    "uploader thread/id: {} / {}. api call completed.",
                    Thread.currentThread().getId(),
                    exchange.getExchangeId());
        }

        if (exchange.getIn().getBody() instanceof List) {
            boolean groupedExchanges = false;
            for (Object body : exchange.getIn().getBody(List.class)) {
                if (body instanceof Exchange) {
                    send((Exchange) body);
                    groupedExchanges = true;
                }
            }
            if (!groupedExchanges) {
                send(exchange);
            }
        } else {
            send(exchange);
        }
    }

    private void send(Exchange exchange) throws Exception {

        GooglePubsubLiteEndpoint endpoint = (GooglePubsubLiteEndpoint) getEndpoint();
        String topicName = String.format(
                "projects/%s/locations/%s/topics/%s",
                endpoint.getProjectId(), endpoint.getLocation(), endpoint.getDestinationName());

        Publisher publisher = endpoint.getComponent().getPublisher(topicName, endpoint);

        Object body = exchange.getMessage().getBody();
        ByteString byteString;

        if (body instanceof String) {
            byteString = ByteString.copyFromUtf8((String) body);
        } else if (body instanceof byte[]) {
            byteString = ByteString.copyFrom((byte[]) body);
        } else {
            byteString = ByteString.copyFrom(endpoint.getSerializer().serialize(body));
        }

        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder().setData(byteString);
        Map<String, String> attributes = exchange.getMessage().getHeader(ATTRIBUTES, Map.class);
        if (attributes != null) {
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                if (!attribute.getKey().startsWith(RESERVED_GOOGLE_CLIENT_ATTRIBUTE_PREFIX)) {
                    messageBuilder.putAttributes(attribute.getKey(), attribute.getValue());
                }
            }
        }
        String orderingKey = exchange.getMessage().getHeader(ORDERING_KEY, String.class);
        if (orderingKey != null) {
            messageBuilder.setOrderingKey(orderingKey);
        }

        PubsubMessage message = messageBuilder.build();

        ApiFuture<String> messageIdFuture = publisher.publish(message);
        exchange.getMessage().setHeader(GooglePubsubLiteConstants.MESSAGE_ID, messageIdFuture.get());
    }
}
