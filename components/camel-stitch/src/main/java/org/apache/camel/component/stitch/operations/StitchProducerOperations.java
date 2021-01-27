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
package org.apache.camel.component.stitch.operations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.StitchConfiguration;
import org.apache.camel.component.stitch.StitchConstants;
import org.apache.camel.component.stitch.client.StitchClient;
import org.apache.camel.component.stitch.client.models.StitchMessage;
import org.apache.camel.component.stitch.client.models.StitchRequestBody;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.client.models.StitchSchema;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class StitchProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(StitchProducerOperations.class);

    private final StitchClient client;
    private final StitchConfiguration configuration;

    public StitchProducerOperations(StitchClient client, StitchConfiguration configuration) {
        ObjectHelper.notNull(client, "client");
        ObjectHelper.notNull(configuration, "configuration");

        this.client = client;
        this.configuration = configuration;
    }

    public boolean sendEvents(
            final Message inMessage, final Consumer<StitchResponse> resultCallback, final AsyncCallback callback) {
        sendAsyncEvents(inMessage)
                .subscribe(resultCallback, error -> {
                    // error but we continue
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error processing async exchange with error: {}", error.getMessage());
                    }
                    inMessage.getExchange().setException(error);
                    callback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.trace("All events with exchange have been sent successfully.");
                    callback.done(false);
                });

        return false;
    }

    private Mono<StitchResponse> sendAsyncEvents(final Message inMessage) {
        return client.batch(createStitchRequestBody(inMessage));
    }

    @SuppressWarnings("unchecked")
    // visible for testing
    public StitchRequestBody createStitchRequestBody(final Message inMessage) {
        if (inMessage.getBody() instanceof StitchRequestBody) {
            return createStitchRequestBodyFromStitchRequestBody(inMessage.getBody(StitchRequestBody.class), inMessage);
        }

        if (inMessage.getBody() instanceof StitchMessage) {
            return createStitchRequestBodyFromStitchMessages(Collections.singletonList(inMessage.getBody(StitchMessage.class)),
                    inMessage);
        }

        if (inMessage.getBody() instanceof Iterable) {
            return createStitchRequestBodyFromIterable(inMessage.getBody(Iterable.class), inMessage);
        }

        if (inMessage.getBody() instanceof Map) {
            return createStitchRecordFromMap(inMessage.getBody(Map.class), inMessage);
        }

        throw new IllegalArgumentException("Message body data `" + inMessage.getBody() + "` type is not supported");
    }

    private StitchRequestBody createStitchRequestBodyFromStitchRequestBody(
            final StitchRequestBody requestBody, final Message message) {
        return createStitchRecordFromBuilder(StitchRequestBody.fromStitchRequestBody(requestBody), message);
    }

    private StitchRequestBody createStitchRequestBodyFromStitchMessages(
            final Collection<StitchMessage> stitchMessages, final Message message) {
        final StitchRequestBody.Builder builder = StitchRequestBody.builder()
                .addMessages(stitchMessages);

        return createStitchRecordFromBuilder(builder, message);
    }

    @SuppressWarnings("unchecked")
    private StitchRequestBody createStitchRequestBodyFromIterable(final Iterable<Object> inputData, final Message message) {
        final Collection<StitchMessage> stitchMessages = new LinkedList<>();

        inputData.forEach(data -> {
            if (data instanceof StitchMessage) {
                stitchMessages.add((StitchMessage) data);
            } else if (data instanceof Map) {
                stitchMessages.add(StitchMessage.fromMap(ObjectHelper.cast(Map.class, data)).build());
            } else if (data instanceof StitchRequestBody) {
                stitchMessages.addAll(((StitchRequestBody) data).getMessages());
            } else if (data instanceof Message) {
                final Message camelNestedMessage = (Message) data;
                // set all the headers from parent message
                camelNestedMessage.setHeaders(message.getHeaders());
                stitchMessages.addAll(createStitchRequestBody(camelNestedMessage).getMessages());
            } else if (data instanceof Exchange) {
                final Message camelNestedMessage = ((Exchange) data).getMessage();
                // set all the headers from parent message
                camelNestedMessage.setHeaders(message.getHeaders());
                stitchMessages.addAll(createStitchRequestBody(camelNestedMessage).getMessages());
            } else {
                throw new IllegalArgumentException("Input data `" + data + "` type is not supported");
            }
        });

        return createStitchRequestBodyFromStitchMessages(stitchMessages, message);
    }

    private StitchRequestBody createStitchRecordFromMap(final Map<String, Object> data, final Message message) {
        return createStitchRecordFromBuilder(StitchRequestBody.fromMap(data), message);
    }

    private StitchRequestBody createStitchRecordFromBuilder(final StitchRequestBody.Builder builder, final Message message) {
        return builder
                .withSchema(getStitchSchema(message))
                .withTableName(getTableName(message))
                .withKeyNames(getKeyNames(message))
                .build();
    }

    private String getTableName(final Message message) {
        return getOption(message, StitchConstants.TABLE_NAME, configuration::getTableName, String.class);
    }

    @SuppressWarnings("unchecked")
    private StitchSchema getStitchSchema(final Message message) {
        // if we have header set, then we try first that
        if (ObjectHelper.isNotEmpty(message.getHeader(StitchConstants.SCHEMA))) {
            if (message.getHeader(StitchConstants.SCHEMA) instanceof StitchSchema) {
                return message.getHeader(StitchConstants.SCHEMA, StitchSchema.class);
            }
            if (message.getHeader(StitchConstants.SCHEMA) instanceof Map) {
                return StitchSchema.builder().addKeywords(message.getHeader(StitchConstants.SCHEMA, Map.class)).build();
            }
        }
        // otherwise we just get whatever we have in the config
        return configuration.getStitchSchema();
    }

    private Collection<String> getKeyNames(final Message message) {
        final String keys = getOption(message, StitchConstants.KEY_NAMES, configuration::getKeyNames, String.class);

        if (ObjectHelper.isNotEmpty(keys)) {
            return Arrays.asList(keys.split(",").clone());
        }

        return Collections.emptyList();
    }

    private <R> R getOption(
            final Message message, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(message) || ObjectHelper.isEmpty(getObjectFromHeaders(message, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(message, headerName, type);
    }

    private <T> T getObjectFromHeaders(final Message message, final String headerName, final Class<T> classType) {
        return message.getHeader(headerName, classType);
    }
}
