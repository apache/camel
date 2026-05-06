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
package org.apache.camel.test.infra.openai.mock;

import java.util.List;


import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builder class for creating OpenAI audio transcription API mock responses.
 */
public class AudioTranscriptionResponseBuilder {
    private final ObjectMapper objectMapper;

    public AudioTranscriptionResponseBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String createTranscriptionResponse(AudioTranscriptionExpectation expectation) throws Exception {
        if (expectation.isVerbose()) {
            return createVerboseResponse(expectation);
        }
        return createSimpleResponse(expectation);
    }

    private String createSimpleResponse(AudioTranscriptionExpectation expectation) throws Exception {
        record TranscriptionResponse(String text) {
        }

        return objectMapper.writeValueAsString(new TranscriptionResponse(expectation.getTranscriptionText()));
    }

    private String createVerboseResponse(AudioTranscriptionExpectation expectation) throws Exception {
        record VerboseTranscriptionResponse(String text, String language, double duration, List<?> segments,
                List<?> words) {
        }

        return objectMapper.writeValueAsString(new VerboseTranscriptionResponse(
                expectation.getTranscriptionText(),
                expectation.getLanguage(),
                expectation.getDuration(),
                List.of(),
                List.of()));
    }
}
