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
package org.apache.camel.component.aws2.transcribe;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

public class Transcribe2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(Transcribe2Producer.class);

    private transient String transcribeProducerToString;

    public Transcribe2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (getConfiguration().getOperation()) {

            case startTranscriptionJob:
                startTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case getTranscriptionJob:
                getTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listTranscriptionJobs:
                listTranscriptionJobs(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteTranscriptionJob:
                deleteTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case createVocabulary:
                createVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case getVocabulary:
                getVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listVocabularies:
                listVocabularies(getEndpoint().getTranscribeClient(), exchange);
                break;
            case updateVocabulary:
                updateVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteVocabulary:
                deleteVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case createVocabularyFilter:
                createVocabularyFilter(getEndpoint().getTranscribeClient(), exchange);
                break;
            case getVocabularyFilter:
                getVocabularyFilter(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listVocabularyFilters:
                listVocabularyFilters(getEndpoint().getTranscribeClient(), exchange);
                break;
            case updateVocabularyFilter:
                updateVocabularyFilter(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteVocabularyFilter:
                deleteVocabularyFilter(getEndpoint().getTranscribeClient(), exchange);
                break;
            case createLanguageModel:
                createLanguageModel(getEndpoint().getTranscribeClient(), exchange);
                break;
            case describeLanguageModel:
                describeLanguageModel(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listLanguageModels:
                listLanguageModels(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteLanguageModel:
                deleteLanguageModel(getEndpoint().getTranscribeClient(), exchange);
                break;
            case createMedicalVocabulary:
                createMedicalVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case getMedicalVocabulary:
                getMedicalVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listMedicalVocabularies:
                listMedicalVocabularies(getEndpoint().getTranscribeClient(), exchange);
                break;
            case updateMedicalVocabulary:
                updateMedicalVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteMedicalVocabulary:
                deleteMedicalVocabulary(getEndpoint().getTranscribeClient(), exchange);
                break;
            case startMedicalTranscriptionJob:
                startMedicalTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case getMedicalTranscriptionJob:
                getMedicalTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listMedicalTranscriptionJobs:
                listMedicalTranscriptionJobs(getEndpoint().getTranscribeClient(), exchange);
                break;
            case deleteMedicalTranscriptionJob:
                deleteMedicalTranscriptionJob(getEndpoint().getTranscribeClient(), exchange);
                break;
            case tagResource:
                tagResource(getEndpoint().getTranscribeClient(), exchange);
                break;
            case untagResource:
                untagResource(getEndpoint().getTranscribeClient(), exchange);
                break;
            case listTagsForResource:
                listTagsForResource(getEndpoint().getTranscribeClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private void startTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartTranscriptionJobRequest req) {
                StartTranscriptionJobResponse result;
                try {
                    result = transcribeClient.startTranscriptionJob(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartTranscriptionJobRequest.Builder builder = StartTranscriptionJobRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME))) {
                String jobName = exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, String.class);
                builder.transcriptionJobName(jobName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE))) {
                String languageCode = exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE, String.class);
                builder.languageCode(languageCode);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.MEDIA_FORMAT))) {
                String mediaFormat = exchange.getIn().getHeader(Transcribe2Constants.MEDIA_FORMAT, String.class);
                builder.mediaFormat(mediaFormat);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.MEDIA_URI))) {
                String mediaUri = exchange.getIn().getHeader(Transcribe2Constants.MEDIA_URI, String.class);
                builder.media(Media.builder().mediaFileUri(mediaUri).build());
            }
            StartTranscriptionJobResponse result;
            try {
                result = transcribeClient.startTranscriptionJob(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetTranscriptionJobRequest req) {
                GetTranscriptionJobResponse result;
                try {
                    result = transcribeClient.getTranscriptionJob(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetTranscriptionJobRequest.Builder builder = GetTranscriptionJobRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME))) {
                String jobName = exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, String.class);
                builder.transcriptionJobName(jobName);
            }
            GetTranscriptionJobResponse result;
            try {
                result = transcribeClient.getTranscriptionJob(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listTranscriptionJobs(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListTranscriptionJobsRequest req) {
                ListTranscriptionJobsResponse result;
                try {
                    result = transcribeClient.listTranscriptionJobs(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Transcription Jobs command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListTranscriptionJobsRequest.Builder builder = ListTranscriptionJobsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.JOB_NAME_CONTAINS))) {
                String jobNameContains = exchange.getIn().getHeader(Transcribe2Constants.JOB_NAME_CONTAINS, String.class);
                builder.jobNameContains(jobNameContains);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.STATUS))) {
                String status = exchange.getIn().getHeader(Transcribe2Constants.STATUS, String.class);
                builder.status(status);
            }
            ListTranscriptionJobsResponse result;
            try {
                result = transcribeClient.listTranscriptionJobs(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Transcription Jobs command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteTranscriptionJobRequest req) {
                try {
                    transcribeClient.deleteTranscriptionJob(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            }
        } else {
            DeleteTranscriptionJobRequest.Builder builder = DeleteTranscriptionJobRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME))) {
                String jobName = exchange.getIn().getHeader(Transcribe2Constants.TRANSCRIPTION_JOB_NAME, String.class);
                builder.transcriptionJobName(jobName);
            }
            try {
                transcribeClient.deleteTranscriptionJob(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Transcription Job command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
    }

    // Additional methods for vocabulary operations...
    private void createVocabulary(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateVocabularyRequest req) {
                CreateVocabularyResponse result;
                try {
                    result = transcribeClient.createVocabulary(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateVocabularyRequest.Builder builder = CreateVocabularyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME))) {
                String vocabularyName = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME, String.class);
                builder.vocabularyName(vocabularyName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE))) {
                String languageCode = exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE, String.class);
                builder.languageCode(languageCode);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_PHRASES))) {
                String phrases = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_PHRASES, String.class);
                builder.phrases(phrases);
            }
            CreateVocabularyResponse result;
            try {
                result = transcribeClient.createVocabulary(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getVocabulary(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetVocabularyRequest req) {
                GetVocabularyResponse result;
                try {
                    result = transcribeClient.getVocabulary(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Get Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetVocabularyRequest.Builder builder = GetVocabularyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME))) {
                String vocabularyName = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME, String.class);
                builder.vocabularyName(vocabularyName);
            }
            GetVocabularyResponse result;
            try {
                result = transcribeClient.getVocabulary(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Get Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listVocabularies(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListVocabulariesRequest req) {
                ListVocabulariesResponse result;
                try {
                    result = transcribeClient.listVocabularies(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Vocabularies command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListVocabulariesRequest.Builder builder = ListVocabulariesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.STATUS))) {
                String status = exchange.getIn().getHeader(Transcribe2Constants.STATUS, String.class);
                builder.stateEquals(status);
            }
            ListVocabulariesResponse result;
            try {
                result = transcribeClient.listVocabularies(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Vocabularies command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateVocabulary(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateVocabularyRequest req) {
                UpdateVocabularyResponse result;
                try {
                    result = transcribeClient.updateVocabulary(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateVocabularyRequest.Builder builder = UpdateVocabularyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME))) {
                String vocabularyName = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME, String.class);
                builder.vocabularyName(vocabularyName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE))) {
                String languageCode = exchange.getIn().getHeader(Transcribe2Constants.LANGUAGE_CODE, String.class);
                builder.languageCode(languageCode);
            }
            UpdateVocabularyResponse result;
            try {
                result = transcribeClient.updateVocabulary(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteVocabulary(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteVocabularyRequest req) {
                try {
                    transcribeClient.deleteVocabulary(req);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            }
        } else {
            DeleteVocabularyRequest.Builder builder = DeleteVocabularyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME))) {
                String vocabularyName = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_NAME, String.class);
                builder.vocabularyName(vocabularyName);
            }
            try {
                transcribeClient.deleteVocabulary(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Vocabulary command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
    }

    private void createVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateVocabularyFilterRequest req) {
                setResponse(exchange, execute("Create Vocabulary Filter", () -> transcribeClient.createVocabularyFilter(req)));
            }
        } else {
            CreateVocabularyFilterRequest.Builder builder = CreateVocabularyFilterRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILTER_NAME, builder::vocabularyFilterName);
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_CODE, builder::languageCode);
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILE_URI, builder::vocabularyFilterFileUri);
            applyIfPresent(exchange, Transcribe2Constants.DATA_ACCESS_ROLE_ARN, builder::dataAccessRoleArn);
            List<String> words = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_PHRASES, List.class);
            if (ObjectHelper.isNotEmpty(words)) {
                builder.words(words);
            }
            setResponse(exchange,
                    execute("Create Vocabulary Filter", () -> transcribeClient.createVocabularyFilter(builder.build())));
        }
    }

    private void getVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetVocabularyFilterRequest req) {
                setResponse(exchange, execute("Get Vocabulary Filter", () -> transcribeClient.getVocabularyFilter(req)));
            }
        } else {
            GetVocabularyFilterRequest.Builder builder = GetVocabularyFilterRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILTER_NAME, builder::vocabularyFilterName);
            setResponse(exchange,
                    execute("Get Vocabulary Filter", () -> transcribeClient.getVocabularyFilter(builder.build())));
        }
    }

    private void listVocabularyFilters(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListVocabularyFiltersRequest req) {
                setResponse(exchange, execute("List Vocabulary Filters", () -> transcribeClient.listVocabularyFilters(req)));
            }
        } else {
            ListVocabularyFiltersRequest.Builder builder = ListVocabularyFiltersRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.JOB_NAME_CONTAINS, builder::nameContains);
            applyIfPresent(exchange, Transcribe2Constants.NEXT_TOKEN, builder::nextToken);
            applyMaxResults(exchange, builder::maxResults);
            setResponse(exchange,
                    execute("List Vocabulary Filters", () -> transcribeClient.listVocabularyFilters(builder.build())));
        }
    }

    private void updateVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateVocabularyFilterRequest req) {
                setResponse(exchange, execute("Update Vocabulary Filter", () -> transcribeClient.updateVocabularyFilter(req)));
            }
        } else {
            UpdateVocabularyFilterRequest.Builder builder = UpdateVocabularyFilterRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILTER_NAME, builder::vocabularyFilterName);
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILE_URI, builder::vocabularyFilterFileUri);
            applyIfPresent(exchange, Transcribe2Constants.DATA_ACCESS_ROLE_ARN, builder::dataAccessRoleArn);
            List<String> words = exchange.getIn().getHeader(Transcribe2Constants.VOCABULARY_PHRASES, List.class);
            if (ObjectHelper.isNotEmpty(words)) {
                builder.words(words);
            }
            setResponse(exchange,
                    execute("Update Vocabulary Filter", () -> transcribeClient.updateVocabularyFilter(builder.build())));
        }
    }

    private void deleteVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteVocabularyFilterRequest req) {
                setResponse(exchange, execute("Delete Vocabulary Filter", () -> transcribeClient.deleteVocabularyFilter(req)));
            }
        } else {
            DeleteVocabularyFilterRequest.Builder builder = DeleteVocabularyFilterRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILTER_NAME, builder::vocabularyFilterName);
            setResponse(exchange,
                    execute("Delete Vocabulary Filter", () -> transcribeClient.deleteVocabularyFilter(builder.build())));
        }
    }

    private void createLanguageModel(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateLanguageModelRequest req) {
                setResponse(exchange, execute("Create Language Model", () -> transcribeClient.createLanguageModel(req)));
            }
        } else {
            CreateLanguageModelRequest.Builder builder = CreateLanguageModelRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_MODEL_NAME, builder::modelName);
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_CODE, builder::languageCode);
            applyIfPresent(exchange, Transcribe2Constants.BASE_MODEL_NAME, builder::baseModelName);
            String s3Uri = exchange.getIn().getHeader(Transcribe2Constants.INPUT_DATA_S3_URI, String.class);
            String roleArn = exchange.getIn().getHeader(Transcribe2Constants.DATA_ACCESS_ROLE_ARN, String.class);
            if (ObjectHelper.isNotEmpty(s3Uri) || ObjectHelper.isNotEmpty(roleArn)) {
                builder.inputDataConfig(InputDataConfig.builder().s3Uri(s3Uri).dataAccessRoleArn(roleArn).build());
            }
            setResponse(exchange,
                    execute("Create Language Model", () -> transcribeClient.createLanguageModel(builder.build())));
        }
    }

    private void describeLanguageModel(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeLanguageModelRequest req) {
                setResponse(exchange, execute("Describe Language Model", () -> transcribeClient.describeLanguageModel(req)));
            }
        } else {
            DescribeLanguageModelRequest.Builder builder = DescribeLanguageModelRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_MODEL_NAME, builder::modelName);
            setResponse(exchange,
                    execute("Describe Language Model", () -> transcribeClient.describeLanguageModel(builder.build())));
        }
    }

    private void listLanguageModels(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListLanguageModelsRequest req) {
                setResponse(exchange, execute("List Language Models", () -> transcribeClient.listLanguageModels(req)));
            }
        } else {
            ListLanguageModelsRequest.Builder builder = ListLanguageModelsRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.JOB_NAME_CONTAINS, builder::nameContains);
            applyIfPresent(exchange, Transcribe2Constants.STATUS, builder::statusEquals);
            applyIfPresent(exchange, Transcribe2Constants.NEXT_TOKEN, builder::nextToken);
            applyMaxResults(exchange, builder::maxResults);
            setResponse(exchange, execute("List Language Models", () -> transcribeClient.listLanguageModels(builder.build())));
        }
    }

    private void deleteLanguageModel(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteLanguageModelRequest req) {
                setResponse(exchange, execute("Delete Language Model", () -> transcribeClient.deleteLanguageModel(req)));
            }
        } else {
            DeleteLanguageModelRequest.Builder builder = DeleteLanguageModelRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_MODEL_NAME, builder::modelName);
            setResponse(exchange,
                    execute("Delete Language Model", () -> transcribeClient.deleteLanguageModel(builder.build())));
        }
    }

    private void createMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateMedicalVocabularyRequest req) {
                setResponse(exchange,
                        execute("Create Medical Vocabulary", () -> transcribeClient.createMedicalVocabulary(req)));
            }
        } else {
            CreateMedicalVocabularyRequest.Builder builder = CreateMedicalVocabularyRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_NAME, builder::vocabularyName);
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_CODE, builder::languageCode);
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILE_URI, builder::vocabularyFileUri);
            setResponse(exchange,
                    execute("Create Medical Vocabulary", () -> transcribeClient.createMedicalVocabulary(builder.build())));
        }
    }

    private void getMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetMedicalVocabularyRequest req) {
                setResponse(exchange, execute("Get Medical Vocabulary", () -> transcribeClient.getMedicalVocabulary(req)));
            }
        } else {
            GetMedicalVocabularyRequest.Builder builder = GetMedicalVocabularyRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_NAME, builder::vocabularyName);
            setResponse(exchange,
                    execute("Get Medical Vocabulary", () -> transcribeClient.getMedicalVocabulary(builder.build())));
        }
    }

    private void listMedicalVocabularies(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListMedicalVocabulariesRequest req) {
                setResponse(exchange,
                        execute("List Medical Vocabularies", () -> transcribeClient.listMedicalVocabularies(req)));
            }
        } else {
            ListMedicalVocabulariesRequest.Builder builder = ListMedicalVocabulariesRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.JOB_NAME_CONTAINS, builder::nameContains);
            applyIfPresent(exchange, Transcribe2Constants.STATUS, builder::stateEquals);
            applyIfPresent(exchange, Transcribe2Constants.NEXT_TOKEN, builder::nextToken);
            applyMaxResults(exchange, builder::maxResults);
            setResponse(exchange,
                    execute("List Medical Vocabularies", () -> transcribeClient.listMedicalVocabularies(builder.build())));
        }
    }

    private void updateMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateMedicalVocabularyRequest req) {
                setResponse(exchange,
                        execute("Update Medical Vocabulary", () -> transcribeClient.updateMedicalVocabulary(req)));
            }
        } else {
            UpdateMedicalVocabularyRequest.Builder builder = UpdateMedicalVocabularyRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_NAME, builder::vocabularyName);
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_CODE, builder::languageCode);
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_FILE_URI, builder::vocabularyFileUri);
            setResponse(exchange,
                    execute("Update Medical Vocabulary", () -> transcribeClient.updateMedicalVocabulary(builder.build())));
        }
    }

    private void deleteMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteMedicalVocabularyRequest req) {
                setResponse(exchange,
                        execute("Delete Medical Vocabulary", () -> transcribeClient.deleteMedicalVocabulary(req)));
            }
        } else {
            DeleteMedicalVocabularyRequest.Builder builder = DeleteMedicalVocabularyRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.VOCABULARY_NAME, builder::vocabularyName);
            setResponse(exchange,
                    execute("Delete Medical Vocabulary", () -> transcribeClient.deleteMedicalVocabulary(builder.build())));
        }
    }

    private void startMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartMedicalTranscriptionJobRequest req) {
                setResponse(exchange,
                        execute("Start Medical Transcription Job",
                                () -> transcribeClient.startMedicalTranscriptionJob(req)));
            }
        } else {
            StartMedicalTranscriptionJobRequest.Builder builder = StartMedicalTranscriptionJobRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.MEDICAL_TRANSCRIPTION_JOB_NAME,
                    builder::medicalTranscriptionJobName);
            applyIfPresent(exchange, Transcribe2Constants.LANGUAGE_CODE, builder::languageCode);
            applyIfPresent(exchange, Transcribe2Constants.MEDIA_FORMAT, builder::mediaFormat);
            applyIfPresent(exchange, Transcribe2Constants.OUTPUT_BUCKET_NAME, builder::outputBucketName);
            applyIfPresent(exchange, Transcribe2Constants.SPECIALTY, builder::specialty);
            applyIfPresent(exchange, Transcribe2Constants.TYPE, builder::type);
            String mediaUri = exchange.getIn().getHeader(Transcribe2Constants.MEDIA_URI, String.class);
            if (ObjectHelper.isNotEmpty(mediaUri)) {
                builder.media(Media.builder().mediaFileUri(mediaUri).build());
            }
            setResponse(exchange, execute("Start Medical Transcription Job",
                    () -> transcribeClient.startMedicalTranscriptionJob(builder.build())));
        }
    }

    private void getMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetMedicalTranscriptionJobRequest req) {
                setResponse(exchange,
                        execute("Get Medical Transcription Job", () -> transcribeClient.getMedicalTranscriptionJob(req)));
            }
        } else {
            GetMedicalTranscriptionJobRequest.Builder builder = GetMedicalTranscriptionJobRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.MEDICAL_TRANSCRIPTION_JOB_NAME,
                    builder::medicalTranscriptionJobName);
            setResponse(exchange, execute("Get Medical Transcription Job",
                    () -> transcribeClient.getMedicalTranscriptionJob(builder.build())));
        }
    }

    private void listMedicalTranscriptionJobs(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListMedicalTranscriptionJobsRequest req) {
                setResponse(exchange,
                        execute("List Medical Transcription Jobs", () -> transcribeClient.listMedicalTranscriptionJobs(req)));
            }
        } else {
            ListMedicalTranscriptionJobsRequest.Builder builder = ListMedicalTranscriptionJobsRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.JOB_NAME_CONTAINS, builder::jobNameContains);
            applyIfPresent(exchange, Transcribe2Constants.STATUS, builder::status);
            applyIfPresent(exchange, Transcribe2Constants.NEXT_TOKEN, builder::nextToken);
            applyMaxResults(exchange, builder::maxResults);
            setResponse(exchange, execute("List Medical Transcription Jobs",
                    () -> transcribeClient.listMedicalTranscriptionJobs(builder.build())));
        }
    }

    private void deleteMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange)
            throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteMedicalTranscriptionJobRequest req) {
                setResponse(exchange, execute("Delete Medical Transcription Job",
                        () -> transcribeClient.deleteMedicalTranscriptionJob(req)));
            }
        } else {
            DeleteMedicalTranscriptionJobRequest.Builder builder = DeleteMedicalTranscriptionJobRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.MEDICAL_TRANSCRIPTION_JOB_NAME,
                    builder::medicalTranscriptionJobName);
            setResponse(exchange, execute("Delete Medical Transcription Job",
                    () -> transcribeClient.deleteMedicalTranscriptionJob(builder.build())));
        }
    }

    private void tagResource(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof TagResourceRequest req) {
                setResponse(exchange, execute("Tag Resource", () -> transcribeClient.tagResource(req)));
            }
        } else {
            TagResourceRequest.Builder builder = TagResourceRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.RESOURCE_ARN, builder::resourceArn);
            Map<String, String> tags = exchange.getIn().getHeader(Transcribe2Constants.TAGS, Map.class);
            if (ObjectHelper.isNotEmpty(tags)) {
                builder.tags(tags.entrySet().stream()
                        .map(e -> Tag.builder().key(e.getKey()).value(e.getValue()).build())
                        .toList());
            }
            setResponse(exchange, execute("Tag Resource", () -> transcribeClient.tagResource(builder.build())));
        }
    }

    private void untagResource(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UntagResourceRequest req) {
                setResponse(exchange, execute("Untag Resource", () -> transcribeClient.untagResource(req)));
            }
        } else {
            UntagResourceRequest.Builder builder = UntagResourceRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.RESOURCE_ARN, builder::resourceArn);
            List<String> tagKeys = exchange.getIn().getHeader(Transcribe2Constants.TAG_KEYS, List.class);
            if (ObjectHelper.isNotEmpty(tagKeys)) {
                builder.tagKeys(tagKeys);
            }
            setResponse(exchange, execute("Untag Resource", () -> transcribeClient.untagResource(builder.build())));
        }
    }

    private void listTagsForResource(TranscribeClient transcribeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListTagsForResourceRequest req) {
                setResponse(exchange, execute("List Tags For Resource", () -> transcribeClient.listTagsForResource(req)));
            }
        } else {
            ListTagsForResourceRequest.Builder builder = ListTagsForResourceRequest.builder();
            applyIfPresent(exchange, Transcribe2Constants.RESOURCE_ARN, builder::resourceArn);
            setResponse(exchange,
                    execute("List Tags For Resource", () -> transcribeClient.listTagsForResource(builder.build())));
        }
    }

    /**
     * Sets the given header value on the builder when the header is present.
     */
    private void applyIfPresent(Exchange exchange, String header, Consumer<String> setter) {
        String value = exchange.getIn().getHeader(header, String.class);
        if (ObjectHelper.isNotEmpty(value)) {
            setter.accept(value);
        }
    }

    private void applyMaxResults(Exchange exchange, Consumer<Integer> setter) {
        Integer maxResults = exchange.getIn().getHeader(Transcribe2Constants.MAX_RESULTS, Integer.class);
        if (ObjectHelper.isNotEmpty(maxResults)) {
            setter.accept(maxResults);
        }
    }

    /**
     * Runs an AWS Transcribe call, logging and rethrowing the service error code like the other operations.
     */
    private <T> T execute(String operationName, Supplier<T> call) {
        try {
            return call.get();
        } catch (AwsServiceException ase) {
            LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
            throw ase;
        }
    }

    private void setResponse(Exchange exchange, Object result) {
        getMessageForResponse(exchange).setBody(result);
    }

    protected Transcribe2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public Transcribe2Endpoint getEndpoint() {
        return (Transcribe2Endpoint) super.getEndpoint();
    }

    private Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    public String toString() {
        if (ObjectHelper.isEmpty(transcribeProducerToString)) {
            transcribeProducerToString = "Transcribe2Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return transcribeProducerToString;
    }
}
