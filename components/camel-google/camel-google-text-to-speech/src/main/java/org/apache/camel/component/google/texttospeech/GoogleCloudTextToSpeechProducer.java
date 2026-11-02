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
package org.apache.camel.component.google.texttospeech;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.ListVoicesRequest;
import com.google.cloud.texttospeech.v1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class GoogleCloudTextToSpeechProducer extends DefaultProducer {

    private final GoogleCloudTextToSpeechEndpoint endpoint;

    public GoogleCloudTextToSpeechProducer(GoogleCloudTextToSpeechEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        GoogleCloudTextToSpeechOperations operation = determineOperation(exchange);
        switch (operation) {
            case synthesize:
                if (getConfiguration().isPojoRequest()) {
                    processSynthesizePojo(exchange);
                } else {
                    processSynthesize(exchange);
                }
                break;
            case listVoices:
                if (getConfiguration().isPojoRequest()) {
                    processListVoicesPojo(exchange);
                } else {
                    processListVoices(exchange);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void processSynthesizePojo(Exchange exchange) throws InvalidPayloadException {
        TextToSpeechClient client = endpoint.getClient();
        SynthesizeSpeechRequest request = exchange.getIn().getMandatoryBody(SynthesizeSpeechRequest.class);

        SynthesizeSpeechResponse response = client.synthesizeSpeech(request);

        Message message = exchange.getMessage();
        message.setHeader(GoogleCloudTextToSpeechConstants.RESPONSE_OBJECT, response);
        message.setBody(response.getAudioContent().toByteArray());
    }

    private void processSynthesize(Exchange exchange) throws InvalidPayloadException {
        TextToSpeechClient client = endpoint.getClient();
        String text = exchange.getIn().getMandatoryBody(String.class);

        SynthesisInput input = SynthesisInput.newBuilder()
                .setText(text)
                .build();

        VoiceSelectionParams.Builder voiceBuilder = VoiceSelectionParams.newBuilder()
                .setLanguageCode(getConfiguration().getLanguageCode());

        if (getConfiguration().getVoiceName() != null) {
            voiceBuilder.setName(getConfiguration().getVoiceName());
        }

        AudioConfig.Builder audioConfigBuilder = AudioConfig.newBuilder()
                .setAudioEncoding(AudioEncoding.valueOf(getConfiguration().getAudioEncoding()));

        if (getConfiguration().getSpeakingRate() != null) {
            audioConfigBuilder.setSpeakingRate(getConfiguration().getSpeakingRate());
        }

        if (getConfiguration().getPitch() != null) {
            audioConfigBuilder.setPitch(getConfiguration().getPitch());
        }

        SynthesizeSpeechResponse response = client.synthesizeSpeech(input, voiceBuilder.build(), audioConfigBuilder.build());

        Message message = exchange.getMessage();
        message.setHeader(GoogleCloudTextToSpeechConstants.RESPONSE_OBJECT, response);
        message.setBody(response.getAudioContent().toByteArray());
    }

    private void processListVoices(Exchange exchange) {
        ListVoicesRequest.Builder requestBuilder = ListVoicesRequest.newBuilder();

        // only a language code that was set filters the voices: the default (en-US) is for synthesize
        if (getConfiguration().isLanguageCodeSet() && ObjectHelper.isNotEmpty(getConfiguration().getLanguageCode())) {
            requestBuilder.setLanguageCode(getConfiguration().getLanguageCode());
        }

        listVoices(exchange, requestBuilder.build());
    }

    private void processListVoicesPojo(Exchange exchange) throws InvalidPayloadException {
        listVoices(exchange, exchange.getIn().getMandatoryBody(ListVoicesRequest.class));
    }

    private void listVoices(Exchange exchange, ListVoicesRequest request) {
        TextToSpeechClient client = endpoint.getClient();

        ListVoicesResponse response = client.listVoices(request);

        Message message = exchange.getMessage();
        message.setBody(response.getVoicesList());
    }

    private GoogleCloudTextToSpeechOperations determineOperation(Exchange exchange) {
        GoogleCloudTextToSpeechOperations operation = exchange.getIn().getHeader(GoogleCloudTextToSpeechConstants.OPERATION,
                GoogleCloudTextToSpeechOperations.class);
        if (operation == null) {
            String operationName = getConfiguration().getOperation();
            if (operationName != null) {
                operation = GoogleCloudTextToSpeechOperations.valueOf(operationName);
            }
        }
        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified via endpoint URI, configuration, or message header.");
        }
        return operation;
    }

    private GoogleCloudTextToSpeechConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }
}
