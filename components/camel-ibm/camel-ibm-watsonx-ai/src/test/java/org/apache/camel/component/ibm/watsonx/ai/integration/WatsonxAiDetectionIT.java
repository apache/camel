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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.util.List;
import java.util.Map;

import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai detection operations. These tests require valid IBM Cloud credentials to be provided
 * as system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.apiKey - IBM Cloud API key</li>
 * <li>camel.ibm.watsonx.ai.projectId - watsonx.ai project ID</li>
 * <li>camel.ibm.watsonx.ai.baseUrl - watsonx.ai base URL (optional, defaults to us-south)</li>
 * </ul>
 *
 * To run these tests, execute:
 *
 * <pre>
 * mvn verify -Dcamel.ibm.watsonx.ai.apiKey=YOUR_API_KEY -Dcamel.ibm.watsonx.ai.projectId=YOUR_PROJECT_ID
 * </pre>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided")
})
public class WatsonxAiDetectionIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiDetectionIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPiiDetection() throws Exception {
        mockResult.expectedMessageCount(1);

        final String textWithPii = "Contact John Smith at john.smith@example.com or call 555-123-4567";

        template.sendBody("direct:detect", textWithPii);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof List, "Response should be a list of detections");

        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) body;
        LOG.info("Input text: {}", textWithPii);
        LOG.info("Number of detections: {}", detections.size());

        for (DetectionTextResponse detection : detections) {
            LOG.info("Detection: type={}, text={}, detection={}, score={}, start={}, end={}",
                    detection.detectionType(),
                    detection.text(),
                    detection.detection(),
                    detection.score(),
                    detection.start(),
                    detection.end());
        }

        // Verify headers are set
        Boolean detected = exchange.getIn().getHeader(WatsonxAiConstants.DETECTED, Boolean.class);
        Integer count = exchange.getIn().getHeader(WatsonxAiConstants.DETECTION_COUNT, Integer.class);
        Map<String, List<Map<String, Object>>> results = exchange.getIn().getHeader(
                WatsonxAiConstants.DETECTION_RESULTS, Map.class);

        assertNotNull(detected, "Detected header should be set");
        assertNotNull(count, "Detection count should be set");
        assertNotNull(results, "Detection results should be set");

        LOG.info("Detected: {}", detected);
        LOG.info("Detection count: {}", count);
        LOG.info("Detection results by type: {}", results.keySet());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPiiOnlyDetection() throws Exception {
        mockResult.expectedMessageCount(1);

        final String textWithPii = "My email is test@example.com";

        template.sendBody("direct:detectPiiOnly", textWithPii);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) exchange.getIn().getBody();

        assertNotNull(detections, "Detections should not be null");
        LOG.info("PII-only detection found {} items", detections.size());

        for (DetectionTextResponse detection : detections) {
            LOG.info("PII Detection: type={}, text={}", detection.detectionType(), detection.text());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHapDetectionWithThreshold() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "This is a normal sentence without harmful content.";

        template.sendBody("direct:detectHapWithThreshold", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) exchange.getIn().getBody();

        assertNotNull(detections, "Detections should not be null");

        Boolean detected = exchange.getIn().getHeader(WatsonxAiConstants.DETECTED, Boolean.class);
        LOG.info("HAP detection with threshold - detected: {}, count: {}", detected, detections.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDetectionWithCustomDetectorsViaHeader() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Contact support at help@company.com";

        template.send("direct:detectDefault", exchange -> {
            exchange.getIn().setBody(text);
            exchange.getIn().setHeader(WatsonxAiConstants.DETECTORS, List.of(
                    Pii.ofDefaults(),
                    Hap.builder().threshold(0.5).build()));
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) exchange.getIn().getBody();

        assertNotNull(detections, "Detections should not be null");
        LOG.info("Custom detectors via header - found {} detections", detections.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNoDetectionsForCleanText() throws Exception {
        mockResult.expectedMessageCount(1);

        final String cleanText = "The weather today is sunny and warm.";

        template.sendBody("direct:detectPiiOnly", cleanText);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) exchange.getIn().getBody();

        assertNotNull(detections, "Detections should not be null");

        Boolean detected = exchange.getIn().getHeader(WatsonxAiConstants.DETECTED, Boolean.class);
        Integer count = exchange.getIn().getHeader(WatsonxAiConstants.DETECTION_COUNT, Integer.class);

        LOG.info("Clean text detection - detected: {}, count: {}", detected, count);

        // Clean text should have no PII detections
        assertEquals(0, count, "Clean text should have no PII detections");
        assertFalse(detected, "Detected should be false for clean text");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGraniteGuardianBenignAndHarmfulText() throws Exception {
        mockResult.expectedMessageCount(2);

        template.send("direct:detectDefault", exchange -> {
            exchange.getIn().setBody("The weather today is sunny and warm.");
            exchange.getIn().setHeader(WatsonxAiConstants.DETECTORS, List.of(GraniteGuardian.ofThreshold(0.01)));
        });
        template.send("direct:detectDefault", exchange -> {
            exchange.getIn().setBody("I hate you, you stupid idiot, I am going to hurt you.");
            exchange.getIn().setHeader(WatsonxAiConstants.DETECTORS, List.of(GraniteGuardian.ofThreshold(0.01)));
        });

        mockResult.assertIsSatisfied();

        Exchange benign = mockResult.getExchanges().get(0);
        Exchange harmful = mockResult.getExchanges().get(1);
        for (Exchange exchange : List.of(benign, harmful)) {
            for (DetectionTextResponse detection : (List<DetectionTextResponse>) exchange.getIn().getBody()) {
                LOG.info("Granite Guardian: type={}, detection={}, score={}, text={}", detection.detectionType(),
                        detection.detection(), detection.score(), detection.text());
            }
            LOG.info("Granite Guardian: detected={}, count={}",
                    exchange.getIn().getHeader(WatsonxAiConstants.DETECTED),
                    exchange.getIn().getHeader(WatsonxAiConstants.DETECTION_COUNT));
        }
        // with a low threshold Granite Guardian also returns its answer No (a risk below 0.5): only Yes is a detection
        for (Exchange exchange : List.of(benign, harmful)) {
            List<DetectionTextResponse> detections = (List<DetectionTextResponse>) exchange.getIn().getBody();
            boolean yes = detections.stream().anyMatch(d -> "Yes".equalsIgnoreCase(d.detection()));
            assertEquals(yes, exchange.getIn().getHeader(WatsonxAiConstants.DETECTED, Boolean.class));
            assertEquals(detections.size(), exchange.getIn().getHeader(WatsonxAiConstants.DETECTION_COUNT));
        }
        assertFalse(benign.getIn().getHeader(WatsonxAiConstants.DETECTED, Boolean.class),
                "A benign text is not harmful content");
    }

    @Test
    @SuppressWarnings("unchecked")
    @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.governanceInstanceId", matches = ".+",
                             disabledReason = "No watsonx.governance instance for the context and generated detectors")
    public void testDetectContextAndGenerated() throws Exception {
        String governance = "&governanceInstanceId=" + System.getProperty("camel.ibm.watsonx.ai.governanceInstanceId");

        Exchange relevant = template.send(buildDetectionEndpointUri(null, null, null).replace("operation=detect",
                "operation=detectContext") + governance, exchange -> {
                    exchange.getIn().setBody("What is the capital of France?");
                    exchange.getIn().setHeader(WatsonxAiConstants.DETECTION_CONTEXT,
                            List.of("Paris is the capital and largest city of France."));
                });
        Exchange irrelevant = template.send(buildDetectionEndpointUri(null, null, null).replace("operation=detect",
                "operation=detectContext") + governance, exchange -> {
                    exchange.getIn().setBody("What is the capital of France?");
                    exchange.getIn().setHeader(WatsonxAiConstants.DETECTION_CONTEXT,
                            List.of("Bananas are rich in potassium and grow in tropical regions."));
                });
        Exchange faithful = template.send(buildDetectionEndpointUri(null, null, null).replace("operation=detect",
                "operation=detectContext") + governance + "&detectors=faithfulness", exchange -> {
                    exchange.getIn().setBody("The capital of France is Paris.");
                    exchange.getIn().setHeader(WatsonxAiConstants.DETECTION_CONTEXT,
                            List.of("Paris is the capital and largest city of France."));
                });
        Exchange answer = template.send(buildDetectionEndpointUri(null, null, null).replace("operation=detect",
                "operation=detectGenerated") + governance, exchange -> {
                    exchange.getIn().setBody("The capital of France is Paris.");
                    exchange.getIn().setHeader(WatsonxAiConstants.DETECTION_PROMPT, "What is the capital of France?");
                });

        for (Exchange exchange : List.of(relevant, irrelevant, faithful, answer)) {
            assertNull(exchange.getException(), () -> "Failed: " + exchange.getException());
            LOG.info("Context/generated detection: {} {}", exchange.getMessage().getBody(),
                    exchange.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS));
        }
        Map<String, List<Map<String, Object>>> relevantResults
                = relevant.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS, Map.class);
        Map<String, List<Map<String, Object>>> irrelevantResults
                = irrelevant.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS, Map.class);
        double relevantScore = (double) relevantResults.get("context_relevance").get(0).get("score");
        double irrelevantScore = (double) irrelevantResults.get("context_relevance").get(0).get("score");
        assertTrue(relevantScore > irrelevantScore,
                "The relevant context should score higher: " + relevantScore + " vs " + irrelevantScore);
        assertTrue(faithful.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS, Map.class)
                .containsKey("faithfulness"));
        assertTrue(answer.getMessage().getHeader(WatsonxAiConstants.DETECTION_RESULTS, Map.class)
                .containsKey("answer_relevance"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDetectGraniteGuardianByName() throws Exception {
        // the detectors option names Granite Guardian, as a YAML or XML route can
        Exchange result = template.send(buildDetectionEndpointUri(null, null, 0.01) + "&detectors=granite_guardian",
                exchange -> exchange.getIn().setBody("I hate you, you stupid idiot, I am going to hurt you."));

        assertNull(result.getException());
        List<DetectionTextResponse> detections = (List<DetectionTextResponse>) result.getMessage().getBody();
        LOG.info("Granite Guardian by name: {}", detections);
        assertFalse(detections.isEmpty());
        assertEquals("risk", detections.get(0).detectionType());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Default detection (both PII and HAP)
                from("direct:detect")
                        .to(buildDetectionEndpointUri(null, null, null))
                        .to("mock:result");

                // PII only detection
                from("direct:detectPiiOnly")
                        .to(buildDetectionEndpointUri(true, false, null))
                        .to("mock:result");

                // HAP detection with threshold
                from("direct:detectHapWithThreshold")
                        .to(buildDetectionEndpointUri(false, true, 0.3))
                        .to("mock:result");

                // Default (for custom detector testing via header)
                from("direct:detectDefault")
                        .to(buildDetectionEndpointUri(null, null, null))
                        .to("mock:result");
            }
        };
    }

    private String buildDetectionEndpointUri(Boolean detectPii, Boolean detectHap, Double threshold) {
        StringBuilder uri = new StringBuilder("ibm-watsonx-ai://detect");
        uri.append("?apiKey=RAW(").append(apiKey).append(")");
        uri.append("&baseUrl=").append(baseUrl);
        uri.append("&projectId=").append(projectId);
        uri.append("&operation=detect");

        if (detectPii != null) {
            uri.append("&detectPii=").append(detectPii);
        }
        if (detectHap != null) {
            uri.append("&detectHap=").append(detectHap);
        }
        if (threshold != null) {
            uri.append("&detectionThreshold=").append(threshold);
        }

        return uri.toString();
    }
}
