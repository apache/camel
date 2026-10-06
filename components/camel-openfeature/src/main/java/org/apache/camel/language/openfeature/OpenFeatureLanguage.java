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
package org.apache.camel.language.openfeature;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.openfeature.OpenFeatureConstants;
import org.apache.camel.component.openfeature.OpenFeatureEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.LanguageSupport;

/** Evaluates a feature flag as a boolean predicate for use in EIP constructs such as filter, choice, and validate. */
@Language(value = "openfeature", modelName = "language")
@Metadata(title = "OpenFeature", description = "Evaluate a feature flag as a boolean predicate",
          label = "language,cloud", firstVersion = "4.23.0")
public class OpenFeatureLanguage extends LanguageSupport {

    private String endpoint = "openfeature:flags";

    @Override
    public Predicate createPredicate(String expression) {
        return createPredicate(expression, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Predicate createPredicate(String expression, Object[] properties) {
        validateExpression(expression);
        Evaluation answer = new Evaluation(
                expression,
                property(String.class, properties, 0, endpoint),
                property(String.class, properties, 1, null),
                property(Map.class, properties, 2, null),
                property(String.class, properties, 3, "boolean"));
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return ExpressionToPredicateAdapter.toPredicate(answer);
    }

    @Override
    public Expression createExpression(String expression) {
        return createExpression(expression, new Object[] {});
    }

    @SuppressWarnings("unchecked")
    @Override
    public ExpressionAdapter createExpression(String expression, Object[] properties) {
        validateExpression(expression);
        Evaluation answer = new Evaluation(
                expression,
                property(String.class, properties, 0, endpoint),
                property(String.class, properties, 1, null),
                property(Map.class, properties, 2, null),
                property(String.class, properties, 3, "variant"));
        if (getCamelContext() != null) {
            answer.init(getCamelContext());
        }
        return answer;
    }

    public boolean validateExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("OpenFeature flag key must not be null or blank");
        }
        return true;
    }

    public String getEndpoint() {
        return endpoint;
    }

    /** Managed endpoint URI. Defaults to openfeature:flags, inheriting camel.component.openfeature settings. */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    private static final class Evaluation extends ExpressionAdapter {
        private final String flagKey;
        private final String endpointUri;
        private final String targetingKey;
        private final Map<String, Object> contextMap;
        private final String evaluationType;
        private OpenFeatureEndpoint endpoint;

        private Evaluation(String flagKey, String endpointUri, String targetingKey,
                           Map<String, Object> contextMap, String evaluationType) {
            this.flagKey = flagKey;
            this.endpointUri = endpointUri;
            this.targetingKey = targetingKey;
            this.contextMap = contextMap;
            this.evaluationType = evaluationType;
        }

        @Override
        public void init(CamelContext context) {
            super.init(context);
            if (endpointUri == null || !endpointUri.startsWith("openfeature:")) {
                throw new IllegalArgumentException("OpenFeature language endpoint must be an openfeature: URI");
            }
            endpoint = context.getEndpoint(endpointUri, OpenFeatureEndpoint.class);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            String localEvalType = evaluationType;
            if (type != null && (Boolean.class.isAssignableFrom(type) || boolean.class == type)) {
                localEvalType = "boolean";
            }
            Object result = doEvaluate(exchange, localEvalType);
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, result);
        }

        @Override
        public Object evaluate(Exchange exchange) {
            return doEvaluate(exchange, evaluationType);
        }

        @SuppressWarnings("unchecked")
        private Object doEvaluate(Exchange exchange, String evalType) {
            try {
                if (endpoint == null) {
                    throw new IllegalStateException("OpenFeature expression must be initialized");
                }
                String tk = targetingKey;
                if (tk == null) {
                    tk = exchange.getMessage().getHeader(OpenFeatureConstants.TARGETING_KEY, String.class);
                }
                Map<String, Object> ctx = contextMap;
                if (ctx == null) {
                    Object ctxHeader = exchange.getMessage().getHeader(OpenFeatureConstants.EVALUATION_CONTEXT);
                    if (ctxHeader instanceof Map) {
                        ctx = (Map<String, Object>) ctxHeader;
                    }
                }
                return endpoint.evaluate(exchange, flagKey, evalType, tk, ctx);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }

        @Override
        public String toString() {
            return "openfeature[" + flagKey + "]";
        }
    }
}
