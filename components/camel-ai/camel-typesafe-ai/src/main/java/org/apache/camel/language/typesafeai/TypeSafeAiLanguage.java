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
package org.apache.camel.language.typesafeai;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.typesafeai.TypeSafeAiConfiguration;
import org.apache.camel.component.typesafeai.TypeSafeAiEndpoint;
import org.apache.camel.component.typesafeai.TypeSafeAiUncertainResultException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.util.json.JsonObject;

/** Evaluates a plain-text Noul question against selected exchange state through a managed TypeSafe AI endpoint. */
@Language(value = "typesafe-ai", modelName = "language")
@Metadata(title = "TypeSafe AI", description = "Evaluate a Noul question with the TypeSafe AI decision API",
          label = "language,ai", firstVersion = "4.23.0")
public class TypeSafeAiLanguage extends LanguageSupport {
    public static final String RESULT = "CamelTypeSafeAiResult";

    public enum UncertaintyPolicy {
        NonMatch,
        Fail
    }

    private String endpoint = "typesafe-ai:default";
    private Double threshold;
    private Double uncertainty;
    private UncertaintyPolicy uncertaintyPolicy;
    private String state;

    @Override
    public Predicate createPredicate(String expression) {
        return createPredicate(expression, null);
    }

    /**
     * The expression is only the question. Optional properties, in order: endpoint URI, threshold, uncertainty,
     * uncertainty policy, state (a Simple string or a thread-safe Expression). Null entries use language defaults,
     * falling back to the selected endpoint configuration.
     */
    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        return createExpression(expression, properties);
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, null);
    }

    /** Creates a Boolean expression with the same configuration and remote evaluation as a predicate. */
    @Override
    public ExpressionAdapter createExpression(String expression, Object[] properties) {
        validateExpression(expression);
        Evaluation answer = new Evaluation(
                expression,
                property(String.class, properties, 0, endpoint),
                property(Double.class, properties, 1, threshold),
                property(Double.class, properties, 2, uncertainty),
                property(UncertaintyPolicy.class, properties, 3, uncertaintyPolicy),
                property(Object.class, properties, 4, state));
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

    // Tooling validation must not create an endpoint or make an HTTP request.
    public boolean validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("TypeSafe AI question must not be null or blank");
        }
        return true;
    }

    public boolean validatePredicate(String expression) {
        return validateExpression(expression);
    }

    public String getEndpoint() {
        return endpoint;
    }

    /** Managed endpoint URI. Defaults to typesafe-ai:default, inheriting camel.component.typesafe-ai settings. */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Double getThreshold() {
        return threshold;
    }

    /** Inclusive matching threshold. Falls back to the endpoint threshold. */
    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Double getUncertainty() {
        return uncertainty;
    }

    /** Half-width of the inclusive uncertainty band. Falls back to the endpoint uncertainty. */
    public void setUncertainty(Double uncertainty) {
        this.uncertainty = uncertainty;
    }

    public UncertaintyPolicy getUncertaintyPolicy() {
        return uncertaintyPolicy;
    }

    /** Whether an uncertain decision is a non-match or fails. Falls back to the endpoint uncertaintyPolicy. */
    public void setUncertaintyPolicy(UncertaintyPolicy uncertaintyPolicy) {
        this.uncertaintyPolicy = uncertaintyPolicy;
    }

    public String getState() {
        return state;
    }

    /** Simple expression selecting state to submit. Falls back to the endpoint state expression. */
    public void setState(String state) {
        this.state = state;
    }

    private static final class Evaluation extends ExpressionAdapter {
        private final Map<String, Object> questions;
        private final String endpointUri;
        private final Double configuredThreshold;
        private final Double configuredUncertainty;
        private final UncertaintyPolicy configuredPolicy;
        private final Object configuredState;
        private TypeSafeAiEndpoint endpoint;
        private Expression state;
        private double threshold;
        private double uncertainty;
        private UncertaintyPolicy uncertaintyPolicy;

        private Evaluation(String question, String endpointUri, Double threshold, Double uncertainty,
                           UncertaintyPolicy uncertaintyPolicy, Object state) {
            this.questions = Map.of("predicate", Map.of("type", "noul", "instructions", question));
            this.endpointUri = endpointUri;
            this.configuredThreshold = threshold;
            this.configuredUncertainty = uncertainty;
            this.configuredPolicy = uncertaintyPolicy;
            this.configuredState = state;
        }

        @Override
        public void init(CamelContext context) {
            super.init(context);
            if (endpointUri == null || !endpointUri.startsWith("typesafe-ai:")) {
                throw new IllegalArgumentException("TypeSafe AI language endpoint must be a typesafe-ai: URI");
            }
            // Register once with Camel, including when no producer uses this endpoint.
            endpoint = context.getEndpoint(endpointUri, TypeSafeAiEndpoint.class);
            TypeSafeAiConfiguration configuration = endpoint.getConfiguration();
            threshold = configuredThreshold != null ? configuredThreshold : configuration.getThreshold();
            uncertainty = configuredUncertainty != null ? configuredUncertainty : configuration.getUncertainty();
            uncertaintyPolicy = configuredPolicy != null ? configuredPolicy : configuration.getUncertaintyPolicy();
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1
                    || !Double.isFinite(uncertainty) || uncertainty < 0
                    || threshold - uncertainty < 0 || threshold + uncertainty > 1 || uncertaintyPolicy == null) {
                throw new IllegalArgumentException(
                        "The threshold and its uncertainty band must be within [0,1] "
                                                   + "and uncertaintyPolicy must be set");
            }
            Object selection = configuredState != null ? configuredState : configuration.getState();
            if (selection instanceof Expression expression) {
                state = expression;
            } else if (selection instanceof String simple && !simple.isBlank()) {
                state = context.resolveLanguage("simple").createExpression(simple);
            } else {
                throw new IllegalArgumentException("TypeSafe AI state must be an Expression or a nonblank Simple string");
            }
            state.init(context);
        }

        @Override
        public Object evaluate(Exchange exchange) {
            exchange.removeProperty(RESULT);
            try {
                if (endpoint == null) {
                    throw new IllegalStateException("TypeSafe AI expression must be initialized");
                }
                Object selected = state.evaluate(exchange, Object.class);
                if (selected == null) {
                    throw new IllegalArgumentException("TypeSafe AI state must not be null");
                }
                JsonObject result = endpoint.evaluate(Map.of("state", selected, "questions", questions));
                exchange.setProperty(RESULT, result);
                double probability = result.getJsonObject("answers").getJsonObject("predicate").getDouble("noul");
                if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
                    if (uncertaintyPolicy == UncertaintyPolicy.Fail) {
                        throw new TypeSafeAiUncertainResultException(probability);
                    }
                    return false;
                }
                return probability >= threshold;
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public String toString() {
            // Do not expose endpoint options (which may contain credentials) or submitted text.
            return "typesafe-ai[endpoint=" + (endpointUri == null ? "" : endpointUri.split("\\?", 2)[0])
                   + ", threshold=" + threshold + "]";
        }
    }
}
