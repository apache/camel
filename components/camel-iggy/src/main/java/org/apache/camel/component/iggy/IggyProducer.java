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
package org.apache.camel.component.iggy;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.iggy.client.blocking.IggyBaseClient;
import org.apache.iggy.identifier.StreamId;
import org.apache.iggy.identifier.TopicId;
import org.apache.iggy.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IggyProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(IggyProducer.class);

    private final IggyEndpoint endpoint;
    private IggyBaseClient client;

    public IggyProducer(IggyEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        client = endpoint.getIggyClient();

        endpoint.initializeTopic(client);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // TODO overrides from headers and reinitialize topic if topic/stream was updated
        IggyConfiguration iggyConfiguration = endpoint.getConfiguration();

        try {
            Object body = exchange.getIn().getBody();

            List<Message> messages;
            if (body instanceof List bodyAsList) {
                if (isListOfStrings(bodyAsList)) {
                    messages = (List<Message>) bodyAsList.stream()
                            .map(s -> Message.of((String) s))
                            .collect(Collectors.toList());
                } else if (isListOfMessages(bodyAsList)) {
                    messages = bodyAsList;
                } else {
                    throw new RuntimeCamelException(
                            String.format(
                                    "Unsupported List body type: %s, only List<String> or List<org.apache.iggy.message.Message> are supported",
                                    body.getClass()));
                }
            } else if (body instanceof Message bodyAsMessage) {
                messages = Collections.singletonList(bodyAsMessage);
            } else {
                messages = Collections
                        .singletonList(Message.of(exchange.getContext().getTypeConverter().convertTo(String.class, body)));
            }

            // TODO Handle user header when they are implemented in the java client
            /*
            let message = IggyMessage::builder()
                .payload(Bytes::from(json))
                .user_headers(headers)
                .build()
                .unwrap();
             */

            client.messages().sendMessages(
                    StreamId.of(iggyConfiguration.getStreamName()),
                    TopicId.of(endpoint.getTopicName()),
                    iggyConfiguration.getPartitioning(),
                    messages);
        } catch (Exception e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    private boolean isListOfStrings(List<?> list) {
        return list != null &&
                list.stream().allMatch(item -> item == null || item instanceof String);
    }

    private boolean isListOfMessages(List<?> list) {
        return list != null &&
                list.stream().allMatch(item -> item == null || item instanceof Message);
    }

}
