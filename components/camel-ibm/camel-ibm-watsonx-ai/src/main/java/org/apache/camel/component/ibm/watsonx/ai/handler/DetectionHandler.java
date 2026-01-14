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
import java.util.Map;

import com.ibm.watsonx.ai.detection.DetectionResponse;
import com.ibm.watsonx.ai.detection.DetectionService;
import com.ibm.watsonx.ai.detection.DetectionTextRequest;
import com.ibm.watsonx.ai.detection.DetectionTextResponse;
import com.ibm.watsonx.ai.detection.detector.BaseDetector;
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
        if (operation != WatsonxAiOperations.detect) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
        return processDetect(exchange);
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] { WatsonxAiOperations.detect };
    }

    @SuppressWarnings("unchecked")
    private WatsonxAiOperationResponse processDetect(Exchange exchange) {
        Message in = exchange.getIn();
        WatsonxAiConfiguration config = getConfiguration();

        // Get input from body or header
        String input = getInput(exchange);

        // Build detectors list from configuration or header
        List<BaseDetector> detectors = in.getHeader(WatsonxAiConstants.DETECTORS, List.class);

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
        boolean hasDetections = !detections.isEmpty();

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
}
