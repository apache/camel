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
package org.apache.camel.component.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.openai.core.ObjectMappers;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionDiarized;
import com.openai.models.audio.transcriptions.TranscriptionInclude;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ai.observability.GenAiObservation;
import org.apache.camel.component.ai.observability.GenAiOperationName;
import org.apache.camel.component.ai.observability.GenAiUsage;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI producer for audio transcription.
 */
public class OpenAIAudioTranscriptionProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAIAudioTranscriptionProducer.class);

    public OpenAIAudioTranscriptionProducer(OpenAIEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public OpenAIEndpoint getEndpoint() {
        return (OpenAIEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        OpenAIConfiguration config = getEndpoint().getConfiguration();
        Message in = exchange.getIn();

        String model = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_MODEL, config.getAudioModel(),
                String.class);
        if (model == null) {
            throw new IllegalArgumentException(
                    "Audio model must be specified via audioModel parameter or CamelOpenAIAudioModel header");
        }

        TranscriptionCreateParams params = buildParams(in, config, model);
        GenAiObservation observation = OpenAIGenAiProducerSupport.start(exchange, GenAiOperationName.TRANSCRIPTION, model);
        TranscriptionCreateResponse response;
        try {
            response = getEndpoint().getClient().audio().transcriptions().create(params);
            OpenAIGenAiProducerSupport.recordSuccess(observation, GenAiUsage.of((Long) null, null, null, model));
        } catch (Exception e) {
            OpenAIGenAiProducerSupport.recordFailure(exchange, observation, e);
            throw e;
        } finally {
            observation.close();
        }

        populateOutput(exchange, config, response);
    }

    private static TranscriptionCreateParams buildParams(Message in, OpenAIConfiguration config, String model) {
        String language = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_LANGUAGE,
                config.getAudioLanguage(), String.class);
        String responseFormat = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_RESPONSE_FORMAT,
                config.getAudioResponseFormat(), String.class);
        Double temperature = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_TEMPERATURE,
                config.getAudioTemperature(), Double.class);
        String prompt = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_PROMPT, config.getAudioPrompt(),
                String.class);
        String timestampGranularities = OpenAIAudioSupport.resolveParameter(in,
                OpenAIConstants.AUDIO_TIMESTAMP_GRANULARITIES, config.getAudioTimestampGranularities(), String.class);
        String chunkingStrategy = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_CHUNKING_STRATEGY,
                config.getAudioChunkingStrategy(), String.class);
        String knownSpeakerNames = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_KNOWN_SPEAKER_NAMES,
                config.getAudioKnownSpeakerNames(), String.class);
        String knownSpeakerReferences = OpenAIAudioSupport.resolveParameter(in,
                OpenAIConstants.AUDIO_KNOWN_SPEAKER_REFERENCES, config.getAudioKnownSpeakerReferences(), String.class);
        String keywords = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_KEYWORDS,
                config.getAudioKeywords(), String.class);
        String languages = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_LANGUAGES,
                config.getAudioLanguages(), String.class);
        String include = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.AUDIO_INCLUDE,
                config.getAudioInclude(), String.class);

        TranscriptionCreateParams.Builder paramsBuilder = TranscriptionCreateParams.builder()
                .model(model);

        OpenAIAudioSupport.applyFileInput(in, paramsBuilder::file, paramsBuilder::file);

        if (ObjectHelper.isNotEmpty(language)) {
            paramsBuilder.language(language);
        }
        if (ObjectHelper.isNotEmpty(prompt)) {
            paramsBuilder.prompt(prompt);
        }
        if (ObjectHelper.isNotEmpty(responseFormat)) {
            paramsBuilder.responseFormat(AudioResponseFormat.of(responseFormat));
        }
        if (temperature != null) {
            paramsBuilder.temperature(temperature);
        }
        if (ObjectHelper.isNotEmpty(timestampGranularities)) {
            List<TranscriptionCreateParams.TimestampGranularity> granularities = new ArrayList<>();
            for (String g : timestampGranularities.split(",")) {
                String trimmed = g.trim();
                if (!trimmed.isEmpty()) {
                    granularities.add(TranscriptionCreateParams.TimestampGranularity.of(trimmed));
                }
            }
            if (!granularities.isEmpty()) {
                paramsBuilder.timestampGranularities(granularities);
            }
        }
        applyChunkingStrategy(paramsBuilder, chunkingStrategy);
        applyCommaSeparatedList(knownSpeakerNames, paramsBuilder::addKnownSpeakerName);
        applyCommaSeparatedList(knownSpeakerReferences, paramsBuilder::addKnownSpeakerReference);
        applyCommaSeparatedList(keywords, paramsBuilder::addKeyword);
        applyCommaSeparatedList(languages, paramsBuilder::addLanguage);
        applyIncludeList(include, paramsBuilder);

        return paramsBuilder.build();
    }

    private static void applyChunkingStrategy(TranscriptionCreateParams.Builder paramsBuilder, String chunkingStrategy) {
        if (ObjectHelper.isEmpty(chunkingStrategy)) {
            return;
        }
        if ("auto".equalsIgnoreCase(chunkingStrategy)) {
            paramsBuilder.chunkingStrategyAuto();
            return;
        }
        if ("vad".equalsIgnoreCase(chunkingStrategy)) {
            paramsBuilder.chunkingStrategy(TranscriptionCreateParams.ChunkingStrategy.VadConfig.builder()
                    .type(TranscriptionCreateParams.ChunkingStrategy.VadConfig.Type.SERVER_VAD)
                    .build());
            return;
        }
        throw new IllegalArgumentException(
                "Unsupported audio chunking strategy: " + chunkingStrategy + ". Supported values are auto and vad.");
    }

    private static void applyCommaSeparatedList(String value, Consumer<String> consumer) {
        for (String item : OpenAIAudioSupport.parseCommaSeparatedValues(value)) {
            consumer.accept(item);
        }
    }

    private static void applyIncludeList(String include, TranscriptionCreateParams.Builder paramsBuilder) {
        for (String item : OpenAIAudioSupport.parseCommaSeparatedValues(include)) {
            paramsBuilder.addInclude(TranscriptionInclude.of(item));
        }
    }

    private static void populateOutput(Exchange exchange, OpenAIConfiguration config, TranscriptionCreateResponse response) {
        Message out = exchange.getMessage();
        TranscriptionCreateResponse storedResponse = response;

        if (response.isVerbose()) {
            TranscriptionVerbose verbose = response.asVerbose();
            out.setBody(verbose.text());
            out.setHeader(OpenAIConstants.AUDIO_DURATION, verbose.duration());
            out.setHeader(OpenAIConstants.AUDIO_DETECTED_LANGUAGE, verbose.language());
        } else if (response.isDiarized()) {
            applyDiarizedOutput(out, response.asDiarized());
        } else if (response.isTranscription()) {
            String text = response.asTranscription().text();
            TranscriptionCreateResponse reparsed = tryParseStructuredTranscription(text);
            if (reparsed != null && reparsed.isDiarized()) {
                storedResponse = reparsed;
                applyDiarizedOutput(out, reparsed.asDiarized());
            } else {
                out.setBody(text);
            }
        } else {
            out.setBody(response.toString());
        }

        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.AUDIO_TRANSCRIPTION_RESPONSE, storedResponse);
        }
    }

    private static void applyDiarizedOutput(Message out, TranscriptionDiarized diarized) {
        out.setBody(diarized.text());
        out.setHeader(OpenAIConstants.AUDIO_DURATION, diarized.duration());
        out.setHeader(OpenAIConstants.AUDIO_DIARIZED_SEGMENTS, diarized.segments());
    }

    /**
     * The OpenAI Java SDK delivers {@code diarized_json} responses through the plain-text handler, so the JSON payload
     * arrives as {@link com.openai.models.audio.transcriptions.Transcription#text()}. Re-parse it when possible.
     */
    private static TranscriptionCreateResponse tryParseStructuredTranscription(String text) {
        if (ObjectHelper.isEmpty(text) || !text.startsWith("{")) {
            return null;
        }
        try {
            return ObjectMappers.jsonMapper().readValue(text, TranscriptionCreateResponse.class);
        } catch (Exception e) {
            LOG.warn("Failed to re-parse diarized_json transcription response; returning raw text. Error: {}",
                    e.getMessage());
            return null;
        }
    }
}
