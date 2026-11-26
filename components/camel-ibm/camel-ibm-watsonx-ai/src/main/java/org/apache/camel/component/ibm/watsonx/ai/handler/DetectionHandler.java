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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ibm.watsonx.ai.detection.BaseDetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionContextRequest;
import com.ibm.watsonx.ai.detection.DetectionContextResponse;
import com.ibm.watsonx.ai.detection.DetectionGeneratedRequest;
import com.ibm.watsonx.ai.detection.DetectionGeneratedResponse;
import com.ibm.watsonx.ai.detection.DetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.AnswerRelevance;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
import com.ibm.watsonx.ai.detection.detector.ContextRelevance;
import com.ibm.watsonx.ai.detection.detector.Faithfulness;
import com.ibm.watsonx.ai.detection.detector.GraniteGuardian;
import com.ibm.watsonx.ai.detection.detector.Hap;
import com.ibm.watsonx.ai.detection.detector.Pii;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConfiguration;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;

/**
 * Handler for detection (PII/HAP) operations.
 */
public class DetectionHandler extends AbstractWatsonxAiHandler {

    public DetectionHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        return switch (operation) {
            case detect -> processDetect(exchange);
            case detectContext -> processDetectContext(exchange);
            case detectGenerated -> processDetectGenerated(exchange);
            default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
        };
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.detect, WatsonxAiOperations.detectContext, WatsonxAiOperations.detectGenerated };
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processDetect(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get input from body or header
        String input = getInput(exchange);

        // Build detectors list from configuration or header
        List<BaseDetector> detectors = in.getHeader(WatsonxAiConstants.DETECTORS, List.class);

        if (detectors == null && config.getDetectors() != null) {
            detectors = namedDetectors(config.getDetectors(), config.getDetectionThreshold());
        }
        if (detectors == null) {
            detectors = new ArrayList<>();

            // Build from configuration
            Boolean detectPii = config.getDetectPii();
            Boolean detectHap = config.getDetectHap();
            Double threshold = config.getDetectionThreshold();

            if (detectPii == null && detectHap == null) {
                // Default to both PII and HAP detection
                detectors.add(Pii.ofDefaults());
                if (threshold != null) {
                    detectors.add(Hap.builder().threshold(threshold).build());
                } else {
                    detectors.add(Hap.ofDefaults());
                }
            } else {
                if (Boolean.TRUE.equals(detectPii)) {
                    detectors.add(Pii.ofDefaults());
                }
                if (Boolean.TRUE.equals(detectHap)) {
                    if (threshold != null) {
                        detectors.add(Hap.builder().threshold(threshold).build());
                    } else {
                        detectors.add(Hap.ofDefaults());
                    }
                }
            }
        }

        if (detectors.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one detector must be configured (detectPii=true or detectHap=true) or provided via header '"
                                               + WatsonxAiConstants.DETECTORS + "'");
        }

        // Build request
        DetectionTextRequest request = DetectionTextRequest.builder()
                .input(input)
                .detectors(detectors)
                .build();

        // Call the service
        DetectionService service = endpoint.getDetectionService();
        DetectionResponse<DetectionTextResponse> response = service.detect(request);

        // Process results
        List<DetectionTextResponse> detections = response.detections();
        // a Granite Guardian answer of "No" (such as a risk below 0.5 above a low threshold) is not a detection
        boolean hasDetections = detections.stream().anyMatch(d -> !"No".equalsIgnoreCase(d.detection()));

        // Build detection scores map
        Map<String, List<Map<String, Object>>> detectionsByType = new HashMap<>();
        for (DetectionTextResponse detection : detections) {
            String type = detection.detectionType();
            detectionsByType.computeIfAbsent(type, k -> new ArrayList<>());

            Map<String, Object> detectionInfo = new HashMap<>();
            detectionInfo.put("text", detection.text());
            detectionInfo.put("detection", detection.detection());
            detectionInfo.put("score", detection.score());
            detectionInfo.put("start", detection.start());
            detectionInfo.put("end", detection.end());

            detectionsByType.get(type).add(detectionInfo);
        }

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DETECTED, hasDetections);
        headers.put(WatsonxAiConstants.DETECTION_RESULTS, detectionsByType);
        headers.put(WatsonxAiConstants.DETECTION_COUNT, detections.size());

        return WatsonxAiOperationResponse.create(detections, headers);
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processDetectContext(Exchange exchange) {
        Message in = exchange.getIn();
        String input = getInput(exchange);
        Object contextHeader = in.getHeader(WatsonxAiConstants.DETECTION_CONTEXT);
        List<String> context = contextHeader instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : contextHeader != null ? List.of(contextHeader.toString()) : null;
        if (context == null || context.isEmpty()) {
            throw new IllegalArgumentException(
                    "The context must be provided via header '" + WatsonxAiConstants.DETECTION_CONTEXT + "'");
        }

        DetectionContextRequest request = DetectionContextRequest.builder()
                .input(input)
                .context(context)
                .detectors(detectorsOf(in, "context_relevance"))
                .build();
        DetectionResponse<DetectionContextResponse> response = endpoint.getDetectionService().detectContext(request);

        Map<String, List<Map<String, Object>>> detectionsByType = new HashMap<>();
        for (DetectionContextResponse detection : response.detections()) {
            Map<String, Object> info = detectionInfo(detection);
            info.put("detectorId", detection.detectorId());
            info.put("evidence", detection.evidence());
            detectionsByType.computeIfAbsent(detection.detectionType(), k -> new ArrayList<>()).add(info);
        }
        return relevanceResponse(response.detections(), detectionsByType);
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processDetectGenerated(Exchange exchange) {
        Message in = exchange.getIn();
        String generatedText = getInput(exchange);
        String prompt = in.getHeader(WatsonxAiConstants.DETECTION_PROMPT, String.class);
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "The prompt must be provided via header '" + WatsonxAiConstants.DETECTION_PROMPT + "'");
        }

        DetectionGeneratedRequest request = DetectionGeneratedRequest.builder()
                .prompt(prompt)
                .generatedText(generatedText)
                .detectors(detectorsOf(in, "answer_relevance"))
                .build();
        DetectionResponse<DetectionGeneratedResponse> response = endpoint.getDetectionService().detectGenerated(request);

        Map<String, List<Map<String, Object>>> detectionsByType = new HashMap<>();
        for (DetectionGeneratedResponse detection : response.detections()) {
            Map<String, Object> info = detectionInfo(detection);
            info.put("detectorId", detection.detectorId());
            detectionsByType.computeIfAbsent(detection.detectionType(), k -> new ArrayList<>()).add(info);
        }
        return relevanceResponse(response.detections(), detectionsByType);
    }

    private static Map<String, Object> detectionInfo(BaseDetectionResponse detection) {
        Map<String, Object> info = new HashMap<>();
        info.put("text", detection.text());
        info.put("detection", detection.detection());
        info.put("score", detection.score());
        return info;
    }

    /**
     * The detections of detectContext and detectGenerated, which grade relevance or faithfulness rather than find
     * harmful content: they give no Detected header.
     */
    private static WatsonxAiOperationResponse relevanceResponse(
            List<?> detections, Map<String, List<Map<String, Object>>> detectionsByType) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DETECTED, null);
        headers.put(WatsonxAiConstants.DETECTION_RESULTS, detectionsByType);
        headers.put(WatsonxAiConstants.DETECTION_COUNT, detections.size());
        return WatsonxAiOperationResponse.create(detections, headers);
    }

    @SuppressWarnings("unchecked")
    private List<BaseDetector> detectorsOf(Message in, String defaultDetector) {
        List<BaseDetector> detectors = in.getHeader(WatsonxAiConstants.DETECTORS, List.class);
        if (detectors != null) {
            return detectors;
        }
        WatsonxAiConfiguration config = getConfiguration();
        return namedDetectors(config.getDetectors() != null ? config.getDetectors() : defaultDetector,
                config.getDetectionThreshold());
    }

    /**
     * The detectors of their names, comma separated, with the threshold for all but pii.
     */
    static List<BaseDetector> namedDetectors(String names, Double threshold) {
        List<BaseDetector> detectors = new ArrayList<>();
        for (String name : toList(null, names)) {
            detectors.add(switch (name.toLowerCase(Locale.ROOT)) {
                case "hap" -> threshold != null ? Hap.ofThreshold(threshold) : Hap.ofDefaults();
                case "pii" -> Pii.ofDefaults();
                case "granite_guardian" -> threshold != null
                        ? GraniteGuardian.ofThreshold(threshold) : GraniteGuardian.ofDefaults();
                case "context_relevance" -> threshold != null
                        ? ContextRelevance.ofThreshold(threshold) : ContextRelevance.ofDefaults();
                case "faithfulness" -> threshold != null ? Faithfulness.ofThreshold(threshold) : Faithfulness.ofDefaults();
                case "answer_relevance" -> threshold != null
                        ? AnswerRelevance.ofThreshold(threshold) : AnswerRelevance.ofDefaults();
                default -> throw new IllegalArgumentException(
                        "Unknown detector " + name + ": use hap, pii, granite_guardian, context_relevance, faithfulness"
                                                              + " or answer_relevance");
            });
        }
        return detectors;
    }
}
