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
            if (payload instanceof StartTranscriptionJobRequest) {
                StartTranscriptionJobResponse result;
                try {
                    result = transcribeClient.startTranscriptionJob((StartTranscriptionJobRequest) payload);
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
            if (payload instanceof GetTranscriptionJobRequest) {
                GetTranscriptionJobResponse result;
                try {
                    result = transcribeClient.getTranscriptionJob((GetTranscriptionJobRequest) payload);
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
            if (payload instanceof ListTranscriptionJobsRequest) {
                ListTranscriptionJobsResponse result;
                try {
                    result = transcribeClient.listTranscriptionJobs((ListTranscriptionJobsRequest) payload);
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
            if (payload instanceof DeleteTranscriptionJobRequest) {
                try {
                    transcribeClient.deleteTranscriptionJob((DeleteTranscriptionJobRequest) payload);
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
            if (payload instanceof CreateVocabularyRequest) {
                CreateVocabularyResponse result;
                try {
                    result = transcribeClient.createVocabulary((CreateVocabularyRequest) payload);
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
            if (payload instanceof GetVocabularyRequest) {
                GetVocabularyResponse result;
                try {
                    result = transcribeClient.getVocabulary((GetVocabularyRequest) payload);
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
            if (payload instanceof ListVocabulariesRequest) {
                ListVocabulariesResponse result;
                try {
                    result = transcribeClient.listVocabularies((ListVocabulariesRequest) payload);
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
            if (payload instanceof UpdateVocabularyRequest) {
                UpdateVocabularyResponse result;
                try {
                    result = transcribeClient.updateVocabulary((UpdateVocabularyRequest) payload);
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
            if (payload instanceof DeleteVocabularyRequest) {
                try {
                    transcribeClient.deleteVocabulary((DeleteVocabularyRequest) payload);
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

    private void createVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for create vocabulary filter
    }

    private void getVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for get vocabulary filter
    }

    private void listVocabularyFilters(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for list vocabulary filters
    }

    private void updateVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for update vocabulary filter
    }

    private void deleteVocabularyFilter(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for delete vocabulary filter
    }

    private void createLanguageModel(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for create language model
    }

    private void describeLanguageModel(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for describe language model
    }

    private void listLanguageModels(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for list language models
    }

    private void deleteLanguageModel(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for delete language model
    }

    private void createMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for create medical vocabulary
    }

    private void getMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for get medical vocabulary
    }

    private void listMedicalVocabularies(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for list medical vocabularies
    }

    private void updateMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for update medical vocabulary
    }

    private void deleteMedicalVocabulary(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for delete medical vocabulary
    }

    private void startMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for start medical transcription job
    }

    private void getMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for get medical transcription job
    }

    private void listMedicalTranscriptionJobs(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for list medical transcription jobs
    }

    private void deleteMedicalTranscriptionJob(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for delete medical transcription job
    }

    private void tagResource(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for tag resource
    }

    private void untagResource(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for untag resource
    }

    private void listTagsForResource(TranscribeClient transcribeClient, Exchange exchange) {
        // Implementation for list tags for resource
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
