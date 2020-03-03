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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

import com.google.api.client.util.Strings;
import com.google.api.core.ApiFuture;
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

        if (logger.isDebugEnabled()) {
            logger.debug("uploader thread/id: " + Thread.currentThread().getId() + " / " + exchange.getExchangeId() + " . api call completed.");
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

        GooglePubsubEndpoint endpoint = (GooglePubsubEndpoint) getEndpoint();
        String topicName = String.format("projects/%s/topics/%s", endpoint.getProjectId(), endpoint.getDestinationName());

        Publisher publisher = endpoint.getComponent().getPublisher(topicName);

        Object body = exchange.getIn().getBody();
        ByteString byteString;

        if (body instanceof String) {
            byteString = ByteString.copyFromUtf8((String) body);
        } else if (body instanceof byte[]) {
            byteString = ByteString.copyFrom((byte[]) body);
        } else {
            byteString = ByteString.copyFrom(serialize(body));
        }

        PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder().setData(byteString);

        Map<String, String> attributes = exchange.getIn().getHeader(GooglePubsubConstants.ATTRIBUTES, Map.class);
        if (attributes != null) {
            messageBuilder.putAllAttributes(attributes).build();
        }
        PubsubMessage message = messageBuilder.build();

        ApiFuture<String> messageIdFuture = publisher.publish(message);
        exchange.getIn().setHeader(GooglePubsubConstants.MESSAGE_ID, messageIdFuture.get());
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        os.writeObject(obj);
        return out.toByteArray();
    }
}
