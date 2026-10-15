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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.citrusframework.CitrusContext;
import org.citrusframework.message.MessagePayloadBuilder;
import org.citrusframework.validation.context.ValidationContext;

@UriEndpoint(firstVersion = "4.24.0", scheme = "citrus", title = "Citrus", syntax = "citrus:operation/name",
             producerOnly = true, remote = false, category = { Category.TESTING })
public class CitrusEndpoint extends MockEndpoint {

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

    private volatile CitrusContext citrusContext;
    private final AtomicInteger nextSpecIndex = new AtomicInteger(0);

    public CitrusEndpoint(String endpointUri, CitrusComponent component) {
        super(endpointUri, component);
    }

    // --- Lifecycle ---

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        citrusContext = CitrusContext.create();
    }

    @Override
    protected void doStop() throws Exception {
        if (citrusContext != null) {
            citrusContext.close();
            citrusContext = null;
        }
        super.doStop();
    }

    // --- Fluent validation specification API ---

    public MessageValidationSpec validateAll() {
        MessageValidationSpec messageValidationSpec = new MessageValidationSpec();
        whenAnyExchangeReceived(createProcessor(messageValidationSpec));
        return messageValidationSpec;
    }

    public MessageValidationSpec validate() {
        return validate(nextSpecIndex.incrementAndGet());
    }

    public MessageValidationSpec validate(int index) {
        MessageValidationSpec messageValidationSpec = new MessageValidationSpec();
        whenExchangeReceived(index, createProcessor(messageValidationSpec));
        applyImpliedMinimumCount(index);
        return messageValidationSpec;
    }

    private CitrusValidationProcessor createProcessor(MessageValidationSpec validationSpec) {
        return new CitrusValidationProcessor(() -> citrusContext.createTestContext(), messageType, validationSpec);
    }

    private CitrusValidationProcessor createProcessor(
            MessageValidationSpec validationSpec,
            MessageResponseSpec responseSpec) {
        return new CitrusValidationProcessor(
                () -> citrusContext.createTestContext(), messageType, validationSpec, responseSpec);
    }

    // --- Gherkin-style API (for 'receive' operation) ---

    public WhenSpec when() {
        return new WhenSpec();
    }

    private void applyImpliedMinimumCount(int index) {
        if (getExpectedCount() < 0 && getExpectedMinimumCount() < index) {
            expectedMinimumMessageCount(index);
        }
    }

    @Override
    public void reset() {
        super.reset();
        // guard against super constructor calling reset() before field initializer runs
        if (nextSpecIndex != null) {
            nextSpecIndex.set(0);
        }
    }

    @Override
    protected void performAssertions(Exchange exchange, Exchange copy) throws Exception {
        super.performAssertions(exchange, copy);

        // tryProcessing() in the superclass swallows processor exceptions and sets them on the
        // exchange instead of adding them to the failures list. Capture them here so
        // assertIsSatisfied() can report Citrus validation errors properly.
        Exception e = exchange.getException();
        if (e != null && !getFailures().contains(e)) {
            getFailures().add(e);
            if ("validate".equals(operation)) {
                // For validate-only mode, clear the exception so it does not propagate
                // through the route — validation errors are collected, not thrown.
                exchange.setException(null);
            }
        }
    }

    // --- Getters and setters ---

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    // --- Gherkin-style inner classes ---

    public class WhenSpec {

        public WhenMessageValidationSpec receive() {
            return receive(nextSpecIndex.incrementAndGet());
        }

        public WhenMessageValidationSpec receive(int index) {
            WhenMessageValidationSpec messageValidationSpec = new WhenMessageValidationSpec(index);
            whenExchangeReceived(index, createProcessor(messageValidationSpec));
            applyImpliedMinimumCount(index);
            return messageValidationSpec;
        }

        public WhenMessageValidationSpec receiveAll() {
            WhenMessageValidationSpec messageValidationSpec = new WhenMessageValidationSpec(-1);
            whenAnyExchangeReceived(createProcessor(messageValidationSpec));
            return messageValidationSpec;
        }
    }

    public class WhenMessageValidationSpec extends MessageValidationSpec {

        private final int index;

        WhenMessageValidationSpec(int index) {
            this.index = index;
        }

        @Override
        public WhenMessageValidationSpec body(MessagePayloadBuilder bodyBuilder) {
            super.body(bodyBuilder);
            return this;
        }

        @Override
        public WhenMessageValidationSpec body(String body) {
            super.body(body);
            return this;
        }

        @Override
        public WhenMessageValidationSpec header(String headerName, Object value) {
            super.header(headerName, value);
            return this;
        }

        @Override
        public WhenMessageValidationSpec validate(ValidationContext.Builder<?, ?>... validationContexts) {
            super.validate(validationContexts);
            return this;
        }

        public ThenSpec then() {
            return new ThenSpec(index, this);
        }
    }

    public class ThenSpec {

        private final int index;
        private final WhenMessageValidationSpec messageValidationSpec;

        public ThenSpec(int index, WhenMessageValidationSpec messageValidationSpec) {
            this.index = index;
            this.messageValidationSpec = messageValidationSpec;
        }

        public MessageResponseSpec respond() {
            MessageResponseSpec messageResponseSpec = new MessageResponseSpec();
            if (index < 0) {
                whenAnyExchangeReceived(createProcessor(messageValidationSpec, messageResponseSpec));
            } else {
                whenExchangeReceived(index, createProcessor(messageValidationSpec, messageResponseSpec));
            }
            return messageResponseSpec;
        }

        public MessageResponseSpec send() {
            return respond();
        }
    }

    // --- Validation spec for expected messages ---

    // --- Response spec for simulated responses ---

}
