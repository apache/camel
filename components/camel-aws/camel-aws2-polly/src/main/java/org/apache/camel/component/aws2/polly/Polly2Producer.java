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
package org.apache.camel.component.aws2.polly;

import java.util.Arrays;

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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.DeleteLexiconRequest;
import software.amazon.awssdk.services.polly.model.DeleteLexiconResponse;
import software.amazon.awssdk.services.polly.model.DescribeVoicesRequest;
import software.amazon.awssdk.services.polly.model.DescribeVoicesResponse;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.GetLexiconRequest;
import software.amazon.awssdk.services.polly.model.GetLexiconResponse;
import software.amazon.awssdk.services.polly.model.GetSpeechSynthesisTaskRequest;
import software.amazon.awssdk.services.polly.model.GetSpeechSynthesisTaskResponse;
import software.amazon.awssdk.services.polly.model.ListLexiconsRequest;
import software.amazon.awssdk.services.polly.model.ListLexiconsResponse;
import software.amazon.awssdk.services.polly.model.ListSpeechSynthesisTasksRequest;
import software.amazon.awssdk.services.polly.model.ListSpeechSynthesisTasksResponse;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.PutLexiconRequest;
import software.amazon.awssdk.services.polly.model.PutLexiconResponse;
import software.amazon.awssdk.services.polly.model.StartSpeechSynthesisTaskRequest;
import software.amazon.awssdk.services.polly.model.StartSpeechSynthesisTaskResponse;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;

/**
 * A Producer which sends messages to the Amazon Polly Service SDK v2 <a href="http://aws.amazon.com/polly/">AWS
 * Polly</a>
 */
public class Polly2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Polly2Producer.class);
    private transient String pollyProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public Polly2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case synthesizeSpeech:
                synthesizeSpeech(getEndpoint().getPollyClient(), exchange);
                break;
            case describeVoices:
                describeVoices(getEndpoint().getPollyClient(), exchange);
                break;
            case listLexicons:
                listLexicons(getEndpoint().getPollyClient(), exchange);
                break;
            case getLexicon:
                getLexicon(getEndpoint().getPollyClient(), exchange);
                break;
            case putLexicon:
                putLexicon(getEndpoint().getPollyClient(), exchange);
                break;
            case deleteLexicon:
                deleteLexicon(getEndpoint().getPollyClient(), exchange);
                break;
            case startSpeechSynthesisTask:
                startSpeechSynthesisTask(getEndpoint().getPollyClient(), exchange);
                break;
            case getSpeechSynthesisTask:
                getSpeechSynthesisTask(getEndpoint().getPollyClient(), exchange);
                break;
            case listSpeechSynthesisTasks:
                listSpeechSynthesisTasks(getEndpoint().getPollyClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private Polly2Operations determineOperation(Exchange exchange) {
        Polly2Operations operation = exchange.getIn().getHeader(Polly2Constants.OPERATION, Polly2Operations.class);
        if (ObjectHelper.isEmpty(operation)) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Polly2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ObjectHelper.isEmpty(pollyProducerToString)) {
            pollyProducerToString = "PollyProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return pollyProducerToString;
    }

    @Override
    public Polly2Endpoint getEndpoint() {
        return (Polly2Endpoint) super.getEndpoint();
    }

    private void synthesizeSpeech(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof SynthesizeSpeechRequest req) {
                ResponseInputStream<SynthesizeSpeechResponse> result;
                try {
                    result = pollyClient.synthesizeSpeech(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Synthesize Speech command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
                if (ObjectHelper.isNotEmpty(result.response().contentType())) {
                    message.setHeader(Polly2Constants.CONTENT_TYPE, result.response().contentType());
                }
                message.setHeader(Polly2Constants.REQUEST_CHARACTERS, result.response().requestCharacters());
            }
        } else {
            SynthesizeSpeechRequest.Builder request = SynthesizeSpeechRequest.builder();

            // Set voice ID - endpoint option first, then header
            VoiceId voiceId = getConfiguration().getVoiceId();
            if (ObjectHelper.isEmpty(voiceId)) {
                voiceId = exchange.getIn().getHeader(Polly2Constants.VOICE_ID, VoiceId.class);
            }
            if (ObjectHelper.isEmpty(voiceId)) {
                throw new IllegalArgumentException("Voice ID must be specified as endpoint option or header");
            }
            request.voiceId(voiceId);

            // Set output format - endpoint option first, then header
            OutputFormat outputFormat = getConfiguration().getOutputFormat();
            if (ObjectHelper.isEmpty(outputFormat)) {
                outputFormat = exchange.getIn().getHeader(Polly2Constants.OUTPUT_FORMAT, OutputFormat.class);
            }
            if (ObjectHelper.isNotEmpty(outputFormat)) {
                request.outputFormat(outputFormat);
            }

            // Set text type - endpoint option first, then header
            TextType textType = getConfiguration().getTextType();
            if (ObjectHelper.isEmpty(textType)) {
                textType = exchange.getIn().getHeader(Polly2Constants.TEXT_TYPE, TextType.class);
            }
            if (ObjectHelper.isNotEmpty(textType)) {
                request.textType(textType);
            }

            // Set engine - endpoint option first, then header
            Engine engine = getConfiguration().getEngine();
            if (ObjectHelper.isEmpty(engine)) {
                engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            }
            if (ObjectHelper.isNotEmpty(engine)) {
                request.engine(engine);
            }

            // Set sample rate - endpoint option first, then header
            String sampleRate = getConfiguration().getSampleRate();
            if (ObjectHelper.isEmpty(sampleRate)) {
                sampleRate = exchange.getIn().getHeader(Polly2Constants.SAMPLE_RATE, String.class);
            }
            if (ObjectHelper.isNotEmpty(sampleRate)) {
                request.sampleRate(sampleRate);
            }

            // Set language code - endpoint option first, then header
            String languageCode = getConfiguration().getLanguageCode();
            if (ObjectHelper.isEmpty(languageCode)) {
                languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            }
            if (ObjectHelper.isNotEmpty(languageCode)) {
                request.languageCode(languageCode);
            }

            // Set lexicon names - endpoint option first, then header
            String lexiconNames = getConfiguration().getLexiconNames();
            if (ObjectHelper.isEmpty(lexiconNames)) {
                lexiconNames = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAMES, String.class);
            }
            if (ObjectHelper.isNotEmpty(lexiconNames)) {
                request.lexiconNames(Arrays.asList(lexiconNames.split(",")));
            }

            // Set the text to synthesize
            request.text(exchange.getMessage().getBody(String.class));

            ResponseInputStream<SynthesizeSpeechResponse> result;
            try {
                result = pollyClient.synthesizeSpeech(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Synthesize Speech command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
            if (result.response().contentType() != null) {
                message.setHeader(Polly2Constants.CONTENT_TYPE, result.response().contentType());
            }
            message.setHeader(Polly2Constants.REQUEST_CHARACTERS, result.response().requestCharacters());
        }
    }

    private void describeVoices(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeVoicesRequest req) {
                DescribeVoicesResponse result;
                try {
                    result = pollyClient.describeVoices(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Voices command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.voices());
            }
        } else {
            DescribeVoicesRequest.Builder request = DescribeVoicesRequest.builder();

            // Set engine filter - endpoint option first, then header
            Engine engine = getConfiguration().getEngine();
            if (ObjectHelper.isEmpty(engine)) {
                engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            }
            if (ObjectHelper.isNotEmpty(engine)) {
                request.engine(engine);
            }

            // Set language code filter - endpoint option first, then header
            String languageCode = getConfiguration().getLanguageCode();
            if (ObjectHelper.isEmpty(languageCode)) {
                languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            }
            if (ObjectHelper.isNotEmpty(languageCode)) {
                request.languageCode(languageCode);
            }

            DescribeVoicesResponse result;
            try {
                result = pollyClient.describeVoices(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Voices command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.voices());
        }
    }

    private void listLexicons(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListLexiconsRequest req) {
                ListLexiconsResponse result;
                try {
                    result = pollyClient.listLexicons(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Lexicons command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.lexicons());
            }
        } else {
            ListLexiconsRequest request = ListLexiconsRequest.builder().build();
            ListLexiconsResponse result;
            try {
                result = pollyClient.listLexicons(request);
            } catch (AwsServiceException ase) {
                LOG.trace("List Lexicons command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.lexicons());
        }
    }

    private void getLexicon(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetLexiconRequest req) {
                GetLexiconResponse result;
                try {
                    result = pollyClient.getLexicon(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.lexicon());
            }
        } else {
            String lexiconName = getConfiguration().getLexiconName();
            if (ObjectHelper.isEmpty(lexiconName)) {
                lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
            }
            if (ObjectHelper.isEmpty(lexiconName)) {
                throw new IllegalArgumentException("Lexicon name must be specified");
            }
            GetLexiconRequest request = GetLexiconRequest.builder().name(lexiconName).build();
            GetLexiconResponse result;
            try {
                result = pollyClient.getLexicon(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Get Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.lexicon());
        }
    }

    private void putLexicon(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutLexiconRequest req) {
                PutLexiconResponse result;
                try {
                    result = pollyClient.putLexicon(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Put Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String lexiconName = getConfiguration().getLexiconName();
            if (ObjectHelper.isEmpty(lexiconName)) {
                lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
            }
            String lexiconContent = getConfiguration().getLexiconContent();
            if (ObjectHelper.isEmpty(lexiconContent)) {
                lexiconContent = exchange.getIn().getHeader(Polly2Constants.LEXICON_CONTENT, String.class);
            }
            if (ObjectHelper.isEmpty(lexiconContent)) {
                lexiconContent = exchange.getMessage().getBody(String.class);
            }
            if (ObjectHelper.isEmpty(lexiconName)) {
                throw new IllegalArgumentException("Lexicon name must be specified");
            }
            if (ObjectHelper.isEmpty(lexiconContent)) {
                throw new IllegalArgumentException("Lexicon content must be specified");
            }
            PutLexiconRequest request = PutLexiconRequest.builder()
                    .name(lexiconName)
                    .content(lexiconContent)
                    .build();
            PutLexiconResponse result;
            try {
                result = pollyClient.putLexicon(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Put Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteLexicon(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteLexiconRequest req) {
                DeleteLexiconResponse result;
                try {
                    result = pollyClient.deleteLexicon(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String lexiconName = getConfiguration().getLexiconName();
            if (ObjectHelper.isEmpty(lexiconName)) {
                lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
            }
            if (ObjectHelper.isEmpty(lexiconName)) {
                throw new IllegalArgumentException("Lexicon name must be specified");
            }
            DeleteLexiconRequest request = DeleteLexiconRequest.builder().name(lexiconName).build();
            DeleteLexiconResponse result;
            try {
                result = pollyClient.deleteLexicon(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void startSpeechSynthesisTask(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartSpeechSynthesisTaskRequest req) {
                StartSpeechSynthesisTaskResponse result;
                try {
                    result = pollyClient.startSpeechSynthesisTask(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Speech Synthesis Task command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.synthesisTask());
                message.setHeader(Polly2Constants.TASK_ID, result.synthesisTask().taskId());
            }
        } else {
            StartSpeechSynthesisTaskRequest.Builder request = StartSpeechSynthesisTaskRequest.builder();

            // Set voice ID - endpoint option first, then header
            VoiceId voiceId = getConfiguration().getVoiceId();
            if (ObjectHelper.isEmpty(voiceId)) {
                voiceId = exchange.getIn().getHeader(Polly2Constants.VOICE_ID, VoiceId.class);
            }
            if (ObjectHelper.isEmpty(voiceId)) {
                throw new IllegalArgumentException("Voice ID must be specified as endpoint option or header");
            }
            request.voiceId(voiceId);

            // Set output format - endpoint option first, then header
            OutputFormat outputFormat = getConfiguration().getOutputFormat();
            if (ObjectHelper.isEmpty(outputFormat)) {
                outputFormat = exchange.getIn().getHeader(Polly2Constants.OUTPUT_FORMAT, OutputFormat.class);
            }
            if (ObjectHelper.isNotEmpty(outputFormat)) {
                request.outputFormat(outputFormat);
            }

            // Set S3 bucket - endpoint option first, then header
            String s3Bucket = getConfiguration().getS3Bucket();
            if (ObjectHelper.isEmpty(s3Bucket)) {
                s3Bucket = exchange.getIn().getHeader(Polly2Constants.S3_BUCKET, String.class);
            }
            if (ObjectHelper.isEmpty(s3Bucket)) {
                throw new IllegalArgumentException("S3 bucket must be specified for async synthesis");
            }
            request.outputS3BucketName(s3Bucket);

            // Set S3 key prefix (optional) - endpoint option first, then header
            String s3KeyPrefix = getConfiguration().getS3KeyPrefix();
            if (ObjectHelper.isEmpty(s3KeyPrefix)) {
                s3KeyPrefix = exchange.getIn().getHeader(Polly2Constants.S3_KEY_PREFIX, String.class);
            }
            if (ObjectHelper.isNotEmpty(s3KeyPrefix)) {
                request.outputS3KeyPrefix(s3KeyPrefix);
            }

            // Set SNS topic ARN (optional) - endpoint option first, then header
            String snsTopicArn = getConfiguration().getSnsTopicArn();
            if (ObjectHelper.isEmpty(snsTopicArn)) {
                snsTopicArn = exchange.getIn().getHeader(Polly2Constants.SNS_TOPIC_ARN, String.class);
            }
            if (ObjectHelper.isNotEmpty(snsTopicArn)) {
                request.snsTopicArn(snsTopicArn);
            }

            // Set text type - endpoint option first, then header
            TextType textType = getConfiguration().getTextType();
            if (ObjectHelper.isEmpty(textType)) {
                textType = exchange.getIn().getHeader(Polly2Constants.TEXT_TYPE, TextType.class);
            }
            if (ObjectHelper.isNotEmpty(textType)) {
                request.textType(textType);
            }

            // Set engine - endpoint option first, then header
            Engine engine = getConfiguration().getEngine();
            if (ObjectHelper.isEmpty(engine)) {
                engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            }
            if (ObjectHelper.isNotEmpty(engine)) {
                request.engine(engine);
            }

            // Set sample rate - endpoint option first, then header
            String sampleRate = getConfiguration().getSampleRate();
            if (ObjectHelper.isEmpty(sampleRate)) {
                sampleRate = exchange.getIn().getHeader(Polly2Constants.SAMPLE_RATE, String.class);
            }
            if (ObjectHelper.isNotEmpty(sampleRate)) {
                request.sampleRate(sampleRate);
            }

            // Set language code - endpoint option first, then header
            String languageCode = getConfiguration().getLanguageCode();
            if (ObjectHelper.isEmpty(languageCode)) {
                languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            }
            if (ObjectHelper.isNotEmpty(languageCode)) {
                request.languageCode(languageCode);
            }

            // Set lexicon names - endpoint option first, then header
            String lexiconNames = getConfiguration().getLexiconNames();
            if (ObjectHelper.isEmpty(lexiconNames)) {
                lexiconNames = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAMES, String.class);
            }
            if (ObjectHelper.isNotEmpty(lexiconNames)) {
                request.lexiconNames(Arrays.asList(lexiconNames.split(",")));
            }

            // Set the text to synthesize
            request.text(exchange.getMessage().getBody(String.class));

            StartSpeechSynthesisTaskResponse result;
            try {
                result = pollyClient.startSpeechSynthesisTask(request.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Speech Synthesis Task command returned the error code {}",
                        ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.synthesisTask());
            message.setHeader(Polly2Constants.TASK_ID, result.synthesisTask().taskId());
        }
    }

    private void getSpeechSynthesisTask(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetSpeechSynthesisTaskRequest req) {
                GetSpeechSynthesisTaskResponse result;
                try {
                    result = pollyClient.getSpeechSynthesisTask(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Speech Synthesis Task command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.synthesisTask());
            }
        } else {
            String taskId = getConfiguration().getTaskId();
            if (ObjectHelper.isEmpty(taskId)) {
                taskId = exchange.getIn().getHeader(Polly2Constants.TASK_ID, String.class);
            }
            if (ObjectHelper.isEmpty(taskId)) {
                throw new IllegalArgumentException("Task ID must be specified");
            }
            GetSpeechSynthesisTaskRequest request = GetSpeechSynthesisTaskRequest.builder()
                    .taskId(taskId)
                    .build();
            GetSpeechSynthesisTaskResponse result;
            try {
                result = pollyClient.getSpeechSynthesisTask(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Get Speech Synthesis Task command returned the error code {}",
                        ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.synthesisTask());
        }
    }

    private void listSpeechSynthesisTasks(PollyClient pollyClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListSpeechSynthesisTasksRequest req) {
                ListSpeechSynthesisTasksResponse result;
                try {
                    result = pollyClient.listSpeechSynthesisTasks(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Speech Synthesis Tasks command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.synthesisTasks());
            }
        } else {
            ListSpeechSynthesisTasksRequest request = ListSpeechSynthesisTasksRequest.builder().build();
            ListSpeechSynthesisTasksResponse result;
            try {
                result = pollyClient.listSpeechSynthesisTasks(request);
            } catch (AwsServiceException ase) {
                LOG.trace("List Speech Synthesis Tasks command returned the error code {}",
                        ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result.synthesisTasks());
        }
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
            producerHealthCheck = new Polly2ProducerHealthCheck(getEndpoint(), id);
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
