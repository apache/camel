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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
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

    private static final Set<String> BINARY_MESSAGE_TYPES = Set.of(
            MessageType.BINARY.name(), MessageType.BINARY_BASE64.name(),
            MessageType.GZIP.name(), MessageType.GZIP_BASE64.name());

    private final Supplier<TestContext> testContextSupplier;
    private final String messageType;
    private final MessageValidationSpec spec;
    private final MessageResponseSpec responseSpec;

    CitrusValidationProcessor(Supplier<TestContext> testContextSupplier, String messageType,
                              MessageValidationSpec validationSpec) {
        this(testContextSupplier, messageType, validationSpec, null);
    }

    CitrusValidationProcessor(Supplier<TestContext> testContextSupplier, String messageType,
                              MessageValidationSpec validationSpec,
                              MessageResponseSpec responseSpec) {
        this.testContextSupplier = testContextSupplier;
        this.messageType = messageType;
        this.spec = validationSpec;
        this.responseSpec = responseSpec;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        TestContext testContext = testContextSupplier.get();
        propagateExchangeVariables(exchange, testContext);
        Map<String, Object> propagated = new HashMap<>(testContext.getVariables());

        try {
            if (spec != null) {
                String resolvedType = resolveMessageType(spec);
                DefaultMessage receivedMessage = buildReceivedMessage(exchange, resolvedType);
                DefaultMessage controlMessage = buildControlMessage(spec, testContext);
                controlMessage.setType(resolvedType);

                List<ValidationContext> validationContexts = buildValidationContexts(spec);
                performCitrusValidation(receivedMessage, controlMessage, testContext, validationContexts, resolvedType);
            }

            if (responseSpec != null) {
                applyResponse(exchange, responseSpec, testContext);
            }
        } finally {
            writeBackExtractedVariables(exchange, testContext, propagated);
        }
    }

    // --- Citrus validation helpers ---

    private DefaultMessage buildReceivedMessage(Exchange exchange, String resolvedType) {
        Message in = exchange.getIn();
        Object body = in.getBody();
        if ((body instanceof InputStream || body instanceof Reader) && !(body instanceof StreamCache)) {
            // cache a plain stream, so the validation does not consume it for the next step in the route
            StreamCache cache = exchange.getContext().getTypeConverter().convertTo(StreamCache.class, exchange, body);
            if (cache != null) {
                in.setBody(cache);
                body = cache;
            }
        }
        // the binary validators compare bytes, the others compare text
        Class<?> payloadType = BINARY_MESSAGE_TYPES.contains(resolvedType) ? byte[].class : String.class;
        if (body instanceof StreamCache sc) {
            body = in.getBody(payloadType);
            sc.reset();
        } else if (body instanceof byte[] || body instanceof File || body instanceof Path || body instanceof WrappedFile) {
            // read the content, as Citrus would otherwise compare a byte[] as [123, 34, ...] and a file as its path
            body = in.getBody(payloadType);
        }
        Map<String, Object> headers = new LinkedHashMap<>(in.getHeaders());
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
            contexts.add(new HeaderValidationContext.Builder().ignoreCase(true).build());
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

    private void writeBackExtractedVariables(Exchange exchange, TestContext testContext, Map<String, Object> propagated) {
        for (Map.Entry<String, Object> entry : testContext.getVariables().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("citrus.") || key.startsWith("CITRUS_")) {
                continue;
            }
            Object value = entry.getValue();
            if (!propagated.containsKey(key) || !Objects.equals(propagated.get(key), value)) {
                exchange.setVariable(key, value);
            }
        }
    }
}
