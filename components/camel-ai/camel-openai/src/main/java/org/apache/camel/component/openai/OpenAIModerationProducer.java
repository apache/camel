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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.openai.core.JsonField;
import com.openai.models.moderations.Moderation;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import com.openai.models.moderations.ModerationImageUrlInput;
import com.openai.models.moderations.ModerationMultiModalInput;
import com.openai.models.moderations.ModerationTextInput;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ai.observability.GenAiErrorSupport;
import org.apache.camel.component.ai.observability.GenAiObservability;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiObservationContext;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for moderating text or an image against the usage policies.
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

        ModerationCreateParams.Builder paramsBuilder = ModerationCreateParams.builder()
                .model(model);

        boolean batch = in.getBody() instanceof List;
        String imageMimeType = batch ? null : resolveImageMimeType(in);
        List<String> inputs;
        if (imageMimeType != null) {
            String text = in.getHeader(OpenAIConstants.MODERATION_TEXT, String.class);
            paramsBuilder.inputOfModerationMultiModalArray(multiModalInput(readImage(in), imageMimeType, text));
            // the API scores a multi-modal input as a whole, so the text and the image share a single verdict
            inputs = Collections.singletonList(ObjectHelper.isNotEmpty(text) ? text : null);
        } else {
            inputs = extractInputs(exchange);
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("No input text provided for moderation");
            }
            if (inputs.size() == 1) {
                paramsBuilder.input(inputs.get(0));
            } else {
                paramsBuilder.inputOfStrings(inputs);
            }
        }

        GenAiObservationContext observationContext = GenAiObservationContext.builder()
                .operationName(GenAiOperationName.MODERATION)
                .system("openai")
                .requestModel(model)
                .componentScheme("openai")
                .build();
        GenAiObservation observation = GenAiObservability.start(exchange, observationContext);
        ModerationCreateResponse response;
        try {
            response = getEndpoint().getClient().moderations().create(paramsBuilder.build());
            observation.recordSuccess(GenAiUsage.of((Long) null, null, null, response.model()));
        } catch (Exception e) {
            GenAiErrorSupport.apply(exchange, e);
            observation.recordError(e);
            throw e;
        } finally {
            observation.close();
        }

        // this operation is used to gate untrusted content, so a missing verdict must fail the exchange
        // instead of leaving CamelOpenAIModerationFlagged false and letting the message through
        if (response.results().size() != inputs.size()) {
            throw new CamelExchangeException(
                    "Moderation returned " + response.results().size() + " result(s) for " + inputs.size()
                                             + " input(s)",
                    exchange);
        }

        // stored only once the response is known to be complete, so a failed exchange carries no verdict
        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.MODERATION_RESPONSE, response);
        }

        setResponseHeaders(exchange.getMessage(), response, inputs, batch);
    }

    private List<String> extractInputs(Exchange exchange) {
        Message in = exchange.getIn();
        Object body = in.getBody();
        List<String> inputs = new ArrayList<>();

        if (body instanceof String text) {
            inputs.add(text);
        } else if (body instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) {
                    // moderating the literal "null" would silently report a verdict for content that was not sent
                    throw new IllegalArgumentException("The input list for moderation must not contain null elements");
                }
                if (item instanceof String s) {
                    inputs.add(s);
                } else if (isImageFile(item)) {
                    // converted to a String, the image bytes would be moderated as text and most likely pass
                    throw new IllegalArgumentException(
                            "A list body is moderated as text, and the moderation API accepts one image per request. "
                                                       + "Split the list and moderate each image on its own");
                } else {
                    // convert as a non-String body would be, so an unconvertible type fails instead of
                    // being moderated as its toString()
                    String converted = exchange.getContext().getTypeConverter().tryConvertTo(String.class, exchange, item);
                    if (converted == null) {
                        throw new IllegalArgumentException(
                                "Cannot convert the moderation input of type " + item.getClass().getName()
                                                           + " to String");
                    }
                    inputs.add(converted);
                }
            }
        } else if (body != null) {
            inputs.add(in.getBody(String.class));
        }

        return inputs;
    }

    /**
     * Returns the MIME type of an image body, or {@code null} when the body is moderated as text. The type is detected
     * the same way as for the vision input of chat-completion, from the headers first and then from the file name.
     */
    private static String resolveImageMimeType(Message in) {
        Object body = in.getBody();
        File file = localFile(body);
        String mimeType;
        if (file != null) {
            mimeType = MimeTypeHelper.resolveForFile(in, file);
        } else if (body instanceof WrappedFile || body instanceof byte[] || body instanceof InputStream) {
            mimeType = MimeTypeHelper.resolveForBinary(in);
        } else {
            return null;
        }
        return MimeTypeHelper.isImage(mimeType) ? mimeType : null;
    }

    private static boolean isImageFile(Object value) {
        String fileName = OpenAIImageSupport.fileNameOfBody(value);
        return fileName != null && MimeTypeHelper.isImage(MimeTypeHelper.fromFileName(fileName));
    }

    private static File localFile(Object body) {
        if (body instanceof WrappedFile<?> wrappedFile && wrappedFile.getFile() instanceof File file) {
            return file;
        } else if (body instanceof File file) {
            return file;
        } else if (body instanceof Path path) {
            return path.toFile();
        }
        return null;
    }

    private static byte[] readImage(Message in) throws IOException {
        Object body = in.getBody();
        File file = localFile(body);
        if (file != null) {
            return Files.readAllBytes(file.toPath());
        } else if (body instanceof byte[] bytes) {
            return bytes;
        }

        byte[] image = in.getBody(byte[].class);
        if (image == null) {
            throw new IllegalArgumentException(
                    "Cannot read the image to moderate from a body of type " + body.getClass().getName());
        }
        // the body is passed through, so reading it here must leave it readable for the rest of the route
        if (body instanceof StreamCache) {
            MessageHelper.resetStreamCache(in);
        } else if (body instanceof InputStream) {
            in.setBody(image);
        }
        return image;
    }

    private static List<ModerationMultiModalInput> multiModalInput(byte[] image, String mimeType, String text) {
        List<ModerationMultiModalInput> parts = new ArrayList<>(2);
        if (ObjectHelper.isNotEmpty(text)) {
            parts.add(ModerationMultiModalInput.ofText(ModerationTextInput.builder().text(text).build()));
        }
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
        parts.add(ModerationMultiModalInput.ofImageUrl(ModerationImageUrlInput.builder()
                .imageUrl(ModerationImageUrlInput.ImageUrl.builder().url(dataUrl).build())
                .build()));
        return parts;
    }

    private void setResponseHeaders(
            Message message, ModerationCreateResponse response, List<String> inputs, boolean batch) {
        message.setHeader(OpenAIConstants.MODERATION_RESPONSE_MODEL, response.model());

        List<Moderation> results = response.results();
        message.setHeader(OpenAIConstants.MODERATION_FLAGGED, results.stream().anyMatch(Moderation::flagged));

        List<Map<String, Object>> verdicts = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) {
            Moderation result = results.get(i);
            Map<String, Object> verdict = new LinkedHashMap<>();
            verdict.put(OpenAIConstants.MODERATION_RESULT_INPUT, inputs.get(i));
            verdict.put(OpenAIConstants.MODERATION_RESULT_FLAGGED, result.flagged());
            verdict.put(OpenAIConstants.MODERATION_RESULT_CATEGORIES, toCategories(result.categories()));
            verdict.put(OpenAIConstants.MODERATION_RESULT_CATEGORY_SCORES, toCategoryScores(result.categoryScores()));
            // an OpenAI-compatible provider may not report the input types, which must not fail the verdict
            result._categoryAppliedInputTypes().asKnown().ifPresent(types -> verdict
                    .put(OpenAIConstants.MODERATION_RESULT_CATEGORY_APPLIED_INPUT_TYPES, toAppliedInputTypes(types)));
            verdicts.add(verdict);
        }
        message.setHeader(OpenAIConstants.MODERATION_RESULTS, verdicts);

        if (!batch && results.size() == 1) {
            message.setHeader(OpenAIConstants.MODERATION_CATEGORIES, toCategories(results.get(0).categories()));
            message.setHeader(OpenAIConstants.MODERATION_CATEGORY_SCORES,
                    toCategoryScores(results.get(0).categoryScores()));
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

    private Map<String, List<String>> toAppliedInputTypes(Moderation.CategoryAppliedInputTypes types) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        putInputTypes(map, "harassment", types._harassment());
        putInputTypes(map, "harassment/threatening", types._harassmentThreatening());
        putInputTypes(map, "hate", types._hate());
        putInputTypes(map, "hate/threatening", types._hateThreatening());
        putInputTypes(map, "illicit", types._illicit());
        putInputTypes(map, "illicit/violent", types._illicitViolent());
        putInputTypes(map, "self-harm", types._selfHarm());
        putInputTypes(map, "self-harm/instructions", types._selfHarmInstructions());
        putInputTypes(map, "self-harm/intent", types._selfHarmIntent());
        putInputTypes(map, "sexual", types._sexual());
        putInputTypes(map, "sexual/minors", types._sexualMinors());
        putInputTypes(map, "violence", types._violence());
        putInputTypes(map, "violence/graphic", types._violenceGraphic());
        return map;
    }

    private static void putInputTypes(Map<String, List<String>> map, String category, JsonField<? extends List<?>> field) {
        field.asKnown().ifPresent(values -> map.put(category, values.stream().map(Object::toString).toList()));
    }

    private <T> T resolveParameter(
            Message message, String headerName,
            T defaultValue, Class<T> type) {
        T headerValue = message.getHeader(headerName, type);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }
}
