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
package org.apache.camel.component.typesafeai;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.json.JsonObject;

/** Evaluate text and structured state with the TypeSafe AI decision API. */
@UriEndpoint(firstVersion = "4.23.0", scheme = "typesafe-ai", title = "TypeSafe AI", syntax = "typesafe-ai:name",
             producerOnly = true, category = { Category.AI })
public class TypeSafeAiEndpoint extends DefaultEndpoint {
    @UriPath
    @Metadata(required = true)
    private String name;
    @UriParam
    private TypeSafeAiConfiguration configuration;
    private JsonObject configuredQuestions;
    private Expression stateExpression;

    private volatile TypeSafeAiClient client;

    public TypeSafeAiEndpoint(String uri, TypeSafeAiComponent component, String name, TypeSafeAiConfiguration configuration) {
        super(uri, component);
        this.name = name;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() {
        return new TypeSafeAiProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("TypeSafe AI is producer only");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        configuration.validate();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        configuredQuestions = null;
        stateExpression = null;
        if (configuration.getQuestions() != null) {
            configuredQuestions = TypeSafeAiJson.questions(configuration.getQuestions());
            stateExpression = createStateExpression();
        }
        client = new TypeSafeAiClient(configuration);
    }

    @Override
    protected void doStop() throws Exception {
        TypeSafeAiClient current = client;
        client = null;
        if (current != null) {
            current.close();
        }
        super.doStop();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> request(Exchange exchange) throws InvalidPayloadException {
        if (configuredQuestions == null) {
            return exchange.getMessage().getMandatoryBody(Map.class);
        }
        Object state = stateExpression.evaluate(exchange, Object.class);
        if (state == null) {
            throw new IllegalArgumentException("TypeSafe AI state must not be null");
        }
        return Map.of("state", state, "questions", configuredQuestions);
    }

    private Expression createStateExpression() {
        if (configuration.getState() == null || configuration.getState().isBlank()) {
            throw new IllegalArgumentException("state must be a nonblank Simple expression");
        }
        Expression expression = getCamelContext().resolveLanguage("simple").createExpression(configuration.getState());
        expression.init(getCamelContext());
        return expression;
    }

    /** Evaluate a request using this endpoint's managed transport, concurrency limit and timeout. */
    public JsonObject evaluate(Map<String, Object> request) throws Exception {
        TypeSafeAiClient current = client;
        if (current == null) {
            throw new IllegalStateException("TypeSafe AI endpoint is not started");
        }
        return current.evaluate(request);
    }

    public String getName() {
        return name;
    }

    /** A logical name for the evaluation endpoint. */
    public void setName(String name) {
        this.name = name;
    }

    public TypeSafeAiConfiguration getConfiguration() {
        return configuration;
    }

    /** The TypeSafe AI API configuration. */
    public void setConfiguration(TypeSafeAiConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getResultProperty() {
        return configuration.getResultProperty();
    }

    /** Store the producer result in this exchange property, preserving the original message body. */
    public void setResultProperty(String resultProperty) {
        configuration.setResultProperty(resultProperty);
    }
}
