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
package org.apache.camel.component.citrus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.citrusframework.exceptions.ValidationException;
import org.citrusframework.message.MessagePayloadBuilder;
import org.citrusframework.validation.context.ValidationContext;

@UriEndpoint(firstVersion = "4.24.0", scheme = "citrus", title = "Citrus", syntax = "citrus:operation/name",
             producerOnly = true, remote = false, category = { Category.TESTING })
public class CitrusEndpoint extends DefaultEndpoint {

    private final AtomicInteger exchangeIndex = new AtomicInteger();
    private final AtomicInteger receivedCount = new AtomicInteger();
    private final List<Exchange> receivedExchanges = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    private volatile CountDownLatch latch;

    @UriPath(description = "The operation to perform. 'validate' validates exchanges without modifying them."
                           + " 'receive' validates exchanges and returns a simulated response.",
             enums = "validate,receive")
    @Metadata(required = true)
    private String operation;

    @UriPath(description = "A unique name to identify this endpoint. Allows multiple Citrus endpoints"
                           + " with different validation expectations in the same route.")
    private String name;

    @UriParam(description = "The message type for validation. Determines which Citrus validation module is used."
                            + " Supported values: json, xml, yaml, plaintext.")
    private String messageType;

    private volatile MessageValidationSpec allMessagesSpec;
    private final Map<Integer, MessageValidationSpec> indexedSpecs = new LinkedHashMap<>();
    private volatile int nextSpecIndex;

    private volatile MessageResponseSpec allMessagesResponseSpec;
    private final Map<Integer, MessageResponseSpec> indexedResponseSpecs = new LinkedHashMap<>();
    private volatile int nextResponseSpecIndex;

    private volatile int expectedCount = -1;

    public CitrusEndpoint(String endpointUri, CitrusComponent component) {
        super(endpointUri, component);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CitrusProducer(this);
    }

    // --- Fluent validation specification API (for 'validate' operation) ---

    public MessageValidationSpec allMessages() {
        if (allMessagesSpec == null) {
            allMessagesSpec = new MessageValidationSpec();
        }
        return allMessagesSpec;
    }

    public MessageValidationSpec message() {
        return message(nextSpecIndex++);
    }

    public MessageValidationSpec message(int index) {
        return indexedSpecs.computeIfAbsent(index, k -> new MessageValidationSpec());
    }

    // --- Gherkin-style API (for 'receive' operation) ---

    public WhenSpec when() {
        return new WhenSpec();
    }

    public ThenSpec then() {
        return new ThenSpec();
    }

    public CitrusEndpoint expectedMessageCount(int count) {
        this.expectedCount = count;
        this.receivedCount.set(0);
        this.receivedExchanges.clear();
        this.latch = new CountDownLatch(count);
        return this;
    }

    // --- Spec resolution ---

    int nextExchangeIndex() {
        return exchangeIndex.getAndIncrement();
    }

    MessageValidationSpec resolveValidationSpec(int index) {
        MessageValidationSpec indexed = indexedSpecs.get(index);
        if (indexed != null) {
            return indexed;
        }
        if (allMessagesSpec != null) {
            return allMessagesSpec;
        }
        if (!indexedSpecs.isEmpty()) {
            throw new IllegalStateException(
                    "No validation specification for exchange at index " + index
                                            + " on endpoint: " + getEndpointUri()
                                            + ". Validation specs are configured for indexes " + indexedSpecs.keySet()
                                            + ". Use allMessages() to provide a default"
                                            + " or message(" + index + ") for this specific exchange.");
        }
        return null;
    }

    MessageResponseSpec resolveResponseSpec(int index) {
        MessageResponseSpec indexed = indexedResponseSpecs.get(index);
        if (indexed != null) {
            return indexed;
        }
        return allMessagesResponseSpec;
    }

    // --- Assertion API ---

    public void assertIsSatisfied() throws InterruptedException {
        assertIsSatisfied(10, TimeUnit.SECONDS);
    }

    public void assertIsSatisfied(long timeout, TimeUnit unit) throws InterruptedException {
        int effectiveExpected = expectedCount;
        if (effectiveExpected < 0 && !indexedSpecs.isEmpty()) {
            effectiveExpected = indexedSpecs.size();
        }

        if (latch != null && !latch.await(timeout, unit)) {
            throw new ValidationException(
                    "Timed out waiting for %d messages on endpoint: %s"
                            .formatted(effectiveExpected, getEndpointUri()));
        }

        if (effectiveExpected >= 0 && receivedCount.get() != effectiveExpected) {
            throw new ValidationException(
                    "Expected %d messages but received %d on endpoint: %s"
                            .formatted(effectiveExpected, receivedCount.get(), getEndpointUri()));
        }

        if (!failures.isEmpty()) {
            ValidationException validationException = new ValidationException(
                    "Citrus validation failed on endpoint: %s. Has %d failure(s)."
                            .formatted(getEndpointUri(), failures.size()));
            failures.forEach(validationException::addSuppressed);
            throw validationException;
        }
    }

    void onExchangeReceived(Exchange exchange) {
        receivedCount.incrementAndGet();
        receivedExchanges.add(exchange);
        if (latch != null) {
            latch.countDown();
        }
    }

    void addFailure(Throwable failure) {
        failures.add(failure);
    }

    public void reset() {
        exchangeIndex.set(0);
        receivedCount.set(0);
        receivedExchanges.clear();
        failures.clear();
        allMessagesSpec = null;
        indexedSpecs.clear();
        nextSpecIndex = 0;
        allMessagesResponseSpec = null;
        indexedResponseSpecs.clear();
        nextResponseSpecIndex = 0;
        expectedCount = -1;
        latch = null;
    }

    // --- Getters and setters ---

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public int getReceivedCount() {
        return receivedCount.get();
    }

    public List<Exchange> getReceivedExchanges() {
        return new ArrayList<>(receivedExchanges);
    }

    // --- Gherkin-style inner classes ---

    public class WhenSpec {

        public WhenMessageSelector message() {
            return message(nextSpecIndex++);
        }

        public WhenMessageSelector message(int index) {
            return new WhenMessageSelector(index, false);
        }

        public WhenMessageSelector allMessages() {
            return new WhenMessageSelector(-1, true);
        }
    }

    public class WhenMessageSelector {

        private final int index;
        private final boolean all;

        WhenMessageSelector(int index, boolean all) {
            this.index = index;
            this.all = all;
        }

        public MessageValidationSpec receive() {
            if (all) {
                return CitrusEndpoint.this.allMessages();
            }
            return CitrusEndpoint.this.message(index);
        }
    }

    public class ThenSpec {

        public ThenMessageSelector message() {
            return message(nextResponseSpecIndex++);
        }

        public ThenMessageSelector message(int index) {
            return new ThenMessageSelector(index, false);
        }

        public ThenMessageSelector allMessages() {
            return new ThenMessageSelector(-1, true);
        }
    }

    public class ThenMessageSelector {

        private final int index;
        private final boolean all;

        ThenMessageSelector(int index, boolean all) {
            this.index = index;
            this.all = all;
        }

        public MessageResponseSpec respond() {
            if (all) {
                if (allMessagesResponseSpec == null) {
                    allMessagesResponseSpec = new MessageResponseSpec();
                }
                return allMessagesResponseSpec;
            }
            return indexedResponseSpecs.computeIfAbsent(index, k -> new MessageResponseSpec());
        }

        public MessageResponseSpec send() {
            return respond();
        }
    }

    // --- Validation spec for expected messages ---

    public static class MessageValidationSpec {

        private String body;
        private MessagePayloadBuilder bodyBuilder;
        private final Map<String, Object> headers = new LinkedHashMap<>();
        private final List<ValidationContext.Builder<?, ?>> validationContextBuilders = new ArrayList<>();

        public MessageValidationSpec body(String body) {
            this.body = body;
            this.bodyBuilder = null;
            return this;
        }

        public MessageValidationSpec body(MessagePayloadBuilder bodyBuilder) {
            this.bodyBuilder = bodyBuilder;
            this.body = null;
            return this;
        }

        public MessageValidationSpec header(String headerName, Object value) {
            this.headers.put(headerName, value);
            return this;
        }

        public MessageValidationSpec validate(ValidationContext.Builder<?, ?>... validationContexts) {
            this.validationContextBuilders.addAll(List.of(validationContexts));
            return this;
        }

        public String getBody() {
            return body;
        }

        public MessagePayloadBuilder getBodyBuilder() {
            return bodyBuilder;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }

        public List<ValidationContext.Builder<?, ?>> getValidationContextBuilders() {
            return validationContextBuilders;
        }
    }

    // --- Response spec for simulated responses ---

    public static class MessageResponseSpec {

        private String body;
        private MessagePayloadBuilder bodyBuilder;
        private final Map<String, Object> headers = new LinkedHashMap<>();

        public MessageResponseSpec body(String body) {
            this.body = body;
            this.bodyBuilder = null;
            return this;
        }

        public MessageResponseSpec body(MessagePayloadBuilder bodyBuilder) {
            this.bodyBuilder = bodyBuilder;
            this.body = null;
            return this;
        }

        public MessageResponseSpec header(String headerName, Object value) {
            this.headers.put(headerName, value);
            return this;
        }

        public String getBody() {
            return body;
        }

        public MessagePayloadBuilder getBodyBuilder() {
            return bodyBuilder;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }
    }
}
