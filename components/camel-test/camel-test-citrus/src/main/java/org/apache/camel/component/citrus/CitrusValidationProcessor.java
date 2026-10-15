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
import java.util.Locale;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.citrusframework.CitrusContext;
import org.citrusframework.context.TestContext;
import org.citrusframework.message.DefaultMessage;
import org.citrusframework.message.MessagePayloadBuilder;
import org.citrusframework.message.MessagePayloadUtils;
import org.citrusframework.message.MessageType;
import org.citrusframework.validation.MessageValidator;
import org.citrusframework.validation.MessageValidatorRegistry;
import org.citrusframework.validation.context.DefaultMessageValidationContext;
import org.citrusframework.validation.context.HeaderValidationContext;
import org.citrusframework.validation.context.ValidationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CitrusValidationProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(CitrusValidationProcessor.class);

    private final CitrusContext citrusContext;
    private final String messageType;
    private final MessageValidationSpec spec;
    private final MessageResponseSpec responseSpec;

    CitrusValidationProcessor(CitrusContext citrusContext, String messageType,
                              MessageValidationSpec validationSpec) {
        this(citrusContext, messageType, validationSpec, null);
    }

    CitrusValidationProcessor(CitrusContext citrusContext, String messageType,
                              MessageValidationSpec validationSpec,
                              MessageResponseSpec responseSpec) {
        this.citrusContext = citrusContext;
        this.messageType = messageType;
        this.spec = validationSpec;
        this.responseSpec = responseSpec;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        TestContext testContext = citrusContext.createTestContext();
        propagateExchangeVariables(exchange, testContext);

        try {
            if (spec != null) {
                DefaultMessage receivedMessage = buildReceivedMessage(exchange);
                DefaultMessage controlMessage = buildControlMessage(spec, testContext);

                String resolvedType = resolveMessageType(spec);
                controlMessage.setType(resolvedType);

                List<ValidationContext> validationContexts = buildValidationContexts(spec);
                performCitrusValidation(receivedMessage, controlMessage, testContext, validationContexts, resolvedType);
            }

            if (responseSpec != null) {
                applyResponse(exchange, responseSpec, testContext);
            }
        } finally {
            writeBackExtractedVariables(exchange, testContext);
        }
    }

    // --- Citrus validation helpers ---

    private DefaultMessage buildReceivedMessage(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body instanceof StreamCache sc) {
            body = exchange.getIn().getBody(String.class);
            sc.reset();
        }
        Map<String, Object> headers = new LinkedHashMap<>(exchange.getIn().getHeaders());
        return new DefaultMessage(body, headers);
    }

    private DefaultMessage buildControlMessage(MessageValidationSpec spec, TestContext testContext) {
        Object resolvedBody = resolveBody(spec.getBody(), spec.getBodyBuilder(), testContext);
        DefaultMessage controlMessage = resolvedBody != null
                ? new DefaultMessage(resolvedBody)
                : new DefaultMessage();

        for (Map.Entry<String, Object> entry : spec.getHeaders().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                value = testContext.replaceDynamicContentInString(s);
            }
            controlMessage.setHeader(entry.getKey(), value);
        }
        return controlMessage;
    }

    private String resolveMessageType(MessageValidationSpec spec) {
        if (messageType != null) {
            if (MessageType.knows(messageType)) {
                return MessageType.valueOf(messageType.toUpperCase(Locale.ROOT)).name();
            } else {
                return messageType.toUpperCase(Locale.ROOT);
            }
        }

        String expectedBody = spec.getBody();
        if (expectedBody != null) {
            if (MessagePayloadUtils.isJson(expectedBody)) {
                return MessageType.JSON.name();
            } else if (MessagePayloadUtils.isXml(expectedBody)) {
                return MessageType.XML.name();
            } else if (MessagePayloadUtils.isYaml(expectedBody)) {
                return MessageType.YAML.name();
            }
        }
        return MessageType.PLAINTEXT.name();
    }

    private List<ValidationContext> buildValidationContexts(MessageValidationSpec spec) {
        List<ValidationContext> contexts = new ArrayList<>();
        contexts.add(new DefaultMessageValidationContext());
        if (!spec.getHeaders().isEmpty()) {
            contexts.add(new HeaderValidationContext());
        }
        for (ValidationContext.Builder<?, ?> builder : spec.getValidationContextBuilders()) {
            contexts.add(builder.build());
        }
        return contexts;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void performCitrusValidation(
            DefaultMessage receivedMessage, DefaultMessage controlMessage,
            TestContext testContext, List<ValidationContext> validationContexts,
            String resolvedType) {

        MessageValidatorRegistry registry = testContext.getMessageValidatorRegistry();
        List<MessageValidator<? extends ValidationContext>> validators
                = registry.findMessageValidators(resolvedType, receivedMessage);

        if (validators.isEmpty()) {
            throw new IllegalStateException(
                    "No Citrus message validator found for message type '" + resolvedType
                                            + "'. Make sure to add the required Citrus validation module "
                                            + "to the test classpath (e.g. citrus-validation-json, "
                                            + "citrus-validation-xml, citrus-validation-text).");
        }

        LOG.debug("Validating exchange with {} Citrus validator(s) for message type '{}'",
                validators.size(), resolvedType);

        for (MessageValidator validator : validators) {
            validator.validateMessage(receivedMessage, controlMessage, testContext, validationContexts);
        }
    }

    private void applyResponse(Exchange exchange, MessageResponseSpec responseSpec, TestContext testContext) {
        Object resolvedBody = resolveBody(responseSpec.getBody(), responseSpec.getBodyBuilder(), testContext);
        if (resolvedBody != null) {
            exchange.getIn().setBody(resolvedBody);
        }
        Map<String, Object> resolvedHeaders = testContext.resolveDynamicValuesInMap(responseSpec.getHeaders());
        resolvedHeaders.forEach((key, value) -> exchange.getIn().setHeader(key, value));
    }

    private Object resolveBody(String body, MessagePayloadBuilder bodyBuilder, TestContext testContext) {
        if (bodyBuilder != null) {
            return bodyBuilder.buildPayload(testContext);
        }
        if (body != null) {
            return testContext.replaceDynamicContentInString(body);
        }
        return null;
    }

    // --- Variable propagation ---

    private void propagateExchangeVariables(Exchange exchange, TestContext testContext) {
        for (Map.Entry<String, Object> entry : exchange.getProperties().entrySet()) {
            if (entry.getValue() != null) {
                testContext.setVariable(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Object> entry : exchange.getVariables().entrySet()) {
            if (entry.getValue() != null) {
                testContext.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    private void writeBackExtractedVariables(Exchange exchange, TestContext testContext) {
        for (Map.Entry<String, Object> entry : testContext.getVariables().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("citrus.") || key.startsWith("CITRUS_")) {
                continue;
            }
            if (!exchange.getProperties().containsKey(key) && !exchange.getVariables().containsKey(key)) {
                exchange.setVariable(key, entry.getValue());
            }
        }
    }
}
