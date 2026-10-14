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
import org.apache.camel.component.citrus.CitrusEndpoint.MessageResponseSpec;
import org.apache.camel.component.citrus.CitrusEndpoint.MessageValidationSpec;
import org.apache.camel.support.DefaultProducer;
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

public class CitrusProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CitrusProducer.class);

    private volatile CitrusContext citrusContext;

    public CitrusProducer(CitrusEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public CitrusEndpoint getEndpoint() {
        return (CitrusEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        citrusContext = CitrusContext.create();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (citrusContext != null) {
            citrusContext.close();
            citrusContext = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        CitrusEndpoint endpoint = getEndpoint();

        int index = endpoint.nextExchangeIndex();
        TestContext testContext = citrusContext.createTestContext();
        propagateExchangeVariables(exchange, testContext);

        try {
            MessageValidationSpec spec = endpoint.resolveValidationSpec(index);
            if (spec != null) {
                DefaultMessage receivedMessage = buildReceivedMessage(exchange);
                DefaultMessage controlMessage = buildControlMessage(spec, testContext);

                String messageType = resolveMessageType(endpoint, spec);
                controlMessage.setType(messageType);

                List<ValidationContext> validationContexts = buildValidationContexts(spec);

                performValidation(receivedMessage, controlMessage, testContext, validationContexts, messageType);
            }

            if ("receive".equals(endpoint.getOperation())) {
                MessageResponseSpec responseSpec = endpoint.resolveResponseSpec(index);
                if (responseSpec != null) {
                    applyResponse(exchange, responseSpec, testContext);
                }
            }
        } catch (Exception e) {
            endpoint.addFailure(e);
            if ("receive".equals(endpoint.getOperation())) {
                throw e;
            }
        } finally {
            writeBackExtractedVariables(exchange, testContext);
            endpoint.onExchangeReceived(exchange);
        }
    }

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
            if (!exchange.getProperties().containsKey(key) && !exchange.getVariables().containsKey(key)) {
                exchange.setVariable(key, entry.getValue());
            }
        }
    }

    private DefaultMessage buildReceivedMessage(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        Map<String, Object> headers = new LinkedHashMap<>(exchange.getIn().getHeaders());
        return new DefaultMessage(body, headers);
    }

    private DefaultMessage buildControlMessage(MessageValidationSpec spec, TestContext testContext) {
        DefaultMessage controlMessage;
        Object resolvedBody = resolveBody(spec.getBody(), spec.getBodyBuilder(), testContext);

        if (resolvedBody != null) {
            controlMessage = new DefaultMessage(resolvedBody);
        } else {
            controlMessage = new DefaultMessage();
        }

        Map<String, Object> expectedHeaders = spec.getHeaders();
        for (Map.Entry<String, Object> entry : expectedHeaders.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s) {
                value = testContext.replaceDynamicContentInString(s);
            }
            controlMessage.setHeader(entry.getKey(), value);
        }

        return controlMessage;
    }

    private String resolveMessageType(CitrusEndpoint endpoint, MessageValidationSpec spec) {
        String type = endpoint.getMessageType();
        if (type != null) {
            if (MessageType.knows(type)) {
                return MessageType.valueOf(type.toUpperCase(Locale.ROOT)).name();
            } else {
                return type.toUpperCase(Locale.ROOT);
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
    private void performValidation(
            DefaultMessage receivedMessage, DefaultMessage controlMessage,
            TestContext testContext, List<ValidationContext> validationContexts,
            String messageType) {

        MessageValidatorRegistry registry = testContext.getMessageValidatorRegistry();
        List<MessageValidator<? extends ValidationContext>> validators
                = registry.findMessageValidators(messageType, receivedMessage);

        if (validators.isEmpty()) {
            throw new IllegalStateException(
                    "No Citrus message validator found for message type '" + messageType
                                            + "'. Make sure to add the required Citrus validation module "
                                            + "to the test classpath (e.g. citrus-validation-json, "
                                            + "citrus-validation-xml, citrus-validation-text).");
        }

        LOG.debug("Validating exchange with {} Citrus validator(s) for message type '{}'",
                validators.size(), messageType);

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
}
