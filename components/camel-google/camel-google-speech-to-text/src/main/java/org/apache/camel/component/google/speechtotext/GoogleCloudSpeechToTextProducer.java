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
package org.apache.camel.component.google.speechtotext;

import java.util.stream.Collectors;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;

public class GoogleCloudSpeechToTextProducer extends DefaultProducer {

    private final GoogleCloudSpeechToTextEndpoint endpoint;

    public GoogleCloudSpeechToTextProducer(GoogleCloudSpeechToTextEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        if (getConfiguration().isPojoRequest()) {
            processPojo(exchange);
        } else {
            processAudio(exchange);
        }
    }

    private void processPojo(Exchange exchange) throws InvalidPayloadException {
        SpeechClient client = endpoint.getClient();
        RecognizeRequest request = exchange.getIn().getMandatoryBody(RecognizeRequest.class);

        RecognizeResponse response = client.recognize(request);

        Message message = exchange.getMessage();
        message.setHeader(GoogleCloudSpeechToTextConstants.RESPONSE_OBJECT, response);
        message.setBody(extractTranscript(response));
    }

    private void processAudio(Exchange exchange) throws InvalidPayloadException {
        SpeechClient client = endpoint.getClient();
        GoogleCloudSpeechToTextOperations operation = determineOperation(exchange);

        switch (operation) {
            case recognize:
                performRecognize(exchange, client);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void performRecognize(Exchange exchange, SpeechClient client) throws InvalidPayloadException {
        byte[] audioData = exchange.getIn().getMandatoryBody(byte[].class);

        RecognitionConfig.Builder configBuilder = RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.valueOf(getConfiguration().getEncoding()))
                .setLanguageCode(getConfiguration().getLanguageCode());

        if (getConfiguration().getSampleRateHertz() != null) {
            configBuilder.setSampleRateHertz(getConfiguration().getSampleRateHertz());
        }

        RecognitionConfig config = configBuilder.build();

        RecognitionAudio audio = RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .build();

        RecognizeResponse response = client.recognize(config, audio);

        Message message = exchange.getMessage();
        message.setHeader(GoogleCloudSpeechToTextConstants.RESPONSE_OBJECT, response);
        message.setBody(extractTranscript(response));
    }

    private String extractTranscript(RecognizeResponse response) {
        if (response.getResultsList().isEmpty()) {
            return "";
        }
        return response.getResultsList().stream()
                .filter(r -> !r.getAlternativesList().isEmpty())
                .map(r -> r.getAlternativesList().get(0).getTranscript())
                .collect(Collectors.joining("\n"));
    }

    private GoogleCloudSpeechToTextOperations determineOperation(Exchange exchange) {
        GoogleCloudSpeechToTextOperations operation = exchange.getIn().getHeader(
                GoogleCloudSpeechToTextConstants.OPERATION,
                GoogleCloudSpeechToTextOperations.class);
        if (operation == null) {
            String operationName = getConfiguration().getOperation();
            if (operationName != null) {
                operation = GoogleCloudSpeechToTextOperations.valueOf(operationName);
            }
        }
        if (operation == null) {
            throw new IllegalArgumentException(
                    "Operation must be specified via endpoint URI, configuration, or message header.");
        }
        return operation;
    }

    private GoogleCloudSpeechToTextConfiguration getConfiguration() {
        return this.endpoint.getConfiguration();
    }
}
