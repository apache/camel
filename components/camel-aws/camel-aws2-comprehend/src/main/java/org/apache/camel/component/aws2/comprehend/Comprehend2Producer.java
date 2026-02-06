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
package org.apache.camel.component.aws2.comprehend;

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
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentRequest;
import software.amazon.awssdk.services.comprehend.model.ClassifyDocumentResponse;
import software.amazon.awssdk.services.comprehend.model.ContainsPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.ContainsPiiEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageRequest;
import software.amazon.awssdk.services.comprehend.model.DetectDominantLanguageResponse;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesRequest;
import software.amazon.awssdk.services.comprehend.model.DetectPiiEntitiesResponse;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSentimentResponse;
import software.amazon.awssdk.services.comprehend.model.DetectSyntaxRequest;
import software.amazon.awssdk.services.comprehend.model.DetectSyntaxResponse;
import software.amazon.awssdk.services.comprehend.model.DetectToxicContentRequest;
import software.amazon.awssdk.services.comprehend.model.DetectToxicContentResponse;
import software.amazon.awssdk.services.comprehend.model.DominantLanguage;
import software.amazon.awssdk.services.comprehend.model.TextSegment;

/**
 * A Producer which sends messages to the Amazon Comprehend Service SDK v2
 * <a href="http://aws.amazon.com/comprehend/">AWS Comprehend</a>
 */
public class Comprehend2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Comprehend2Producer.class);
    private transient String comprehendProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Comprehend2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case detectDominantLanguage:
                detectDominantLanguage(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectEntities:
                detectEntities(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectKeyPhrases:
                detectKeyPhrases(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectSentiment:
                detectSentiment(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectSyntax:
                detectSyntax(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectPiiEntities:
                detectPiiEntities(getEndpoint().getComprehendClient(), exchange);
                break;
            case detectToxicContent:
                detectToxicContent(getEndpoint().getComprehendClient(), exchange);
                break;
            case classifyDocument:
                classifyDocument(getEndpoint().getComprehendClient(), exchange);
                break;
            case containsPiiEntities:
                containsPiiEntities(getEndpoint().getComprehendClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Comprehend2Operations determineOperation(Exchange exchange) {
        Comprehend2Operations operation
                = exchange.getIn().getHeader(Comprehend2Constants.OPERATION, Comprehend2Operations.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Comprehend2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ObjectHelper.isEmpty(comprehendProducerToString)) {
            comprehendProducerToString = "ComprehendProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return comprehendProducerToString;
    }

    @Override
    public Comprehend2Endpoint getEndpoint() {
        return (Comprehend2Endpoint) super.getEndpoint();
    }

    private void detectDominantLanguage(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectDominantLanguageRequest req) {
                DetectDominantLanguageResponse result;
                try {
                    result = comprehendClient.detectDominantLanguage(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Dominant Language command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.languages());
                if (!result.languages().isEmpty()) {
                    DominantLanguage topLanguage = result.languages().get(0);
                    message.setHeader(Comprehend2Constants.DETECTED_LANGUAGE, topLanguage.languageCode());
                    message.setHeader(Comprehend2Constants.DETECTED_LANGUAGE_SCORE, topLanguage.score());
                }
            }
        } else {
            DetectDominantLanguageRequest.Builder request = DetectDominantLanguageRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            DetectDominantLanguageResponse result;
            try {
                result = comprehendClient.detectDominantLanguage(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Dominant Language command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.languages());
            if (!result.languages().isEmpty()) {
                DominantLanguage topLanguage = result.languages().get(0);
                message.setHeader(Comprehend2Constants.DETECTED_LANGUAGE, topLanguage.languageCode());
                message.setHeader(Comprehend2Constants.DETECTED_LANGUAGE_SCORE, topLanguage.score());
            }
        }
    }

    private void detectEntities(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectEntitiesRequest req) {
                DetectEntitiesResponse result;
                try {
                    result = comprehendClient.detectEntities(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.entities());
            }
        } else {
            DetectEntitiesRequest.Builder request = DetectEntitiesRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            DetectEntitiesResponse result;
            try {
                result = comprehendClient.detectEntities(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.entities());
        }
    }

    private void detectKeyPhrases(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectKeyPhrasesRequest req) {
                DetectKeyPhrasesResponse result;
                try {
                    result = comprehendClient.detectKeyPhrases(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Key Phrases command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.keyPhrases());
            }
        } else {
            DetectKeyPhrasesRequest.Builder request = DetectKeyPhrasesRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            DetectKeyPhrasesResponse result;
            try {
                result = comprehendClient.detectKeyPhrases(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Key Phrases command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.keyPhrases());
        }
    }

    private void detectSentiment(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectSentimentRequest req) {
                DetectSentimentResponse result;
                try {
                    result = comprehendClient.detectSentiment(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Sentiment command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
                message.setHeader(Comprehend2Constants.DETECTED_SENTIMENT, result.sentimentAsString());
                message.setHeader(Comprehend2Constants.DETECTED_SENTIMENT_SCORE, result.sentimentScore());
            }
        } else {
            DetectSentimentRequest.Builder request = DetectSentimentRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            DetectSentimentResponse result;
            try {
                result = comprehendClient.detectSentiment(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Sentiment command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            message.setHeader(Comprehend2Constants.DETECTED_SENTIMENT, result.sentimentAsString());
            message.setHeader(Comprehend2Constants.DETECTED_SENTIMENT_SCORE, result.sentimentScore());
        }
    }

    private void detectSyntax(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectSyntaxRequest req) {
                DetectSyntaxResponse result;
                try {
                    result = comprehendClient.detectSyntax(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Syntax command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.syntaxTokens());
            }
        } else {
            DetectSyntaxRequest.Builder request = DetectSyntaxRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            DetectSyntaxResponse result;
            try {
                result = comprehendClient.detectSyntax(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Syntax command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.syntaxTokens());
        }
    }

    private void detectPiiEntities(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectPiiEntitiesRequest req) {
                DetectPiiEntitiesResponse result;
                try {
                    result = comprehendClient.detectPiiEntities(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect PII Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.entities());
            }
        } else {
            DetectPiiEntitiesRequest.Builder request = DetectPiiEntitiesRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            DetectPiiEntitiesResponse result;
            try {
                result = comprehendClient.detectPiiEntities(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect PII Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.entities());
        }
    }

    private void detectToxicContent(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DetectToxicContentRequest req) {
                DetectToxicContentResponse result;
                try {
                    result = comprehendClient.detectToxicContent(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Detect Toxic Content command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.resultList());
            }
        } else {
            DetectToxicContentRequest.Builder request = DetectToxicContentRequest.builder();
            String text = exchange.getMessage().getBody(String.class);
            request.textSegments(List.of(TextSegment.builder().text(text).build()));
            request.languageCode(getLanguageCode(exchange));
            DetectToxicContentResponse result;
            try {
                result = comprehendClient.detectToxicContent(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Detect Toxic Content command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.resultList());
        }
    }

    private void classifyDocument(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ClassifyDocumentRequest req) {
                ClassifyDocumentResponse result;
                try {
                    result = comprehendClient.classifyDocument(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Classify Document command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ClassifyDocumentRequest.Builder request = ClassifyDocumentRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            String endpointArn = getEndpointArn(exchange);
            if (ObjectHelper.isEmpty(endpointArn)) {
                throw new IllegalArgumentException("endpointArn must be specified for classifyDocument operation");
            }
            request.endpointArn(endpointArn);
            ClassifyDocumentResponse result;
            try {
                result = comprehendClient.classifyDocument(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Classify Document command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void containsPiiEntities(ComprehendClient comprehendClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ContainsPiiEntitiesRequest req) {
                ContainsPiiEntitiesResponse result;
                try {
                    result = comprehendClient.containsPiiEntities(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Contains PII Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.labels());
            }
        } else {
            ContainsPiiEntitiesRequest.Builder request = ContainsPiiEntitiesRequest.builder();
            request.text(exchange.getMessage().getBody(String.class));
            request.languageCode(getLanguageCode(exchange));
            ContainsPiiEntitiesResponse result;
            try {
                result = comprehendClient.containsPiiEntities(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Contains PII Entities command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.labels());
        }
    }

    private String getLanguageCode(Exchange exchange) {
        String languageCode = exchange.getIn().getHeader(Comprehend2Constants.LANGUAGE_CODE, String.class);
        if (ObjectHelper.isEmpty(languageCode)) {
            languageCode = getConfiguration().getLanguageCode();
        }
        if (ObjectHelper.isEmpty(languageCode)) {
            throw new IllegalArgumentException("languageCode must be specified as a header or endpoint option");
        }
        return languageCode;
    }

    private String getEndpointArn(Exchange exchange) {
        String endpointArn = exchange.getIn().getHeader(Comprehend2Constants.ENDPOINT_ARN, String.class);
        if (ObjectHelper.isEmpty(endpointArn)) {
            endpointArn = getConfiguration().getEndpointArn();
        }
        return endpointArn;
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
            producerHealthCheck = new Comprehend2ProducerHealthCheck(getEndpoint(), id);
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
