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
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected Polly2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (pollyProducerToString == null) {
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
            if (payload instanceof SynthesizeSpeechRequest) {
                ResponseInputStream<SynthesizeSpeechResponse> result;
                try {
                    result = pollyClient.synthesizeSpeech((SynthesizeSpeechRequest) payload);
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
        } else {
            SynthesizeSpeechRequest.Builder request = SynthesizeSpeechRequest.builder();

            // Set voice ID
            VoiceId voiceId = exchange.getIn().getHeader(Polly2Constants.VOICE_ID, VoiceId.class);
            if (voiceId == null) {
                voiceId = getConfiguration().getVoiceId();
            }
            if (voiceId == null) {
                throw new IllegalArgumentException("Voice ID must be specified as header or endpoint option");
            }
            request.voiceId(voiceId);

            // Set output format
            OutputFormat outputFormat = exchange.getIn().getHeader(Polly2Constants.OUTPUT_FORMAT, OutputFormat.class);
            if (outputFormat == null) {
                outputFormat = getConfiguration().getOutputFormat();
            }
            request.outputFormat(outputFormat);

            // Set text type
            TextType textType = exchange.getIn().getHeader(Polly2Constants.TEXT_TYPE, TextType.class);
            if (textType == null) {
                textType = getConfiguration().getTextType();
            }
            request.textType(textType);

            // Set engine
            Engine engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            if (engine == null && getConfiguration().getEngine() != null) {
                engine = getConfiguration().getEngine();
            }
            if (engine != null) {
                request.engine(engine);
            }

            // Set sample rate
            String sampleRate = exchange.getIn().getHeader(Polly2Constants.SAMPLE_RATE, String.class);
            if (sampleRate == null) {
                sampleRate = getConfiguration().getSampleRate();
            }
            if (sampleRate != null) {
                request.sampleRate(sampleRate);
            }

            // Set language code
            String languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            if (languageCode == null) {
                languageCode = getConfiguration().getLanguageCode();
            }
            if (languageCode != null) {
                request.languageCode(languageCode);
            }

            // Set lexicon names
            String lexiconNames = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAMES, String.class);
            if (lexiconNames == null) {
                lexiconNames = getConfiguration().getLexiconNames();
            }
            if (lexiconNames != null) {
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
            if (payload instanceof DescribeVoicesRequest) {
                DescribeVoicesResponse result;
                try {
                    result = pollyClient.describeVoices((DescribeVoicesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Voices command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.voices());
            }
        } else {
            DescribeVoicesRequest.Builder request = DescribeVoicesRequest.builder();

            // Set engine filter
            Engine engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            if (engine == null && getConfiguration().getEngine() != null) {
                engine = getConfiguration().getEngine();
            }
            if (engine != null) {
                request.engine(engine);
            }

            // Set language code filter
            String languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            if (languageCode == null) {
                languageCode = getConfiguration().getLanguageCode();
            }
            if (languageCode != null) {
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
            if (payload instanceof ListLexiconsRequest) {
                ListLexiconsResponse result;
                try {
                    result = pollyClient.listLexicons((ListLexiconsRequest) payload);
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
            if (payload instanceof GetLexiconRequest) {
                GetLexiconResponse result;
                try {
                    result = pollyClient.getLexicon((GetLexiconRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.lexicon());
            }
        } else {
            String lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
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
            if (payload instanceof PutLexiconRequest) {
                PutLexiconResponse result;
                try {
                    result = pollyClient.putLexicon((PutLexiconRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Put Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
            String lexiconContent = exchange.getIn().getHeader(Polly2Constants.LEXICON_CONTENT, String.class);
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
            if (payload instanceof DeleteLexiconRequest) {
                DeleteLexiconResponse result;
                try {
                    result = pollyClient.deleteLexicon((DeleteLexiconRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Lexicon command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            String lexiconName = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAME, String.class);
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
            if (payload instanceof StartSpeechSynthesisTaskRequest) {
                StartSpeechSynthesisTaskResponse result;
                try {
                    result = pollyClient.startSpeechSynthesisTask((StartSpeechSynthesisTaskRequest) payload);
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

            // Set voice ID
            VoiceId voiceId = exchange.getIn().getHeader(Polly2Constants.VOICE_ID, VoiceId.class);
            if (voiceId == null) {
                voiceId = getConfiguration().getVoiceId();
            }
            if (voiceId == null) {
                throw new IllegalArgumentException("Voice ID must be specified as header or endpoint option");
            }
            request.voiceId(voiceId);

            // Set output format
            OutputFormat outputFormat = exchange.getIn().getHeader(Polly2Constants.OUTPUT_FORMAT, OutputFormat.class);
            if (outputFormat == null) {
                outputFormat = getConfiguration().getOutputFormat();
            }
            request.outputFormat(outputFormat);

            // Set S3 bucket
            String s3Bucket = exchange.getIn().getHeader(Polly2Constants.S3_BUCKET, String.class);
            if (ObjectHelper.isEmpty(s3Bucket)) {
                throw new IllegalArgumentException("S3 bucket must be specified for async synthesis");
            }
            request.outputS3BucketName(s3Bucket);

            // Set S3 key prefix (optional)
            String s3KeyPrefix = exchange.getIn().getHeader(Polly2Constants.S3_KEY_PREFIX, String.class);
            if (ObjectHelper.isNotEmpty(s3KeyPrefix)) {
                request.outputS3KeyPrefix(s3KeyPrefix);
            }

            // Set SNS topic ARN (optional)
            String snsTopicArn = exchange.getIn().getHeader(Polly2Constants.SNS_TOPIC_ARN, String.class);
            if (ObjectHelper.isNotEmpty(snsTopicArn)) {
                request.snsTopicArn(snsTopicArn);
            }

            // Set text type
            TextType textType = exchange.getIn().getHeader(Polly2Constants.TEXT_TYPE, TextType.class);
            if (textType == null) {
                textType = getConfiguration().getTextType();
            }
            request.textType(textType);

            // Set engine
            Engine engine = exchange.getIn().getHeader(Polly2Constants.ENGINE, Engine.class);
            if (engine == null && getConfiguration().getEngine() != null) {
                engine = getConfiguration().getEngine();
            }
            if (engine != null) {
                request.engine(engine);
            }

            // Set sample rate
            String sampleRate = exchange.getIn().getHeader(Polly2Constants.SAMPLE_RATE, String.class);
            if (sampleRate == null) {
                sampleRate = getConfiguration().getSampleRate();
            }
            if (sampleRate != null) {
                request.sampleRate(sampleRate);
            }

            // Set language code
            String languageCode = exchange.getIn().getHeader(Polly2Constants.LANGUAGE_CODE, String.class);
            if (languageCode == null) {
                languageCode = getConfiguration().getLanguageCode();
            }
            if (languageCode != null) {
                request.languageCode(languageCode);
            }

            // Set lexicon names
            String lexiconNames = exchange.getIn().getHeader(Polly2Constants.LEXICON_NAMES, String.class);
            if (lexiconNames == null) {
                lexiconNames = getConfiguration().getLexiconNames();
            }
            if (lexiconNames != null) {
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
            if (payload instanceof GetSpeechSynthesisTaskRequest) {
                GetSpeechSynthesisTaskResponse result;
                try {
                    result = pollyClient.getSpeechSynthesisTask((GetSpeechSynthesisTaskRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Speech Synthesis Task command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result.synthesisTask());
            }
        } else {
            String taskId = exchange.getIn().getHeader(Polly2Constants.TASK_ID, String.class);
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
            if (payload instanceof ListSpeechSynthesisTasksRequest) {
                ListSpeechSynthesisTasksResponse result;
                try {
                    result = pollyClient.listSpeechSynthesisTasks((ListSpeechSynthesisTasksRequest) payload);
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

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new Polly2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
