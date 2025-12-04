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

package org.apache.camel.component.ibm.watson.tts;

import java.io.InputStream;
import java.util.List;

import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.CustomModel;
import com.ibm.watson.text_to_speech.v1.model.CustomModels;
import com.ibm.watson.text_to_speech.v1.model.GetCustomModelOptions;
import com.ibm.watson.text_to_speech.v1.model.GetPronunciationOptions;
import com.ibm.watson.text_to_speech.v1.model.GetVoiceOptions;
import com.ibm.watson.text_to_speech.v1.model.ListCustomModelsOptions;
import com.ibm.watson.text_to_speech.v1.model.Pronunciation;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;
import com.ibm.watson.text_to_speech.v1.model.Voice;
import com.ibm.watson.text_to_speech.v1.model.Voices;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatsonTextToSpeechProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonTextToSpeechProducer.class);

    public WatsonTextToSpeechProducer(WatsonTextToSpeechEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonTextToSpeechOperations operation = determineOperation(exchange);

        switch (operation) {
            case synthesize:
                synthesize(exchange);
                break;
            case listVoices:
                listVoices(exchange);
                break;
            case getVoice:
                getVoice(exchange);
                break;
            case listCustomModels:
                listCustomModels(exchange);
                break;
            case getCustomModel:
                getCustomModel(exchange);
                break;
            case getPronunciation:
                getPronunciation(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonTextToSpeechEndpoint getEndpoint() {
        return (WatsonTextToSpeechEndpoint) super.getEndpoint();
    }

    private WatsonTextToSpeechOperations determineOperation(Exchange exchange) {
        WatsonTextToSpeechOperations operation =
                exchange.getIn().getHeader(WatsonTextToSpeechConstants.OPERATION, WatsonTextToSpeechOperations.class);

        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        return operation;
    }

    private void synthesize(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        String text = exchange.getIn().getHeader(WatsonTextToSpeechConstants.TEXT, String.class);
        if (text == null) {
            text = exchange.getIn().getBody(String.class);
        }

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to synthesize must be specified");
        }

        String voice = exchange.getIn()
                .getHeader(
                        WatsonTextToSpeechConstants.VOICE,
                        getEndpoint().getConfiguration().getVoice(),
                        String.class);
        String accept = exchange.getIn()
                .getHeader(
                        WatsonTextToSpeechConstants.ACCEPT,
                        getEndpoint().getConfiguration().getAccept(),
                        String.class);
        String customizationId = exchange.getIn()
                .getHeader(
                        WatsonTextToSpeechConstants.CUSTOMIZATION_ID,
                        getEndpoint().getConfiguration().getCustomizationId(),
                        String.class);

        LOG.trace("Synthesizing text with TTS: voice={}, accept={}", voice, accept);

        SynthesizeOptions.Builder builder =
                new SynthesizeOptions.Builder().text(text).voice(voice).accept(accept);

        if (customizationId != null && !customizationId.isBlank()) {
            builder.customizationId(customizationId);
        }

        SynthesizeOptions options = builder.build();

        InputStream audioStream = tts.synthesize(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(audioStream);
        message.setHeader(WatsonTextToSpeechConstants.VOICE, voice);
        message.setHeader(WatsonTextToSpeechConstants.ACCEPT, accept);
    }

    private void listVoices(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        LOG.trace("Listing available voices");

        Voices voices = tts.listVoices().execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(voices.getVoices());
    }

    private void getVoice(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        String voiceName = exchange.getIn().getHeader(WatsonTextToSpeechConstants.VOICE_NAME, String.class);
        if (voiceName == null) {
            voiceName = exchange.getIn().getBody(String.class);
        }

        if (voiceName == null || voiceName.isBlank()) {
            throw new IllegalArgumentException("Voice name must be specified");
        }

        LOG.trace("Getting voice information for: {}", voiceName);

        GetVoiceOptions options = new GetVoiceOptions.Builder().voice(voiceName).build();

        Voice voice = tts.getVoice(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(voice);
    }

    private void listCustomModels(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        String language = exchange.getIn().getHeader(WatsonTextToSpeechConstants.LANGUAGE, String.class);

        LOG.trace("Listing custom models, language filter: {}", language);

        ListCustomModelsOptions.Builder builder = new ListCustomModelsOptions.Builder();
        if (language != null && !language.isBlank()) {
            builder.language(language);
        }

        ListCustomModelsOptions options = builder.build();
        CustomModels customModels = tts.listCustomModels(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        List<CustomModel> models = customModels.getCustomizations();
        message.setBody(models != null ? models : List.of());
    }

    private void getCustomModel(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        String modelId = exchange.getIn().getHeader(WatsonTextToSpeechConstants.MODEL_ID, String.class);
        if (modelId == null) {
            modelId = exchange.getIn().getBody(String.class);
        }

        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Model ID must be specified");
        }

        LOG.trace("Getting custom model: {}", modelId);

        GetCustomModelOptions options =
                new GetCustomModelOptions.Builder().customizationId(modelId).build();

        CustomModel customModel = tts.getCustomModel(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(customModel);
    }

    private void getPronunciation(Exchange exchange) {
        TextToSpeech tts = getEndpoint().getTtsClient();
        if (tts == null) {
            throw new IllegalStateException("TTS client not initialized");
        }

        String word = exchange.getIn().getHeader(WatsonTextToSpeechConstants.WORD, String.class);
        if (word == null) {
            word = exchange.getIn().getBody(String.class);
        }

        if (word == null || word.isBlank()) {
            throw new IllegalArgumentException("Word must be specified");
        }

        String voice = exchange.getIn()
                .getHeader(
                        WatsonTextToSpeechConstants.VOICE,
                        getEndpoint().getConfiguration().getVoice(),
                        String.class);
        String format = exchange.getIn().getHeader(WatsonTextToSpeechConstants.FORMAT, String.class);

        LOG.trace("Getting pronunciation for word: {}, voice: {}, format: {}", word, voice, format);

        GetPronunciationOptions.Builder builder =
                new GetPronunciationOptions.Builder().text(word).voice(voice);

        if (format != null && !format.isBlank()) {
            builder.format(format);
        }

        GetPronunciationOptions options = builder.build();
        Pronunciation pronunciation = tts.getPronunciation(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(pronunciation);
    }

    private Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
