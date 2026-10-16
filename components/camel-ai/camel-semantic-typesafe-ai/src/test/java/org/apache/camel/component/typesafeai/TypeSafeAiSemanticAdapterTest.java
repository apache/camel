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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticEvaluations;
import org.apache.camel.semantic.SemanticResult;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeSafeAiSemanticAdapterTest extends TypeSafeAiTestSupport {
    @Test
    void rejectsImagesAndBinaryStateBeforeInitializingTransport() {
        var adapter = new TypeSafeAiSemanticAdapter();
        var question = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Assess"));
        var image = Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,AA==")));
        for (Object input : List.of(new byte[] { 1 }, image, Map.of("nested", image), "data:image/png;base64,AA==")) {
            assertThatThrownBy(() -> adapter.validateInput(question, input)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> adapter.evaluateBatch(Map.of("q", question), input))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(requests).isEmpty();
    }

    @BeforeEach
    void registerExpert() {
        var expert = new TypeSafeAiSemanticAdapter();
        expert.setCamelContext(context);
        context.getRegistry().bind("decisions", expert);
    }

    @ParameterizedTest
    @CsvSource({ "0.25, fail", "0.5, fail", "0.75, fail", "0.25, non-match", "0.5, non-match", "0.75, non-match" })
    void inclusiveUncertaintyBandIsAppliedByTheAdapter(double probability, String policy) throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", probability)));
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var question = new SemanticEvaluation(
                "boolean", "decisions", null, Map.of(
                        "instructions", "Classify", "threshold", 0.5, "uncertainty", 0.25, "uncertaintyPolicy", policy));
        if (policy.equals("fail")) {
            assertThatThrownBy(() -> adapter.evaluate(question, "text"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("uncertain");
        } else {
            var result = adapter.evaluate(question, "text");
            assertThat(result.getValue()).isEqualTo(false);
            assertThat(result.getProbability()).isEqualTo(probability);
        }
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({ "0.249, false", "0.751, true" })
    void outsideUncertaintyBandUsesThreshold(double probability, boolean expected) throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", probability)));
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var question = new SemanticEvaluation(
                "boolean", "decisions", null,
                Map.of("instructions", "Classify", "threshold", 0.5, "uncertainty", 0.25));
        assertThat(adapter.evaluate(question, "text").getValue()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.1, 0.9 })
    void invalidUncertaintyBandFailsWithoutTransport(double threshold) {
        var adapter = new TypeSafeAiSemanticAdapter();
        var question = new SemanticEvaluation(
                "boolean", "decisions", null,
                Map.of("instructions", "Classify", "threshold", threshold, "uncertainty", 0.25));
        assertThatThrownBy(() -> adapter.validate(question)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("band within [0,1]");
        assertThat(requests).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "probability, Expected a number",
            "answer, Answer names must match question names",
            "answers, Expected an object"
    })
    void missingBooleanResponseFieldsFailBeforeAdapterMapping(String missing, String message) throws Exception {
        JsonObject response = (JsonObject) Jsoner.deserialize(result(Map.of("question", Map.of("type", "noul", "noul", 0.9))));
        JsonObject answers = response.getJsonObject("answers");
        switch (missing) {
            case "probability" -> answers.getJsonObject("question").remove("noul");
            case "answer" -> answers.remove("question");
            case "answers" -> response.remove("answers");
            default -> throw new IllegalArgumentException(missing);
        }
        respond = request -> response.toJson();
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var evaluation = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"));
        assertThatThrownBy(() -> adapter.evaluate(evaluation, "private-input"))
                .isExactlyInstanceOf(IOException.class)
                .hasMessage("Invalid TypeSafe AI response: " + message);
        assertThat(requests).hasSize(1);
    }

    @Test
    void omittedPolicyUsesAdapterDefaults() throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.5)));
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var question = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"));
        assertThat(adapter.evaluate(question, "text").getValue()).isEqualTo(true);
        assertThat(question.getParameters()).containsOnlyKeys("instructions");
    }

    @Test
    void advertisesCapabilitiesAndRequiresInstructionsWithoutTransport() {
        TypeSafeAiSemanticAdapter adapter = new TypeSafeAiSemanticAdapter();
        var capabilities = SemanticCapabilities.from(adapter.getClass());
        assertThat(capabilities.getName()).isEqualTo("typesafe-ai");
        assertThat(capabilities.getArtifactId()).isEqualTo("camel-semantic-typesafe-ai");
        assertThat(capabilities.operation("choice").getParameters().get("criteria").getMaxSize()).isEqualTo(255);
        assertThat(capabilities.operation("score").getParameters().get("criteria").getMaxSize()).isEqualTo(10);
        assertThat(capabilities.operation("score").getScoreLevelsParameter()).isEqualTo("criteria");
        assertThat(capabilities.operation("choice").getScoreLevelsParameter()).isEmpty();
        assertThat(capabilities.operation("boolean").getScoreLevelsParameter()).isEmpty();
        assertThat(capabilities.getOperations()).containsOnlyKeys("boolean", "choice", "score");
        assertThat(capabilities.operation("boolean").isProbability()).isTrue();
        assertThat(capabilities.operation("boolean").isConfidence()).isFalse();
        SemanticEvaluation fixed = new SemanticEvaluation(
                "boolean", "decisions", null, Map.of("threshold", 0.5, "uncertainty", 0.0, "uncertaintyPolicy", "fail"));
        assertThatThrownBy(() -> adapter.validate(fixed)).hasMessageContaining("Parameter 'instructions' is required");
        assertThat(requests).isEmpty();
    }

    @Test
    void subclassesRetainCapabilitiesAndProviderValidation() {
        TypeSafeAiSemanticAdapter subclass = new TypeSafeAiSemanticAdapter() {
        };
        assertThat(SemanticCapabilities.from(subclass.getClass())).usingRecursiveComparison()
                .isEqualTo(SemanticCapabilities.from(TypeSafeAiSemanticAdapter.class));
        SemanticEvaluation missing = new SemanticEvaluation(
                "boolean", "decisions", null, Map.of("threshold", 0.5, "uncertainty", 0.0, "uncertaintyPolicy", "fail"));
        assertThatThrownBy(() -> subclass.validate(missing)).hasMessageContaining("Parameter 'instructions' is required");
        SemanticEvaluation score = new SemanticEvaluation(
                "score", "decisions", null,
                Map.of("instructions", "Score", "criteria", IntStream.range(0, 11).mapToObj(i -> "Level " + i).toList()));
        assertThatThrownBy(() -> subclass.validate(score))
                .hasMessageContaining("Parameter 'criteria' is outside its size constraints");
    }

    @Test
    void directEvaluationInitializesTransportWithoutPriorValidation() throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        TypeSafeAiSemanticAdapter adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        SemanticEvaluation question = new SemanticEvaluation(
                "boolean", "decisions", null,
                Map.of("instructions", "Classify", "threshold", 0.5, "uncertainty", 0.0, "uncertaintyPolicy", "fail"));
        assertThat(adapter.evaluate(question, "original").getValue()).isEqualTo(true);
        assertThat(requests).hasSize(1);
        assertThat(authorization).containsExactly("Bearer test-key");
    }

    @Test
    void rejectsCapabilityLimitsWithoutInitializingTransport() {
        TypeSafeAiSemanticAdapter adapter = new TypeSafeAiSemanticAdapter();
        Map<String, String> criteria = IntStream.range(0, 256).boxed()
                .collect(Collectors.toMap(Object::toString, i -> "Criterion " + i));
        SemanticEvaluation choice
                = new SemanticEvaluation("choice", "decisions", null, Map.of("instructions", "Classify", "criteria", criteria));
        SemanticEvaluation score = new SemanticEvaluation(
                "score", "decisions", null,
                Map.of("instructions", "Score", "criteria", IntStream.range(0, 11).mapToObj(i -> "Level " + i).toList()));
        assertThatThrownBy(() -> adapter.validate(choice)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parameter 'criteria' is outside its size constraints");
        assertThatThrownBy(() -> adapter.evaluate(score, "original")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parameter 'criteria' is outside its size constraints");
        assertThat(requests).isEmpty();
    }

    private Expression expression(String type) {
        SemanticEvaluation question = new SemanticEvaluation(type, "decisions", null, switch (type) {
            case "boolean" -> Map.of("instructions", "Classify", "threshold", 0.5,
                    "uncertainty", 0.0, "uncertaintyPolicy", "fail");
            case "choice" -> Map.of("instructions", "Classify", "criteria", Map.of("billing", "Payments", "technical", "Bugs"));
            case "score" -> Map.of("instructions", "Classify", "criteria", List.of("low", "high"));
            default -> throw new IllegalArgumentException("Unknown fixture operation");
        });
        SemanticEvaluations.get(context).replace("test", Map.of("q", question));
        return context.resolveLanguage("semantic").createExpression("ref:q");
    }

    @Test
    void namedExpertUsesConfiguredComponentWithoutProviderRoute() {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        Expression expression = expression("boolean");
        assertThat(requests).isEmpty();
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("original");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(exchange.getMessage().getBody()).isEqualTo("original");
        assertThat(authorization).containsExactly("Bearer test-key");
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo("original");
        SemanticResult answer = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(answer.getMetadata()).containsEntry("provider", "typesafe-ai").containsEntry("model", "jev-1.13.0");
        assertThat(answer.getProbability()).isEqualTo(0.9);
        assertThat(answer.getConfidence()).isNull();
    }

    @Test
    void mapsChoiceAndScoreSeparatelyFromConfidence() {
        respond = request -> result(Map.of("question", Map.of("type", "choice", "choice", "technical",
                "confidence", 0.6, "probabilities", Map.of("billing", 0.1, "technical", 0.9))));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("outage");
        assertThat(expression("choice").evaluate(exchange, String.class)).isEqualTo("technical");
        SemanticResult choice = exchange.getProperty(SemanticLanguage.RESULT, SemanticResult.class);
        assertThat(choice.getConfidence()).isEqualTo(0.6);
        assertThat(choice.getProbabilities()).containsEntry("technical", 0.9);
        respond = request -> result(Map.of("question", Map.of("type", "score", "score", 0.7,
                "confidence", 0.4, "probabilities", Map.of("0", 0.3, "1", 0.7), "legend", Map.of("0", "low", "1", "high"))));
        assertThat(expression("score").evaluate(exchange, Double.class)).isEqualTo(0.7);
    }

    private Expression batchExpression() {
        batchQuestions();
        return context.resolveLanguage("semantic").createExpression("refs:refund,department,urgency");
    }

    private void batchQuestions() {
        SemanticEvaluations.get(context).replace("test", Map.of(
                "refund",
                new SemanticEvaluation(
                        "boolean", "decisions", null,
                        Map.of("instructions", "Refund requested?", "threshold", 0.95, "uncertainty", 0.0, "uncertaintyPolicy",
                                "fail")),
                "department",
                new SemanticEvaluation(
                        "choice", "decisions", null,
                        Map.of("instructions", "Which department?", "criteria",
                                Map.of("billing", "Refunds", "technical", "Faults", "other", "Anything else"))),
                "urgency",
                new SemanticEvaluation(
                        "score", "decisions", null,
                        Map.of("instructions", "How urgent?", "criteria", List.of("Routine", "Urgent", "Critical")))));
    }

    @Test
    void xmlBatchUsesGenericLanguageAndReusesResults() throws Exception {
        batchQuestions();
        respond = request -> mixedResponse();
        PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("batch.xml", """
                <routes xmlns="http://camel.apache.org/schema/spring">
                  <route>
                    <from uri="direct:batch"/>
                    <setProperty name="decision">
                      <language language="semantic">refs:refund,department,urgency</language>
                    </setProperty>
                    <setHeader name="department">
                      <simple>${exchangeProperty.decision[department]}</simple>
                    </setHeader>
                    <setHeader name="urgency">
                      <simple>${exchangeProperty.decision[urgency]}</simple>
                    </setHeader>
                  </route>
                </routes>
                """));
        assertThat(requests).isEmpty();
        Exchange exchange = template.request("direct:batch", e -> e.getMessage().setBody("refund requested"));
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getProperty("decision", Map.class))
                .containsEntry("refund", false).containsEntry("department", "billing").containsEntry("urgency", 1.2);
        assertThat(exchange.getMessage().getHeader("department")).isEqualTo("billing");
        assertThat(exchange.getMessage().getHeader("urgency", Double.class)).isEqualTo(1.2);
        assertThat(exchange.getMessage().getBody()).isEqualTo("refund requested");
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS, Map.class)).containsKeys("refund", "department", "urgency");
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo("refund requested");
    }

    @Test
    void mixedBatchUsesOneRequestAndPreservesEveryResult() {
        respond = request -> mixedResponse();
        Expression expression = batchExpression();
        assertThat(requests).isEmpty();
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(Map.of("ticket", "refund requested"));
        assertThat(expression.evaluate(exchange, Map.class))
                .containsEntry("refund", false).containsEntry("department", "billing").containsEntry("urgency", 1.2);
        assertThat(requests).hasSize(1);
        JsonObject request = requests.peek();
        assertThat(request.getJsonObject("questions").keySet()).containsExactlyInAnyOrder("refund", "department", "urgency");
        assertThat(request.get("state")).isEqualTo(exchange.getMessage().getBody());
        assertThat(authorization).containsExactly("Bearer test-key");
        Map<?, ?> results = exchange.getProperty(SemanticLanguage.RESULTS, Map.class);
        SemanticResult refund = (SemanticResult) results.get("refund");
        SemanticResult department = (SemanticResult) results.get("department");
        SemanticResult urgency = (SemanticResult) results.get("urgency");
        assertThat(refund.getProbability()).isEqualTo(0.9);
        assertThat(refund.getConfidence()).isNull();
        assertThat(department.getProbabilities()).containsEntry("billing", 0.9);
        assertThat(department.getConfidence()).isEqualTo(0.8);
        assertThat(urgency.getProbabilities()).containsEntry("1", 0.8);
        assertThat(urgency.getConfidence()).isEqualTo(0.7);
        assertThat(urgency.getMetadata()).containsEntry("provider", "typesafe-ai").containsEntry("model", "jev-1.13.0")
                .containsKey("usage");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing", "extra", "type", "probability", "choice", "score", "confidence" })
    void malformedBatchResponseClearsPreviousDiagnostics(String failure) throws Exception {
        JsonObject response = (JsonObject) Jsoner.deserialize(mixedResponse());
        JsonObject answers = response.getJsonObject("answers");
        switch (failure) {
            case "missing" -> answers.remove("urgency");
            case "extra" -> answers.put("extra", answers.get("refund"));
            case "type" -> answers.getJsonObject("refund").put("type", "choice");
            case "probability" -> answers.getJsonObject("refund").put("noul", 1.1);
            case "choice" -> answers.getJsonObject("department").put("choice", "undeclared");
            case "score" -> answers.getJsonObject("urgency").put("score", 3);
            case "confidence" -> answers.getJsonObject("urgency").remove("confidence");
            default -> throw new IllegalArgumentException(failure);
        }
        respond = request -> response.toJson();
        Expression expression = batchExpression();
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("private-input");
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        exchange.setProperty(SemanticLanguage.RESULTS, "old");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class))
                .hasStackTraceContaining("Invalid TypeSafe AI response")
                .hasMessageNotContaining("private-input");
        assertThat(requests).hasSize(1);
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void providerErrorsClearPreviousResult(boolean batch) {
        Expression expression = batch ? batchExpression() : expression("boolean");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("private-input");
        exchange.setProperty(SemanticLanguage.RESULT, "old");
        status = 503;
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class)).hasStackTraceContaining("503")
                .hasMessageNotContaining("private-input");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void componentTimeoutBoundsSemanticEvaluation(boolean batch) {
        context.getComponent("typesafe-ai", TypeSafeAiComponent.class).getConfiguration().setRequestTimeout(100);
        holdHeaders = true;
        Expression expression = batch ? batchExpression() : expression("boolean");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("private-input");
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasStackTraceContaining("TimeoutException").hasMessageNotContaining("private-input");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "false, image/png", "true, image/png", "false, audio/wav", "true, audio/wav",
            "false, video/mp4", "true, video/mp4" })
    void languageRejectsMediaBodiesBeforeHttp(boolean batch, String contentType) {
        Expression expression = batch ? batchExpression() : expression("boolean");
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("AQID");
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType);
        assertThatThrownBy(() -> expression.evaluate(exchange, Object.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("AQID");
        assertThat(requests).isEmpty();
        assertThat(exchange.getMessage().getBody()).isEqualTo("AQID");
        assertThat(exchange.getProperty(SemanticLanguage.RESULT)).isNull();
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @Test
    void selectedTextHeaderDoesNotInheritImageBodyContentType() {
        var question = new SemanticEvaluation(
                "boolean", "decisions", "${header.selected}",
                Map.of("instructions", "Classify"));
        SemanticEvaluations.get(context).replace("test", Map.of("q", question));
        var expression = context.resolveLanguage("semantic").createExpression("ref:q");
        var exchange = new DefaultExchange(context);
        byte[] image = { 1, 2, 3 };
        exchange.getMessage().setBody(image);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "image/png");
        exchange.getMessage().setHeader("selected", "Refund requested");
        assertThat(expression.evaluate(exchange, Boolean.class)).isTrue();
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo("Refund requested");
        assertThat(exchange.getMessage().getBody()).isSameAs(image);
    }

    static Stream<Object> unsupportedInputs() {
        return Stream.of(new byte[] { 1 }, new Object(), "data:image/png;base64,private",
                Map.of("$semanticInput", List.of(Map.of("type", "text", "text", "private"))),
                Map.of("nested", Map.of("$semanticInput", List.of(Map.of("type", "text", "text", "private")))),
                Map.of("$semanticInput", List.of(Map.of("type", "image", "data", "data:image/png;base64,private"))),
                Map.of("type", "file", "file", Map.of("file_data", "private")),
                Map.of("type", "document", "source", Map.of("type", "url", "url", "https://example.test/private")),
                Map.of("type", "document", "source", Map.of("type", "base64", "data", "private")),
                Map.of("type", "binary", "bytes", List.of(1, 2)),
                Map.of("mimeType", "application/pdf", "data", "private"),
                Map.of("media_type", "Application/Pdf; version=1.7", "data", "private"),
                Map.of("mime_type", "application/zip", "bytes", List.of(1, 2)),
                Map.of("type", "image_url", "image_url", Map.of("url", "https://example.test/private")),
                Map.of("type", "input_audio", "input_audio", Map.of("data", "private", "format", "wav")),
                Map.of("items", List.of("text", Map.of("type", "video", "source", Map.of("type", "url", "url", "private")))),
                List.of("text", Map.of("nested", new byte[] { 1 })));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("unsupportedInputs")
    void rejectsUnsupportedInputInDirectCallsAndBatchesBeforeTransport(Object state) {
        var adapter = new TypeSafeAiSemanticAdapter();
        var question = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"));
        // No CamelContext: validation must precede even transport initialization.
        assertThatThrownBy(() -> adapter.validateInput(question, state)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.evaluate(question, state)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.evaluateBatch(Map.of("first", question, "second", question), state))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    static Stream<Object> supportedInputs() {
        return Stream.of(Map.of("type", "document", "url", "https://cms/42", "title", "Policy"),
                Map.of("type", "document", "source", "legal", "title", "Policy"),
                Map.of("type", "image", "url", "https://shop/p/42.jpg", "sku", 42),
                Map.of("type", "video", "url", "https://cms/42", "title", "Assembly instructions"),
                Map.of("type", "audio", "source", "library", "title", "Interview"),
                Map.of("type", "binary", "data", "A description of the file"),
                Map.of("type", "image", "data", Map.of("title", "Product photo")),
                Map.of("type", "video", "source", Map.of("url", "https://cms/42")),
                Map.of("products", List.of(Map.of("type", "image", "url", "https://shop/p/42.jpg", "sku", 42))),
                Map.of("mimeType", "image/png", "url", "https://shop/p/42.jpg", "sku", 42),
                Map.of("media_type", "application/pdf", "url", "https://cms/42", "title", "Policy"),
                Map.of("type", "file", "url", "https://cms/42", "data", "Summary"),
                List.of(Map.of("type", "document", "data", Map.of("title", "Policy"), "source", "legal")),
                Map.of("mimeType", "application/javascript", "data", "const image = 'text';"),
                Map.of("media_type", "application/vnd.example+json", "data", Map.of("text", "hello")),
                Map.of("file", "report.pdf", "bytes", List.of(1, 2)), "An image_url or data:image/png is ordinary text here",
                Map.of("type", "image", "image", "a description", "amount", 42L),
                List.of(Map.of("type", "text", "text", "hello"), Map.of("approved", true)));
    }

    @ParameterizedTest
    @MethodSource("supportedInputs")
    void directAdapterPreservesSupportedState(Object state) throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var question = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"));
        adapter.validateInput(question, state);
        assertThat(adapter.evaluate(question, state).getValue()).isEqualTo(true);
        assertThat(requests).hasSize(1);
        assertThat(requests.peek().get("state")).isEqualTo(Jsoner.deserialize(Jsoner.serialize(state)));
    }

    @Test
    void invalidPartInBatchStatePreventsEveryProviderRequest() {
        SemanticEvaluations.get(context).replace("test", Map.of(
                "first", new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify")),
                "second",
                new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"))));
        var exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(List.of("text", Map.of("parts", List.of(
                Map.of("type", "image_url", "image_url", "private")))));
        var expression = context.resolveLanguage("semantic").createExpression("refs:first,second");
        assertThatThrownBy(() -> expression.evaluate(exchange, Map.class)).hasStackTraceContaining("media parts");
        assertThat(requests).isEmpty();
        assertThat(exchange.getProperty(SemanticLanguage.RESULTS)).isNull();
    }

    @Test
    void invalidDefinitionInDirectBatchPreventsEveryProviderRequest() {
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        Map<String, SemanticEvaluation> questions = new LinkedHashMap<>();
        questions.put("first", new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify")));
        questions.put("second", new SemanticEvaluation("boolean", "decisions", null, Map.of()));
        assertThatThrownBy(() -> adapter.evaluateBatch(questions, "text")).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void componentStopAndRestartRetainsManagedTransportLifecycle() throws Exception {
        respond = request -> result(Map.of("question", Map.of("type", "noul", "noul", 0.9)));
        var adapter = new TypeSafeAiSemanticAdapter();
        adapter.setCamelContext(context);
        var question = new SemanticEvaluation("boolean", "decisions", null, Map.of("instructions", "Classify"));
        assertThat(adapter.evaluate(question, "text").getValue()).isEqualTo(true);
        var endpoint = context.getEndpoint("typesafe-ai:semantic", TypeSafeAiEndpoint.class);
        endpoint.stop();
        assertThatThrownBy(() -> adapter.evaluate(question, "text"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not started");
        assertThat(requests).hasSize(1);
        endpoint.start();
        assertThat(adapter.evaluate(question, "text").getValue()).isEqualTo(true);
        assertThat(requests).hasSize(2);
    }
}
