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

import java.time.Instant;

import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.TranscribeServiceClientConfiguration;
import software.amazon.awssdk.services.transcribe.model.*;

public class AmazonAWSTranscribeMock implements TranscribeClient {

    @Override
    public StartTranscriptionJobResponse startTranscriptionJob(StartTranscriptionJobRequest request) {
        TranscriptionJob job = TranscriptionJob.builder()
                .transcriptionJobName(request.transcriptionJobName())
                .transcriptionJobStatus(TranscriptionJobStatus.IN_PROGRESS)
                .languageCode(request.languageCode())
                .mediaFormat(request.mediaFormat())
                .media(request.media())
                .creationTime(Instant.now())
                .build();

        return StartTranscriptionJobResponse.builder().transcriptionJob(job).build();
    }

    @Override
    public GetTranscriptionJobResponse getTranscriptionJob(GetTranscriptionJobRequest request) {
        TranscriptionJob job = TranscriptionJob.builder()
                .transcriptionJobName(request.transcriptionJobName())
                .transcriptionJobStatus(TranscriptionJobStatus.COMPLETED)
                .languageCode(LanguageCode.EN_US)
                .mediaFormat(MediaFormat.MP3)
                .creationTime(Instant.now())
                .completionTime(Instant.now())
                .build();

        return GetTranscriptionJobResponse.builder().transcriptionJob(job).build();
    }

    @Override
    public ListTranscriptionJobsResponse listTranscriptionJobs(ListTranscriptionJobsRequest request) {
        TranscriptionJobSummary summary = TranscriptionJobSummary.builder()
                .transcriptionJobName("test-job")
                .transcriptionJobStatus(TranscriptionJobStatus.COMPLETED)
                .languageCode(LanguageCode.EN_US)
                .creationTime(Instant.now())
                .completionTime(Instant.now())
                .build();

        return ListTranscriptionJobsResponse.builder()
                .transcriptionJobSummaries(summary)
                .build();
    }

    @Override
    public DeleteTranscriptionJobResponse deleteTranscriptionJob(DeleteTranscriptionJobRequest request) {
        return DeleteTranscriptionJobResponse.builder().build();
    }

    @Override
    public CreateVocabularyResponse createVocabulary(CreateVocabularyRequest request) {
        return CreateVocabularyResponse.builder()
                .vocabularyName(request.vocabularyName())
                .languageCode(request.languageCode())
                .vocabularyState(VocabularyState.PENDING)
                .build();
    }

    @Override
    public GetVocabularyResponse getVocabulary(GetVocabularyRequest request) {
        return GetVocabularyResponse.builder()
                .vocabularyName(request.vocabularyName())
                .languageCode(LanguageCode.EN_US)
                .vocabularyState(VocabularyState.READY)
                .lastModifiedTime(Instant.now())
                .build();
    }

    @Override
    public ListVocabulariesResponse listVocabularies(ListVocabulariesRequest request) {
        VocabularyInfo vocab = VocabularyInfo.builder()
                .vocabularyName("test-vocabulary")
                .languageCode(LanguageCode.EN_US)
                .vocabularyState(VocabularyState.READY)
                .lastModifiedTime(Instant.now())
                .build();

        return ListVocabulariesResponse.builder().vocabularies(vocab).build();
    }

    @Override
    public UpdateVocabularyResponse updateVocabulary(UpdateVocabularyRequest request) {
        return UpdateVocabularyResponse.builder()
                .vocabularyName(request.vocabularyName())
                .languageCode(request.languageCode())
                .vocabularyState(VocabularyState.PENDING)
                .lastModifiedTime(Instant.now())
                .build();
    }

    @Override
    public DeleteVocabularyResponse deleteVocabulary(DeleteVocabularyRequest request) {
        return DeleteVocabularyResponse.builder().build();
    }

    @Override
    public TranscribeServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        return "transcribe";
    }

    @Override
    public void close() {
        // Mock implementation - no resources to close
    }
}
