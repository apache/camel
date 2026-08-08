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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.openai.models.moderations.Moderation;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for moderating text against the usage policies.
 * <p>
 * The message body is passed through unchanged so the verdict can be used for content-based routing while the original
 * content is still available to the rest of the route.
 */
public class OpenAIModerationProducer extends DefaultAsyncProducer {

    public OpenAIModerationProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            processInternal(exchange);
            callback.done(true);
            return true;
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    private void processInternal(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();

        String model = resolveParameter(in, OpenAIConstants.MODERATION_MODEL,
                config.getModerationModel(), String.class);

        if (ObjectHelper.isEmpty(model)) {
            throw new IllegalArgumentException("Moderation model must be specified via moderationModel parameter");
        }

        List<String> inputs = extractInputs(in);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("No input text provided for moderation");
        }

        ModerationCreateParams.Builder paramsBuilder = ModerationCreateParams.builder()
                .model(model);

        if (inputs.size() == 1) {
            paramsBuilder.input(inputs.get(0));
        } else {
            paramsBuilder.inputOfStrings(inputs);
        }

        ModerationCreateResponse response = getEndpoint().getClient()
                .moderations().create(paramsBuilder.build());

        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.MODERATION_RESPONSE, response);
        }

        setResponseHeaders(exchange.getMessage(), response, inputs.size());
    }

    private List<String> extractInputs(Message in) {
        Object body = in.getBody();
        List<String> inputs = new ArrayList<>();

        if (body instanceof String text) {
            inputs.add(text);
        } else if (body instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    inputs.add(s);
                } else {
                    inputs.add(String.valueOf(item));
                }
            }
        } else if (body != null) {
            inputs.add(in.getBody(String.class));
        }

        return inputs;
    }

    private void setResponseHeaders(Message message, ModerationCreateResponse response, int inputCount) {
        message.setHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL, response.model());

        List<Moderation> results = response.results();
        boolean flagged = results.stream().anyMatch(Moderation::flagged);
        message.setHeader(OpenAIConstants.MODERATION_FLAGGED, flagged);

        List<Map<String, Boolean>> categories = new ArrayList<>(results.size());
        List<Map<String, Double>> categoryScores = new ArrayList<>(results.size());
        for (Moderation result : results) {
            categories.add(toCategories(result.categories()));
            categoryScores.add(toCategoryScores(result.categoryScores()));
        }

        // a single input yields a single verdict, so unwrap it for convenient routing
        if (inputCount == 1 && categories.size() == 1) {
            message.setHeader(OpenAIConstants.MODERATION_CATEGORIES, categories.get(0));
            message.setHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, categoryScores.get(0));
        } else {
            message.setHeader(OpenAIConstants.MODERATION_CATEGORIES, categories);
            message.setHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES, categoryScores);
        }
    }

    private Map<String, Boolean> toCategories(Moderation.Categories categories) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("harassment", categories.harassment());
        map.put("harassment/threatening", categories.harassmentThreatening());
        map.put("hate", categories.hate());
        map.put("hate/threatening", categories.hateThreatening());
        categories.illicit().ifPresent(value -> map.put("illicit", value));
        categories.illicitViolent().ifPresent(value -> map.put("illicit/violent", value));
        map.put("self-harm", categories.selfHarm());
        map.put("self-harm/instructions", categories.selfHarmInstructions());
        map.put("self-harm/intent", categories.selfHarmIntent());
        map.put("sexual", categories.sexual());
        map.put("sexual/minors", categories.sexualMinors());
        map.put("violence", categories.violence());
        map.put("violence/graphic", categories.violenceGraphic());
        return map;
    }

    private Map<String, Double> toCategoryScores(Moderation.CategoryScores scores) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("harassment", scores.harassment());
        map.put("harassment/threatening", scores.harassmentThreatening());
        map.put("hate", scores.hate());
        map.put("hate/threatening", scores.hateThreatening());
        map.put("illicit", scores.illicit());
        map.put("illicit/violent", scores.illicitViolent());
        map.put("self-harm", scores.selfHarm());
        map.put("self-harm/instructions", scores.selfHarmInstructions());
        map.put("self-harm/intent", scores.selfHarmIntent());
        map.put("sexual", scores.sexual());
        map.put("sexual/minors", scores.sexualMinors());
        map.put("violence", scores.violence());
        map.put("violence/graphic", scores.violenceGraphic());
        return map;
    }

    private <T> T resolveParameter(
            Message message, String headerName,
            T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }
}
