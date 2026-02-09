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
package org.apache.camel.component.aws2.textract;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

/**
 * A Producer which sends messages to the Amazon Textract Service SDK v2 <a href="http://aws.amazon.com/textract/">AWS
 * Textract</a>
 */
public class Textract2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Textract2Producer.class);
    private transient String textractProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Textract2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case detectDocumentText:
                detectDocumentText(getEndpoint().getTextractClient(), exchange);
                break;
            case analyzeDocument:
                analyzeDocument(getEndpoint().getTextractClient(), exchange);
                break;
            case analyzeExpense:
                analyzeExpense(getEndpoint().getTextractClient(), exchange);
                break;
            case startDocumentTextDetection:
                startDocumentTextDetection(getEndpoint().getTextractClient(), exchange);
                break;
            case startDocumentAnalysis:
                startDocumentAnalysis(getEndpoint().getTextractClient(), exchange);
                break;
            case startExpenseAnalysis:
                startExpenseAnalysis(getEndpoint().getTextractClient(), exchange);
                break;
            case getDocumentTextDetection:
                getDocumentTextDetection(getEndpoint().getTextractClient(), exchange);
                break;
            case getDocumentAnalysis:
                getDocumentAnalysis(getEndpoint().getTextractClient(), exchange);
                break;
            case getExpenseAnalysis:
                getExpenseAnalysis(getEndpoint().getTextractClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Textract2Operations determineOperation(Exchange exchange) {
        Textract2Operations operation = exchange.getIn().getHeader(Textract2Constants.OPERATION, Textract2Operations.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Textract2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ObjectHelper.isEmpty(textractProducerToString)) {
            textractProducerToString = "TextractProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return textractProducerToString;
    }

    @Override
    public Textract2Endpoint getEndpoint() {
        return (Textract2Endpoint) super.getEndpoint();
    }

    private void detectDocumentText(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectDocumentTextRequest) {
                DetectDocumentTextResponse result;
                try {
                    result = textractClient.detectDocumentText((DetectDocumentTextRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Document Text command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DetectDocumentTextRequest.Builder request = DetectDocumentTextRequest.builder();
            Document document = createDocumentFromExchange(exchange);
            request.document(document);

            DetectDocumentTextResponse result;
            try {
                result = textractClient.detectDocumentText(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Document Text command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void analyzeDocument(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof AnalyzeDocumentRequest) {
                AnalyzeDocumentResponse result;
                try {
                    result = textractClient.analyzeDocument((AnalyzeDocumentRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Analyze Document command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            AnalyzeDocumentRequest.Builder request = AnalyzeDocumentRequest.builder();
            Document document = createDocumentFromExchange(exchange);
            request.document(document);

            // Get feature types from headers or default to TABLES and FORMS
            @SuppressWarnings("unchecked")
            List<FeatureType> featureTypes = exchange.getIn().getHeader(Textract2Constants.FEATURE_TYPES, List.class);
            if (featureTypes != null && !featureTypes.isEmpty()) {
                request.featureTypes(featureTypes);
            } else {
                request.featureTypes(FeatureType.TABLES, FeatureType.FORMS);
            }

            AnalyzeDocumentResponse result;
            try {
                result = textractClient.analyzeDocument(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Analyze Document command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void analyzeExpense(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof AnalyzeExpenseRequest) {
                AnalyzeExpenseResponse result;
                try {
                    result = textractClient.analyzeExpense((AnalyzeExpenseRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Analyze Expense command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            AnalyzeExpenseRequest.Builder request = AnalyzeExpenseRequest.builder();
            Document document = createDocumentFromExchange(exchange);
            request.document(document);

            AnalyzeExpenseResponse result;
            try {
                result = textractClient.analyzeExpense(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Analyze Expense command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startDocumentTextDetection(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartDocumentTextDetectionRequest) {
                StartDocumentTextDetectionResponse result;
                try {
                    result = textractClient.startDocumentTextDetection((StartDocumentTextDetectionRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Document Text Detection command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartDocumentTextDetectionRequest.Builder request = StartDocumentTextDetectionRequest.builder();
            DocumentLocation documentLocation = createDocumentLocationFromExchange(exchange);
            request.documentLocation(documentLocation);

            StartDocumentTextDetectionResponse result;
            try {
                result = textractClient.startDocumentTextDetection(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Document Text Detection command returned the error code {}",
                        ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startDocumentAnalysis(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartDocumentAnalysisRequest) {
                StartDocumentAnalysisResponse result;
                try {
                    result = textractClient.startDocumentAnalysis((StartDocumentAnalysisRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Document Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartDocumentAnalysisRequest.Builder request = StartDocumentAnalysisRequest.builder();
            DocumentLocation documentLocation = createDocumentLocationFromExchange(exchange);
            request.documentLocation(documentLocation);

            @SuppressWarnings("unchecked")
            List<FeatureType> featureTypes = exchange.getIn().getHeader(Textract2Constants.FEATURE_TYPES, List.class);
            if (featureTypes != null && !featureTypes.isEmpty()) {
                request.featureTypes(featureTypes);
            } else {
                request.featureTypes(FeatureType.TABLES, FeatureType.FORMS);
            }

            StartDocumentAnalysisResponse result;
            try {
                result = textractClient.startDocumentAnalysis(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Document Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startExpenseAnalysis(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartExpenseAnalysisRequest) {
                StartExpenseAnalysisResponse result;
                try {
                    result = textractClient.startExpenseAnalysis((StartExpenseAnalysisRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Expense Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartExpenseAnalysisRequest.Builder request = StartExpenseAnalysisRequest.builder();
            DocumentLocation documentLocation = createDocumentLocationFromExchange(exchange);
            request.documentLocation(documentLocation);

            StartExpenseAnalysisResponse result;
            try {
                result = textractClient.startExpenseAnalysis(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Expense Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getDocumentTextDetection(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetDocumentTextDetectionRequest) {
                GetDocumentTextDetectionResponse result;
                try {
                    result = textractClient.getDocumentTextDetection((GetDocumentTextDetectionRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Document Text Detection command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetDocumentTextDetectionRequest.Builder request = GetDocumentTextDetectionRequest.builder();

            String jobId = exchange.getIn().getHeader(Textract2Constants.JOB_ID, String.class);
            if (ObjectHelper.isEmpty(jobId)) {
                jobId = exchange.getIn().getBody(String.class);
            }
            if (ObjectHelper.isEmpty(jobId)) {
                throw new IllegalArgumentException("Job ID must be specified in header or body");
            }
            request.jobId(jobId);

            Integer maxResults = exchange.getIn().getHeader(Textract2Constants.MAX_RESULTS, Integer.class);
            if (ObjectHelper.isNotEmpty(maxResults)) {
                request.maxResults(maxResults);
            }

            String nextToken = exchange.getIn().getHeader(Textract2Constants.NEXT_TOKEN, String.class);
            if (ObjectHelper.isNotEmpty(nextToken)) {
                request.nextToken(nextToken);
            }

            GetDocumentTextDetectionResponse result;
            try {
                result = textractClient.getDocumentTextDetection(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Document Text Detection command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getDocumentAnalysis(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetDocumentAnalysisRequest) {
                GetDocumentAnalysisResponse result;
                try {
                    result = textractClient.getDocumentAnalysis((GetDocumentAnalysisRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Document Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetDocumentAnalysisRequest.Builder request = GetDocumentAnalysisRequest.builder();

            String jobId = exchange.getIn().getHeader(Textract2Constants.JOB_ID, String.class);
            if (ObjectHelper.isEmpty(jobId)) {
                jobId = exchange.getIn().getBody(String.class);
            }
            if (ObjectHelper.isEmpty(jobId)) {
                throw new IllegalArgumentException("Job ID must be specified in header or body");
            }
            request.jobId(jobId);

            Integer maxResults = exchange.getIn().getHeader(Textract2Constants.MAX_RESULTS, Integer.class);
            if (ObjectHelper.isNotEmpty(maxResults)) {
                request.maxResults(maxResults);
            }

            String nextToken = exchange.getIn().getHeader(Textract2Constants.NEXT_TOKEN, String.class);
            if (ObjectHelper.isNotEmpty(nextToken)) {
                request.nextToken(nextToken);
            }

            GetDocumentAnalysisResponse result;
            try {
                result = textractClient.getDocumentAnalysis(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Document Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getExpenseAnalysis(TextractClient textractClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetExpenseAnalysisRequest) {
                GetExpenseAnalysisResponse result;
                try {
                    result = textractClient.getExpenseAnalysis((GetExpenseAnalysisRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Expense Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetExpenseAnalysisRequest.Builder request = GetExpenseAnalysisRequest.builder();

            String jobId = exchange.getIn().getHeader(Textract2Constants.JOB_ID, String.class);
            if (ObjectHelper.isEmpty(jobId)) {
                jobId = exchange.getIn().getBody(String.class);
            }
            if (ObjectHelper.isEmpty(jobId)) {
                throw new IllegalArgumentException("Job ID must be specified in header or body");
            }
            request.jobId(jobId);

            Integer maxResults = exchange.getIn().getHeader(Textract2Constants.MAX_RESULTS, Integer.class);
            if (ObjectHelper.isNotEmpty(maxResults)) {
                request.maxResults(maxResults);
            }

            String nextToken = exchange.getIn().getHeader(Textract2Constants.NEXT_TOKEN, String.class);
            if (ObjectHelper.isNotEmpty(nextToken)) {
                request.nextToken(nextToken);
            }

            GetExpenseAnalysisResponse result;
            try {
                result = textractClient.getExpenseAnalysis(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Expense Analysis command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private Document createDocumentFromExchange(Exchange exchange) throws InvalidPayloadException {
        // Check if we have S3 location in configuration or headers
        String s3Bucket = getConfiguration().getS3Bucket();
        String s3Object = getConfiguration().getS3Object();

        if (ObjectHelper.isEmpty(s3Bucket)) {
            s3Bucket = exchange.getIn().getHeader(Textract2Constants.S3_BUCKET, String.class);
        }
        if (ObjectHelper.isEmpty(s3Object)) {
            s3Object = exchange.getIn().getHeader(Textract2Constants.S3_OBJECT, String.class);
        }

        if (ObjectHelper.isNotEmpty(s3Bucket) && ObjectHelper.isNotEmpty(s3Object)) {
            S3Object.Builder s3ObjectBuilder = S3Object.builder()
                    .bucket(s3Bucket)
                    .name(s3Object);

            String s3ObjectVersion = getConfiguration().getS3ObjectVersion();
            if (ObjectHelper.isEmpty(s3ObjectVersion)) {
                s3ObjectVersion = exchange.getIn().getHeader(Textract2Constants.S3_OBJECT_VERSION, String.class);
            }
            if (ObjectHelper.isNotEmpty(s3ObjectVersion)) {
                s3ObjectBuilder.version(s3ObjectVersion);
            }

            return Document.builder()
                    .s3Object(s3ObjectBuilder.build())
                    .build();
        } else {
            // Use bytes from message body
            byte[] documentBytes = exchange.getIn().getMandatoryBody(byte[].class);
            return Document.builder()
                    .bytes(SdkBytes.fromByteArray(documentBytes))
                    .build();
        }
    }

    private DocumentLocation createDocumentLocationFromExchange(Exchange exchange) {
        String s3Bucket = getConfiguration().getS3Bucket();
        String s3Object = getConfiguration().getS3Object();

        if (ObjectHelper.isEmpty(s3Bucket)) {
            s3Bucket = exchange.getIn().getHeader(Textract2Constants.S3_BUCKET, String.class);
        }
        if (ObjectHelper.isEmpty(s3Object)) {
            s3Object = exchange.getIn().getHeader(Textract2Constants.S3_OBJECT, String.class);
        }

        if (ObjectHelper.isEmpty(s3Bucket) || ObjectHelper.isEmpty(s3Object)) {
            throw new IllegalArgumentException("S3 bucket and object must be specified for async operations");
        }

        S3Object.Builder s3ObjectBuilder = S3Object.builder()
                .bucket(s3Bucket)
                .name(s3Object);

        String s3ObjectVersion = getConfiguration().getS3ObjectVersion();
        if (ObjectHelper.isEmpty(s3ObjectVersion)) {
            s3ObjectVersion = exchange.getIn().getHeader(Textract2Constants.S3_OBJECT_VERSION, String.class);
        }
        if (ObjectHelper.isNotEmpty(s3ObjectVersion)) {
            s3ObjectBuilder.version(s3ObjectVersion);
        }

        return DocumentLocation.builder()
                .s3Object(s3ObjectBuilder.build())
                .build();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Textract2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }
}
