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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.citrusframework.CitrusContext;
import org.citrusframework.context.TestContext;
import org.citrusframework.message.MessagePayloadBuilder;
import org.citrusframework.validation.context.ValidationContext;

@UriEndpoint(firstVersion = "4.24.0", scheme = "citrus", title = "Citrus", syntax = "citrus:operation/name",
             producerOnly = true, remote = false, category = { Category.TESTING })
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.STREAM },
                                  description = "The body Citrus validates, as text; a stream cache is read as text and"
                                                + " reset. Any other body is compared by its toString(): bytes as a list of"
                                                + " numbers, a file as its path, a Map as {key=value}, so convert it to a String"
                                                + " first"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   byOption = @BodyType.When(option = "operation", value = "receive",
                                                             carriers = { Carrier.TEXT, Carrier.UNCHANGED }),
                                   description = "validate leaves the body as it is. receive sets the body of the simulated"
                                                 + " response, as text with its Citrus variables resolved (or what the"
                                                 + " MessagePayloadBuilder given to it builds), and leaves the body as it is"
                                                 + " when the response has no body or no spec matches the exchange"))
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

    /**
     * Auto-incrementing variant. Do not mix with explicit {@code validate(index)} calls — the auto-index is unaware of
     * explicit registrations and may overwrite them.
     */
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
        return new CitrusValidationProcessor(this::createTestContext, messageType, validationSpec);
    }

    private CitrusValidationProcessor createProcessor(
            MessageValidationSpec validationSpec,
            MessageResponseSpec responseSpec) {
        return new CitrusValidationProcessor(this::createTestContext, messageType, validationSpec, responseSpec);
    }

    private TestContext createTestContext() {
        CitrusContext ctx = citrusContext;
        if (ctx == null) {
            throw new IllegalStateException("CitrusEndpoint is not started");
        }
        return ctx.createTestContext();
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
            if ("validate".equals(operation)) {
                // For validate-only mode, collect the error and clear the exception so it does not
                // propagate through the route — validation errors are collected, not thrown.
                getFailures().add(e);
                exchange.setException(null);
            }
            // For receive mode, leave the exception on the exchange so Camel's onException
            // handlers trigger. Do NOT add to failures — the error is handled by the route,
            // not by assertIsSatisfied().
        }
    }

    // --- Getters and setters ---

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
        super.setName(name);
    }

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

        /**
         * Auto-incrementing variant. Do not mix with explicit {@code receive(index)} calls.
         */
        public WhenMessageValidationSpec receive() {
            return receive(nextSpecIndex.incrementAndGet());
        }

        public WhenMessageValidationSpec receive(int index) {
            // registers a validation-only processor now so receive() without respond() still validates;
            // ThenSpec.respond() overwrites this registration with a validation+response processor
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

}
