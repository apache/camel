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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.PollyServiceClientConfiguration;
import software.amazon.awssdk.services.polly.model.DeleteLexiconRequest;
import software.amazon.awssdk.services.polly.model.DeleteLexiconResponse;
import software.amazon.awssdk.services.polly.model.DescribeVoicesRequest;
import software.amazon.awssdk.services.polly.model.DescribeVoicesResponse;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.Gender;
import software.amazon.awssdk.services.polly.model.GetLexiconRequest;
import software.amazon.awssdk.services.polly.model.GetLexiconResponse;
import software.amazon.awssdk.services.polly.model.GetSpeechSynthesisTaskRequest;
import software.amazon.awssdk.services.polly.model.GetSpeechSynthesisTaskResponse;
import software.amazon.awssdk.services.polly.model.LanguageCode;
import software.amazon.awssdk.services.polly.model.Lexicon;
import software.amazon.awssdk.services.polly.model.LexiconDescription;
import software.amazon.awssdk.services.polly.model.ListLexiconsRequest;
import software.amazon.awssdk.services.polly.model.ListLexiconsResponse;
import software.amazon.awssdk.services.polly.model.ListSpeechSynthesisTasksRequest;
import software.amazon.awssdk.services.polly.model.ListSpeechSynthesisTasksResponse;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.PutLexiconRequest;
import software.amazon.awssdk.services.polly.model.PutLexiconResponse;
import software.amazon.awssdk.services.polly.model.StartSpeechSynthesisTaskRequest;
import software.amazon.awssdk.services.polly.model.StartSpeechSynthesisTaskResponse;
import software.amazon.awssdk.services.polly.model.SynthesisTask;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TaskStatus;
import software.amazon.awssdk.services.polly.model.Voice;
import software.amazon.awssdk.services.polly.model.VoiceId;

public class AmazonPollyClientMock implements PollyClient {

    @Override
    public ResponseInputStream<SynthesizeSpeechResponse> synthesizeSpeech(SynthesizeSpeechRequest request) {
        SynthesizeSpeechResponse response = SynthesizeSpeechResponse.builder()
                .contentType("audio/mpeg")
                .requestCharacters(request.text().length())
                .build();

        byte[] audioData = "mock-audio-data".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(audioData);
        AbortableInputStream abortableInputStream = AbortableInputStream.create(inputStream);

        return new ResponseInputStream<>(response, abortableInputStream);
    }

    @Override
    public DescribeVoicesResponse describeVoices(DescribeVoicesRequest request) {
        Voice voice1 = Voice.builder()
                .id(VoiceId.JOANNA)
                .name("Joanna")
                .languageCode(LanguageCode.EN_US)
                .languageName("US English")
                .gender(Gender.FEMALE)
                .supportedEngines(Engine.NEURAL, Engine.STANDARD)
                .build();

        Voice voice2 = Voice.builder()
                .id(VoiceId.MATTHEW)
                .name("Matthew")
                .languageCode(LanguageCode.EN_US)
                .languageName("US English")
                .gender(Gender.MALE)
                .supportedEngines(Engine.NEURAL, Engine.STANDARD)
                .build();

        return DescribeVoicesResponse.builder()
                .voices(Arrays.asList(voice1, voice2))
                .build();
    }

    @Override
    public ListLexiconsResponse listLexicons(ListLexiconsRequest request) {
        LexiconDescription lexicon = LexiconDescription.builder()
                .name("TestLexicon")
                .build();

        return ListLexiconsResponse.builder()
                .lexicons(Arrays.asList(lexicon))
                .build();
    }

    @Override
    public GetLexiconResponse getLexicon(GetLexiconRequest request) {
        Lexicon lexicon = Lexicon.builder()
                .name(request.name())
                .content("<?xml version=\"1.0\" encoding=\"UTF-8\"?><lexicon></lexicon>")
                .build();

        return GetLexiconResponse.builder()
                .lexicon(lexicon)
                .build();
    }

    @Override
    public PutLexiconResponse putLexicon(PutLexiconRequest request) {
        return PutLexiconResponse.builder().build();
    }

    @Override
    public DeleteLexiconResponse deleteLexicon(DeleteLexiconRequest request) {
        return DeleteLexiconResponse.builder().build();
    }

    @Override
    public StartSpeechSynthesisTaskResponse startSpeechSynthesisTask(StartSpeechSynthesisTaskRequest request) {
        SynthesisTask task = SynthesisTask.builder()
                .taskId("test-task-id-123")
                .taskStatus(TaskStatus.IN_PROGRESS)
                .outputFormat(request.outputFormat())
                .voiceId(request.voiceId())
                .outputUri("s3://" + request.outputS3BucketName() + "/output.mp3")
                .build();

        return StartSpeechSynthesisTaskResponse.builder()
                .synthesisTask(task)
                .build();
    }

    @Override
    public GetSpeechSynthesisTaskResponse getSpeechSynthesisTask(GetSpeechSynthesisTaskRequest request) {
        SynthesisTask task = SynthesisTask.builder()
                .taskId(request.taskId())
                .taskStatus(TaskStatus.COMPLETED)
                .outputFormat(OutputFormat.MP3)
                .voiceId(VoiceId.JOANNA)
                .outputUri("s3://test-bucket/output.mp3")
                .build();

        return GetSpeechSynthesisTaskResponse.builder()
                .synthesisTask(task)
                .build();
    }

    @Override
    public ListSpeechSynthesisTasksResponse listSpeechSynthesisTasks(ListSpeechSynthesisTasksRequest request) {
        SynthesisTask task = SynthesisTask.builder()
                .taskId("test-task-id-123")
                .taskStatus(TaskStatus.COMPLETED)
                .outputFormat(OutputFormat.MP3)
                .voiceId(VoiceId.JOANNA)
                .build();

        return ListSpeechSynthesisTasksResponse.builder()
                .synthesisTasks(Arrays.asList(task))
                .build();
    }

    @Override
    public PollyServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        return "polly";
    }

    @Override
    public void close() {
    }
}
