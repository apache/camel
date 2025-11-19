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
package org.apache.camel.component.ibm.watson.stt;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import com.ibm.watson.speech_to_text.v1.SpeechToText;
import com.ibm.watson.speech_to_text.v1.model.GetModelOptions;
import com.ibm.watson.speech_to_text.v1.model.LanguageModel;
import com.ibm.watson.speech_to_text.v1.model.LanguageModels;
import com.ibm.watson.speech_to_text.v1.model.ListLanguageModelsOptions;
import com.ibm.watson.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.speech_to_text.v1.model.SpeechModel;
import com.ibm.watson.speech_to_text.v1.model.SpeechModels;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResult;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatsonSpeechToTextProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonSpeechToTextProducer.class);

    public WatsonSpeechToTextProducer(WatsonSpeechToTextEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        WatsonSpeechToTextOperations operation = determineOperation(exchange);

        switch (operation) {
            case recognize:
                recognize(exchange);
                break;
            case listModels:
                listModels(exchange);
                break;
            case getModel:
                getModel(exchange);
                break;
            case listCustomModels:
                listCustomModels(exchange);
                break;
            case getCustomModel:
                getCustomModel(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonSpeechToTextEndpoint getEndpoint() {
        return (WatsonSpeechToTextEndpoint) super.getEndpoint();
    }

    private WatsonSpeechToTextOperations determineOperation(Exchange exchange) {
        WatsonSpeechToTextOperations operation
                = exchange.getIn().getHeader(WatsonSpeechToTextConstants.OPERATION, WatsonSpeechToTextOperations.class);

        if (operation == null) {
            operation = getEndpoint().getConfiguration().getOperation();
        }

        if (operation == null) {
            throw new IllegalArgumentException("Operation must be specified");
        }

        return operation;
    }

    private void recognize(Exchange exchange) throws Exception {
        SpeechToText stt = getEndpoint().getSttClient();
        if (stt == null) {
            throw new IllegalStateException("STT client not initialized");
        }

        // Get audio input from header or body
        File audioFile = exchange.getIn().getHeader(WatsonSpeechToTextConstants.AUDIO_FILE, File.class);
        InputStream audioStream = null;

        if (audioFile != null) {
            audioStream = new FileInputStream(audioFile);
        } else {
            audioStream = exchange.getIn().getBody(InputStream.class);
            if (audioStream == null) {
                File bodyFile = exchange.getIn().getBody(File.class);
                if (bodyFile != null) {
                    audioStream = new FileInputStream(bodyFile);
                }
            }
        }

        if (audioStream == null) {
            throw new IllegalArgumentException("Audio input (InputStream or File) must be specified");
        }

        String model = exchange.getIn().getHeader(WatsonSpeechToTextConstants.MODEL,
                getEndpoint().getConfiguration().getModel(), String.class);
        String contentType = exchange.getIn().getHeader(WatsonSpeechToTextConstants.CONTENT_TYPE,
                getEndpoint().getConfiguration().getContentType(), String.class);
        Boolean timestamps = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TIMESTAMPS,
                getEndpoint().getConfiguration().isTimestamps(), Boolean.class);
        Boolean wordConfidence = exchange.getIn().getHeader(WatsonSpeechToTextConstants.WORD_CONFIDENCE,
                getEndpoint().getConfiguration().isWordConfidence(), Boolean.class);
        Boolean speakerLabels = exchange.getIn().getHeader(WatsonSpeechToTextConstants.SPEAKER_LABELS,
                getEndpoint().getConfiguration().isSpeakerLabels(), Boolean.class);

        LOG.trace("Recognizing audio with STT: model={}, contentType={}", model, contentType);

        RecognizeOptions options = new RecognizeOptions.Builder()
                .audio(audioStream)
                .model(model)
                .contentType(contentType)
                .timestamps(timestamps)
                .wordConfidence(wordConfidence)
                .speakerLabels(speakerLabels)
                .build();

        SpeechRecognitionResults results = stt.recognize(options).execute().getResult();

        // Extract transcript text
        StringBuilder transcript = new StringBuilder();
        if (results.getResults() != null && !results.getResults().isEmpty()) {
            for (SpeechRecognitionResult result : results.getResults()) {
                if (result.getAlternatives() != null && !result.getAlternatives().isEmpty()) {
                    transcript.append(result.getAlternatives().get(0).getTranscript());
                }
            }
        }

        Message message = getMessageForResponse(exchange);
        message.setBody(results);
        message.setHeader(WatsonSpeechToTextConstants.TRANSCRIPT, transcript.toString());
        message.setHeader(WatsonSpeechToTextConstants.MODEL, model);
        message.setHeader(WatsonSpeechToTextConstants.CONTENT_TYPE, contentType);
    }

    private void listModels(Exchange exchange) {
        SpeechToText stt = getEndpoint().getSttClient();
        if (stt == null) {
            throw new IllegalStateException("STT client not initialized");
        }

        LOG.trace("Listing available speech recognition models");

        SpeechModels models = stt.listModels().execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(models.getModels());
    }

    private void getModel(Exchange exchange) {
        SpeechToText stt = getEndpoint().getSttClient();
        if (stt == null) {
            throw new IllegalStateException("STT client not initialized");
        }

        String modelName = exchange.getIn().getHeader(WatsonSpeechToTextConstants.MODEL_NAME, String.class);
        if (modelName == null) {
            modelName = exchange.getIn().getBody(String.class);
        }

        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("Model name must be specified");
        }

        LOG.trace("Getting model information for: {}", modelName);

        GetModelOptions options = new GetModelOptions.Builder()
                .modelId(modelName)
                .build();

        SpeechModel model = stt.getModel(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(model);
    }

    private void listCustomModels(Exchange exchange) {
        SpeechToText stt = getEndpoint().getSttClient();
        if (stt == null) {
            throw new IllegalStateException("STT client not initialized");
        }

        String language = exchange.getIn().getHeader(WatsonSpeechToTextConstants.LANGUAGE, String.class);

        LOG.trace("Listing custom language models, language filter: {}", language);

        ListLanguageModelsOptions.Builder builder = new ListLanguageModelsOptions.Builder();
        if (language != null && !language.isBlank()) {
            builder.language(language);
        }

        ListLanguageModelsOptions options = builder.build();
        LanguageModels customModels = stt.listLanguageModels(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        List<LanguageModel> models = customModels.getCustomizations();
        message.setBody(models != null ? models : List.of());
    }

    private void getCustomModel(Exchange exchange) {
        SpeechToText stt = getEndpoint().getSttClient();
        if (stt == null) {
            throw new IllegalStateException("STT client not initialized");
        }

        String modelId = exchange.getIn().getHeader(WatsonSpeechToTextConstants.MODEL_NAME, String.class);
        if (modelId == null) {
            modelId = exchange.getIn().getBody(String.class);
        }

        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Custom model ID must be specified");
        }

        LOG.trace("Getting custom language model: {}", modelId);

        com.ibm.watson.speech_to_text.v1.model.GetLanguageModelOptions options
                = new com.ibm.watson.speech_to_text.v1.model.GetLanguageModelOptions.Builder()
                        .customizationId(modelId)
                        .build();

        LanguageModel customModel = stt.getLanguageModel(options).execute().getResult();

        Message message = getMessageForResponse(exchange);
        message.setBody(customModel);
    }

    private Message getMessageForResponse(Exchange exchange) {
        return exchange.getMessage();
    }
}
