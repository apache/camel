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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.CreateLanguageModelRequest;
import software.amazon.awssdk.services.transcribe.model.CreateLanguageModelResponse;
import software.amazon.awssdk.services.transcribe.model.CreateVocabularyFilterRequest;
import software.amazon.awssdk.services.transcribe.model.CreateVocabularyFilterResponse;
import software.amazon.awssdk.services.transcribe.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.transcribe.model.ListTagsForResourceResponse;
import software.amazon.awssdk.services.transcribe.model.ListVocabularyFiltersRequest;
import software.amazon.awssdk.services.transcribe.model.ListVocabularyFiltersResponse;
import software.amazon.awssdk.services.transcribe.model.StartMedicalTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.StartMedicalTranscriptionJobResponse;
import software.amazon.awssdk.services.transcribe.model.TagResourceRequest;
import software.amazon.awssdk.services.transcribe.model.TagResourceResponse;
import software.amazon.awssdk.services.transcribe.model.UntagResourceRequest;
import software.amazon.awssdk.services.transcribe.model.UntagResourceResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * These operations were previously declared but implemented as empty methods, so selecting them was a silent no-op.
 * Each test proves the operation now reaches the AWS client with the headers it was given.
 */
public class Transcribe2ProducerImplementedOperationsTest extends CamelTestSupport {

    @BindToRegistry("transcribeClient")
    private final TranscribeClient transcribeClient = mock(TranscribeClient.class);

    @Test
    public void createVocabularyFilterUsesNameLanguageAndWords() throws Exception {
        when(transcribeClient.createVocabularyFilter(any(CreateVocabularyFilterRequest.class)))
                .thenReturn(CreateVocabularyFilterResponse.builder().build());

        template.send("direct:createVocabularyFilter", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_FILTER_NAME, "my-filter");
            exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
            exchange.getIn().setHeader(Transcribe2Constants.VOCABULARY_PHRASES, List.of("foo", "bar"));
        });

        ArgumentCaptor<CreateVocabularyFilterRequest> captor
                = ArgumentCaptor.forClass(CreateVocabularyFilterRequest.class);
        verify(transcribeClient).createVocabularyFilter(captor.capture());
        assertEquals("my-filter", captor.getValue().vocabularyFilterName());
        assertEquals("en-US", captor.getValue().languageCodeAsString());
        assertEquals(List.of("foo", "bar"), captor.getValue().words());
    }

    @Test
    public void listVocabularyFiltersUsesNameContainsAndMaxResults() throws Exception {
        when(transcribeClient.listVocabularyFilters(any(ListVocabularyFiltersRequest.class)))
                .thenReturn(ListVocabularyFiltersResponse.builder().build());

        template.send("direct:listVocabularyFilters", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.JOB_NAME_CONTAINS, "prod");
            exchange.getIn().setHeader(Transcribe2Constants.MAX_RESULTS, 25);
        });

        ArgumentCaptor<ListVocabularyFiltersRequest> captor = ArgumentCaptor.forClass(ListVocabularyFiltersRequest.class);
        verify(transcribeClient).listVocabularyFilters(captor.capture());
        assertEquals("prod", captor.getValue().nameContains());
        assertEquals(25, captor.getValue().maxResults());
    }

    @Test
    public void createLanguageModelBuildsInputDataConfig() throws Exception {
        when(transcribeClient.createLanguageModel(any(CreateLanguageModelRequest.class)))
                .thenReturn(CreateLanguageModelResponse.builder().build());

        template.send("direct:createLanguageModel", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_MODEL_NAME, "my-model");
            exchange.getIn().setHeader(Transcribe2Constants.BASE_MODEL_NAME, "WideBand");
            exchange.getIn().setHeader(Transcribe2Constants.INPUT_DATA_S3_URI, "s3://bucket/training");
            exchange.getIn().setHeader(Transcribe2Constants.DATA_ACCESS_ROLE_ARN, "arn:aws:iam::1:role/r");
        });

        ArgumentCaptor<CreateLanguageModelRequest> captor = ArgumentCaptor.forClass(CreateLanguageModelRequest.class);
        verify(transcribeClient).createLanguageModel(captor.capture());
        assertEquals("my-model", captor.getValue().modelName());
        assertEquals("WideBand", captor.getValue().baseModelNameAsString());
        assertEquals("s3://bucket/training", captor.getValue().inputDataConfig().s3Uri());
        assertEquals("arn:aws:iam::1:role/r", captor.getValue().inputDataConfig().dataAccessRoleArn());
    }

    @Test
    public void startMedicalTranscriptionJobBuildsMediaAndSpecialty() throws Exception {
        when(transcribeClient.startMedicalTranscriptionJob(any(StartMedicalTranscriptionJobRequest.class)))
                .thenReturn(StartMedicalTranscriptionJobResponse.builder().build());

        template.send("direct:startMedicalTranscriptionJob", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.MEDICAL_TRANSCRIPTION_JOB_NAME, "job-1");
            exchange.getIn().setHeader(Transcribe2Constants.LANGUAGE_CODE, "en-US");
            exchange.getIn().setHeader(Transcribe2Constants.MEDIA_URI, "s3://bucket/audio.wav");
            exchange.getIn().setHeader(Transcribe2Constants.OUTPUT_BUCKET_NAME, "out-bucket");
            exchange.getIn().setHeader(Transcribe2Constants.SPECIALTY, "PRIMARYCARE");
            exchange.getIn().setHeader(Transcribe2Constants.TYPE, "DICTATION");
        });

        ArgumentCaptor<StartMedicalTranscriptionJobRequest> captor
                = ArgumentCaptor.forClass(StartMedicalTranscriptionJobRequest.class);
        verify(transcribeClient).startMedicalTranscriptionJob(captor.capture());
        assertEquals("job-1", captor.getValue().medicalTranscriptionJobName());
        assertEquals("s3://bucket/audio.wav", captor.getValue().media().mediaFileUri());
        assertEquals("out-bucket", captor.getValue().outputBucketName());
        assertEquals("PRIMARYCARE", captor.getValue().specialtyAsString());
        assertEquals("DICTATION", captor.getValue().typeAsString());
    }

    @Test
    public void tagResourceConvertsTheTagMap() throws Exception {
        when(transcribeClient.tagResource(any(TagResourceRequest.class)))
                .thenReturn(TagResourceResponse.builder().build());

        template.send("direct:tagResource", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.RESOURCE_ARN, "arn:aws:transcribe:::job/j");
            exchange.getIn().setHeader(Transcribe2Constants.TAGS, Map.of("env", "prod"));
        });

        ArgumentCaptor<TagResourceRequest> captor = ArgumentCaptor.forClass(TagResourceRequest.class);
        verify(transcribeClient).tagResource(captor.capture());
        assertEquals("arn:aws:transcribe:::job/j", captor.getValue().resourceArn());
        assertEquals(1, captor.getValue().tags().size());
        assertEquals("env", captor.getValue().tags().get(0).key());
        assertEquals("prod", captor.getValue().tags().get(0).value());
    }

    @Test
    public void untagResourceUsesTagKeys() throws Exception {
        when(transcribeClient.untagResource(any(UntagResourceRequest.class)))
                .thenReturn(UntagResourceResponse.builder().build());

        template.send("direct:untagResource", exchange -> {
            exchange.getIn().setHeader(Transcribe2Constants.RESOURCE_ARN, "arn:aws:transcribe:::job/j");
            exchange.getIn().setHeader(Transcribe2Constants.TAG_KEYS, List.of("env", "team"));
        });

        ArgumentCaptor<UntagResourceRequest> captor = ArgumentCaptor.forClass(UntagResourceRequest.class);
        verify(transcribeClient).untagResource(captor.capture());
        assertEquals(List.of("env", "team"), captor.getValue().tagKeys());
    }

    @Test
    public void listTagsForResourceUsesResourceArn() throws Exception {
        when(transcribeClient.listTagsForResource(any(ListTagsForResourceRequest.class)))
                .thenReturn(ListTagsForResourceResponse.builder().build());

        template.send("direct:listTagsForResource",
                exchange -> exchange.getIn().setHeader(Transcribe2Constants.RESOURCE_ARN, "arn:aws:transcribe:::job/j"));

        ArgumentCaptor<ListTagsForResourceRequest> captor = ArgumentCaptor.forClass(ListTagsForResourceRequest.class);
        verify(transcribeClient).listTagsForResource(captor.capture());
        assertEquals("arn:aws:transcribe:::job/j", captor.getValue().resourceArn());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String base = "aws2-transcribe://transcribe?transcribeClient=#transcribeClient&operation=";
                from("direct:createVocabularyFilter").to(base + "createVocabularyFilter");
                from("direct:listVocabularyFilters").to(base + "listVocabularyFilters");
                from("direct:createLanguageModel").to(base + "createLanguageModel");
                from("direct:startMedicalTranscriptionJob").to(base + "startMedicalTranscriptionJob");
                from("direct:tagResource").to(base + "tagResource");
                from("direct:untagResource").to(base + "untagResource");
                from("direct:listTagsForResource").to(base + "listTagsForResource");
            }
        };
    }
}
