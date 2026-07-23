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

/**
 * Groups the configured mock expectations for each supported OpenAI API so they can be passed to
 * {@link OpenAIMockServerHandler} as a single value instead of a long positional argument list.
 */
public record OpenAIMockExpectations(
        List<MockExpectation> chat,
        List<EmbeddingExpectation> embeddings,
        List<AudioTranscriptionExpectation> transcriptions,
        List<AudioTranscriptionExpectation> translations,
        List<SpeechExpectation> speeches) {

    /**
     * Convenience factory for callers that only need chat completion expectations (e.g. SSL/TLS tests).
     */
    public static OpenAIMockExpectations ofChat(List<MockExpectation> chat) {
        return new OpenAIMockExpectations(chat, List.of(), List.of(), List.of(), List.of());
    }
}
