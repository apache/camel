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
package org.apache.camel.component.google.vision;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

/**
 * The GoogleCloudVision producer.
 */
public class GoogleCloudVisionProducer extends DefaultProducer {
    private GoogleCloudVisionEndpoint endpoint;

    public GoogleCloudVisionProducer(GoogleCloudVisionEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        if (getConfiguration().isPojoRequest()) {
            processPojo(exchange);
        } else {
            processImage(exchange);
        }
    }

    private void processPojo(Exchange exchange) throws InvalidPayloadException {
        ImageAnnotatorClient client = endpoint.getClient();
        AnnotateImageRequest request = exchange.getIn().getMandatoryBody(AnnotateImageRequest.class);

        List<AnnotateImageRequest> requests = new ArrayList<>();
        requests.add(request);

        BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);
        AnnotateImageResponse response = batchResponse.getResponses(0);

        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudVisionConstants.RESPONSE_OBJECT, response);
        message.setBody(response);
    }

    private void processImage(Exchange exchange) throws InvalidPayloadException {
        ImageAnnotatorClient client = endpoint.getClient();
        GoogleCloudVisionOperations operation = determineOperation(exchange);

        byte[] imageData = exchange.getIn().getMandatoryBody(byte[].class);

        Image image = Image.newBuilder()
                .setContent(ByteString.copyFrom(imageData))
                .build();

        Feature.Builder featureBuilder = Feature.newBuilder()
                .setType(mapOperationToFeatureType(operation));

        if (getConfiguration().getMaxResults() != null) {
            featureBuilder.setMaxResults(getConfiguration().getMaxResults());
        }

        Feature feature = featureBuilder.build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                .addFeatures(feature)
                .setImage(image)
                .build();

        List<AnnotateImageRequest> requests = new ArrayList<>();
        requests.add(request);

        BatchAnnotateImagesResponse batchResponse = client.batchAnnotateImages(requests);
        AnnotateImageResponse response = batchResponse.getResponses(0);

        if (response.hasError()) {
            throw new RuntimeException(
                    "Google Cloud Vision API error: " + response.getError().getMessage());
        }

        Message message = getMessageForResponse(exchange);
        message.setHeader(GoogleCloudVisionConstants.RESPONSE_OBJECT, response);
        message.setBody(extractResult(response, operation));
    }

    private Feature.Type mapOperationToFeatureType(GoogleCloudVisionOperations operation) {
        switch (operation) {
            case labelDetection:
                return Feature.Type.LABEL_DETECTION;
            case textDetection:
                return Feature.Type.TEXT_DETECTION;
            case faceDetection:
                return Feature.Type.FACE_DETECTION;
            case landmarkDetection:
                return Feature.Type.LANDMARK_DETECTION;
            case logoDetection:
                return Feature.Type.LOGO_DETECTION;
            case safeSearchDetection:
                return Feature.Type.SAFE_SEARCH_DETECTION;
            case imagePropertiesDetection:
                return Feature.Type.IMAGE_PROPERTIES;
            case webDetection:
                return Feature.Type.WEB_DETECTION;
            case objectLocalization:
                return Feature.Type.OBJECT_LOCALIZATION;
            case cropHintsDetection:
                return Feature.Type.CROP_HINTS;
            case documentTextDetection:
                return Feature.Type.DOCUMENT_TEXT_DETECTION;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private Object extractResult(AnnotateImageResponse response, GoogleCloudVisionOperations operation) {
        switch (operation) {
            case labelDetection:
                return response.getLabelAnnotationsList();
            case textDetection:
                return response.getTextAnnotationsList();
            case faceDetection:
                return response.getFaceAnnotationsList();
            case landmarkDetection:
                return response.getLandmarkAnnotationsList();
            case logoDetection:
                return response.getLogoAnnotationsList();
            case safeSearchDetection:
                return response.getSafeSearchAnnotation();
            case imagePropertiesDetection:
                return response.getImagePropertiesAnnotation();
            case webDetection:
                return response.getWebDetection();
            case objectLocalization:
                return response.getLocalizedObjectAnnotationsList();
            case cropHintsDetection:
                return response.getCropHintsAnnotation();
            case documentTextDetection:
                return response.getFullTextAnnotation();
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private GoogleCloudVisionOperations determineOperation(Exchange exchange) {
        GoogleCloudVisionOperations operation = exchange.getIn().getHeader(GoogleCloudVisionConstants.OPERATION,
                GoogleCloudVisionOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperationType();
        }
        if (operation == null) {
            String operationName = getConfiguration().getOperation();
            if (operationName != null) {
                operation = GoogleCloudVisionOperations.valueOf(operationName);
            }
        }
        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified via endpoint URI, configuration, or message header.");
        }
        return operation;
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    private GoogleCloudVisionConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }
}
