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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.openai.core.MultipartField;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionVerbose;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for audio transcription.
 */
public class OpenAIAudioTranscriptionProducer extends DefaultProducer {

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

        String model = resolveParameter(in, OpenAIConstants.AUDIO_MODEL, config.getAudioModel(), String.class);
        if (model == null) {
            throw new IllegalArgumentException(
                    "Audio model must be specified via audioModel parameter or CamelOpenAIAudioModel header");
        }

        String language = resolveParameter(in, OpenAIConstants.AUDIO_LANGUAGE, config.getAudioLanguage(), String.class);
        String responseFormat = resolveParameter(in, OpenAIConstants.AUDIO_RESPONSE_FORMAT,
                config.getAudioResponseFormat(), String.class);
        Double temperature = resolveParameter(in, OpenAIConstants.AUDIO_TEMPERATURE, config.getAudioTemperature(),
                Double.class);
        String prompt = resolveParameter(in, OpenAIConstants.AUDIO_PROMPT, config.getAudioPrompt(), String.class);
        String timestampGranularities = resolveParameter(in, OpenAIConstants.AUDIO_TIMESTAMP_GRANULARITIES,
                config.getAudioTimestampGranularities(), String.class);

        TranscriptionCreateParams.Builder paramsBuilder = TranscriptionCreateParams.builder()
                .model(model);

        setFileInput(paramsBuilder, in);

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

        TranscriptionCreateParams params = paramsBuilder.build();
        TranscriptionCreateResponse response = getEndpoint().getClient()
                .audio().transcriptions().create(params);

        Message out = exchange.getMessage();

        if (response.isVerbose()) {
            TranscriptionVerbose verbose = response.asVerbose();
            out.setBody(verbose.text());
            out.setHeader(OpenAIConstants.AUDIO_DURATION, verbose.duration());
            out.setHeader(OpenAIConstants.AUDIO_DETECTED_LANGUAGE, verbose.language());
        } else if (response.isTranscription()) {
            out.setBody(response.asTranscription().text());
        } else {
            out.setBody(response.toString());
        }

        if (config.isStoreFullResponse()) {
            exchange.setProperty(OpenAIConstants.RESPONSE, response);
        }
    }

    private void setFileInput(TranscriptionCreateParams.Builder paramsBuilder, Message in) {
        Object body = in.getBody();

        if (body instanceof WrappedFile<?> wrappedFile) {
            body = wrappedFile.getFile();
        }

        if (body instanceof File file) {
            paramsBuilder.file(file.toPath());
        } else if (body instanceof Path path) {
            paramsBuilder.file(path);
        } else if (body instanceof byte[] bytes) {
            paramsBuilder.file(multipartWithFilename(new ByteArrayInputStream(bytes), resolveFilename(in)));
        } else if (body instanceof InputStream inputStream) {
            paramsBuilder.file(multipartWithFilename(inputStream, resolveFilename(in)));
        } else {
            InputStream converted = in.getBody(InputStream.class);
            if (converted == null) {
                throw new IllegalArgumentException(
                        "Unsupported body type for audio transcription: "
                                                   + (body != null ? body.getClass().getName() : "null")
                                                   + ". Supported: File, Path, InputStream, byte[]");
            }
            paramsBuilder.file(multipartWithFilename(converted, resolveFilename(in)));
        }
    }

    private String resolveFilename(Message in) {
        String filename = in.getHeader(Exchange.FILE_NAME_ONLY, String.class);
        if (ObjectHelper.isNotEmpty(filename)) {
            return filename;
        }
        return "audio";
    }

    private MultipartField<InputStream> multipartWithFilename(InputStream stream, String filename) {
        return MultipartField.<InputStream> builder()
                .value(stream)
                .filename(filename)
                .build();
    }

    private <T> T resolveParameter(Message message, String headerName, T defaultValue, Class<T> type) {
        if (headerName != null) {
            T headerValue = message.getHeader(headerName, type);
            return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
        }
        return defaultValue;
    }
}
