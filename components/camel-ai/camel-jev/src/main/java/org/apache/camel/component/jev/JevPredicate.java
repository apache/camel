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
package org.apache.camel.component.jev;

import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.json.JsonObject;

/**
 * A synchronous Noul predicate. Each invocation submits freshly selected state, preserves the message body and stores
 * the complete response in {@link #RESULT}. The supplied state expression must be thread-safe.
 */
public final class JevPredicate implements Predicate {
    public static final String RESULT = "CamelJevResult";

    public enum UncertaintyPolicy {
        NonMatch,
        Fail
    }

    private final String endpointUri;
    private final Expression state;
    private final String question;
    private final String questionName;
    private final double threshold;
    private final double uncertainty;
    private final UncertaintyPolicy uncertaintyPolicy;

    public JevPredicate(String endpointUri, Expression state, String instructions, double threshold) {
        this(endpointUri, state, Map.of("type", "noul", "instructions", instructions), threshold);
    }

    public JevPredicate(String endpointUri, Expression state, Map<String, Object> question, double threshold) {
        this(endpointUri, state, question, threshold, 0, UncertaintyPolicy.NonMatch);
    }

    /**
     * @param endpointUri       the configured Jev endpoint to share with producers and other predicates
     * @param state             selects only the exchange data to submit
     * @param question          the Noul proposition
     * @param threshold         a probability at or above this value matches, outside the uncertainty band
     * @param uncertainty       half-width of the inclusive band around threshold; zero disables the band
     * @param uncertaintyPolicy whether uncertainty yields a non-match or an exception
     */
    public JevPredicate(String endpointUri, Expression state, Map<String, Object> question,
                        double threshold, double uncertainty, UncertaintyPolicy uncertaintyPolicy) {
        this(endpointUri, state, "predicate", question, threshold, uncertainty, uncertaintyPolicy);
    }

    JevPredicate(String endpointUri, Expression state, String questionName, Map<String, Object> question,
                 double threshold, double uncertainty, UncertaintyPolicy uncertaintyPolicy) {
        this.questionName = Objects.requireNonNull(questionName, "questionName");
        this.endpointUri = Objects.requireNonNull(endpointUri, "endpointUri");
        this.state = Objects.requireNonNull(state, "state");
        this.question = JevJson.noulQuestion(Objects.requireNonNull(question, "question"));
        this.uncertaintyPolicy = Objects.requireNonNull(uncertaintyPolicy, "uncertaintyPolicy");
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1
                || !Double.isFinite(uncertainty) || uncertainty < 0
                || threshold - uncertainty < 0 || threshold + uncertainty > 1) {
            throw new IllegalArgumentException("The threshold and its uncertainty band must be within [0,1]");
        }
        this.threshold = threshold;
        this.uncertainty = uncertainty;
    }

    @Override
    public void init(CamelContext context) {
        state.init(context);
        // Register the endpoint even when it is used only by a predicate, so Camel owns its lifecycle.
        context.getEndpoint(endpointUri, JevEndpoint.class);
    }

    @Override
    public boolean matches(Exchange exchange) {
        exchange.removeProperty(RESULT);
        try {
            JevEndpoint endpoint = exchange.getContext().getEndpoint(endpointUri, JevEndpoint.class);
            ServiceHelper.startService(endpoint);
            Object selected = Objects.requireNonNull(state.evaluate(exchange, Object.class), "Jev state");
            JsonObject result = endpoint.evaluate(Map.of("state", selected,
                    "questions", Map.of(questionName, JevJson.parse(question))));
            exchange.setProperty(RESULT, result);
            double probability = result.getJsonObject("answers").getJsonObject(questionName).getDouble("noul");
            if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
                if (uncertaintyPolicy == UncertaintyPolicy.Fail) {
                    throw new JevUncertainResultException(probability);
                }
                return false;
            }
            return probability >= threshold;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }
}
