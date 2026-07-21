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

import java.io.InputStream;
import java.util.List;

import com.openai.core.http.HttpResponse;
import com.openai.models.audio.speech.SpeechCreateParams;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenAI producer for audio speech (text-to-speech). The message body is the input text and the produced body is the
 * generated audio as a {@code byte[]}.
 */
public class OpenAIAudioSpeechProducer extends DefaultProducer {

    public OpenAIAudioSpeechProducer(OpenAIEndpoint endpoint) {
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

        String model = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.SPEECH_MODEL, config.getSpeechModel(),
                String.class);
        if (model == null) {
            throw new IllegalArgumentException(
                    "Speech model must be specified via speechModel parameter or CamelOpenAISpeechModel header");
        }

        String input = in.getBody(String.class);
        if (ObjectHelper.isEmpty(input)) {
            throw new IllegalArgumentException("The message body must contain the input text for text-to-speech");
        }

        String voice = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.SPEECH_VOICE, config.getSpeechVoice(),
                String.class);
        String responseFormat = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.SPEECH_RESPONSE_FORMAT,
                config.getSpeechResponseFormat(), String.class);
        Double speed = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.SPEECH_SPEED, config.getSpeechSpeed(),
                Double.class);
        String instructions = OpenAIAudioSupport.resolveParameter(in, OpenAIConstants.SPEECH_INSTRUCTIONS,
                config.getSpeechInstructions(), String.class);

        SpeechCreateParams.Builder paramsBuilder = SpeechCreateParams.builder()
                .model(model)
                .input(input)
                .voice(voice);

        if (ObjectHelper.isNotEmpty(responseFormat)) {
            paramsBuilder.responseFormat(SpeechCreateParams.ResponseFormat.of(responseFormat));
        }
        if (speed != null) {
            paramsBuilder.speed(speed);
        }
        if (ObjectHelper.isNotEmpty(instructions)) {
            paramsBuilder.instructions(instructions);
        }

        SpeechCreateParams params = paramsBuilder.build();

        byte[] audio;
        String contentType;
        try (HttpResponse response = getEndpoint().getClient().audio().speech().create(params);
             InputStream body = response.body()) {
            audio = body.readAllBytes();
            contentType = resolveContentType(response, responseFormat);
        }

        Message out = exchange.getMessage();
        out.setBody(audio);
        out.setHeader(Exchange.CONTENT_TYPE, contentType);
    }

    private static String resolveContentType(HttpResponse response, String responseFormat) {
        List<String> values = response.headers().values("Content-Type");
        if (values != null && !values.isEmpty()) {
            String header = values.get(0);
            if (ObjectHelper.isNotEmpty(header)) {
                return header;
            }
        }
        return contentTypeFor(responseFormat);
    }

    private static String contentTypeFor(String responseFormat) {
        if (ObjectHelper.isEmpty(responseFormat)) {
            return "audio/mpeg";
        }
        return switch (responseFormat.toLowerCase()) {
            case "opus" -> "audio/opus";
            case "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "pcm" -> "audio/pcm";
            default -> "audio/mpeg";
        };
    }
}
