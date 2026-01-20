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
