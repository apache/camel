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
package org.apache.camel.component.google.texttospeech.unit;

import java.util.List;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.ListVoicesRequest;
import com.google.cloud.texttospeech.v1.ListVoicesResponse;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.Voice;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.component.google.texttospeech.GoogleCloudTextToSpeechComponent;
import org.apache.camel.component.google.texttospeech.GoogleCloudTextToSpeechConstants;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the requests the producer sends to a mocked {@link TextToSpeechClient}.
 */
class GoogleCloudTextToSpeechProducerTest extends CamelTestSupport {

    private static final Voice VOICE = Voice.newBuilder().setName("fr-FR-Wavenet-A").addLanguageCodes("fr-FR").build();
    private static final byte[] AUDIO = { 1, 2, (byte) 0xff };

    private final TextToSpeechClient client = mock(TextToSpeechClient.class);

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("ttsClient", client);
    }

    @BeforeEach
    void stubClient() {
        SynthesizeSpeechResponse response
                = SynthesizeSpeechResponse.newBuilder().setAudioContent(ByteString.copyFrom(AUDIO)).build();
        when(client.listVoices(any(ListVoicesRequest.class)))
                .thenReturn(ListVoicesResponse.newBuilder().addVoices(VOICE).build());
        when(client.synthesizeSpeech(any(SynthesizeSpeechRequest.class))).thenReturn(response);
        when(client.synthesizeSpeech(any(SynthesisInput.class), any(VoiceSelectionParams.class), any(AudioConfig.class)))
                .thenReturn(response);
    }

    @Test
    void listVoicesWithPojoRequestSendsTheListVoicesRequestOfTheBody() {
        ListVoicesRequest request = ListVoicesRequest.newBuilder().setLanguageCode("fr-FR").build();

        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient&pojoRequest=true",
                e -> e.getIn().setBody(request));

        assertNull(exchange.getException());
        assertEquals(List.of(VOICE), exchange.getMessage().getBody(List.class));
        verify(client).listVoices(request);
        verify(client, never()).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
    }

    @Test
    void listVoicesWithPojoRequestDoesNotSynthesizeASynthesizeSpeechRequest() {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.newBuilder()
                .setInput(SynthesisInput.newBuilder().setText("Hello")).build();

        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient&pojoRequest=true",
                e -> e.getIn().setBody(request));

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        verify(client, never()).synthesizeSpeech(any(SynthesizeSpeechRequest.class));
        verify(client, never()).listVoices(any(ListVoicesRequest.class));
    }

    @Test
    void listVoicesWithPojoRequestAndNoBodyFails() {
        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient&pojoRequest=true",
                e -> e.getIn().setBody(null));

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        verify(client, never()).listVoices(any(ListVoicesRequest.class));
    }

    @Test
    void theOperationHeaderSelectsListVoicesWithPojoRequest() {
        ListVoicesRequest request = ListVoicesRequest.newBuilder().setLanguageCode("de-DE").build();

        Exchange exchange = template.request("google-text-to-speech:synthesize?client=#ttsClient&pojoRequest=true", e -> {
            e.getIn().setHeader(GoogleCloudTextToSpeechConstants.OPERATION, "listVoices");
            e.getIn().setBody(request);
        });

        assertNull(exchange.getException());
        assertEquals(List.of(VOICE), exchange.getMessage().getBody(List.class));
        verify(client).listVoices(request);
    }

    @Test
    void synthesizeWithPojoRequestSendsTheSynthesizeSpeechRequestOfTheBody() {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.newBuilder()
                .setInput(SynthesisInput.newBuilder().setText("Hello"))
                .setVoice(VoiceSelectionParams.newBuilder().setLanguageCode("it-IT"))
                .build();

        Exchange exchange = template.request("google-text-to-speech:synthesize?client=#ttsClient&pojoRequest=true",
                e -> e.getIn().setBody(request));

        assertNull(exchange.getException());
        assertArrayEquals(AUDIO, exchange.getMessage().getBody(byte[].class));
        verify(client).synthesizeSpeech(request);
    }

    @Test
    void listVoicesWithoutTheLanguageCodeOptionListsTheVoicesOfAllLanguages() {
        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient",
                e -> e.getIn().setBody(null));

        assertNull(exchange.getException());
        assertEquals("", sentListVoicesRequest().getLanguageCode());
        assertEquals(List.of(VOICE), exchange.getMessage().getBody(List.class));
    }

    @Test
    void listVoicesWithTheLanguageCodeOptionListsTheVoicesOfThatLanguage() {
        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient&languageCode=fr-FR",
                e -> e.getIn().setBody(null));

        assertNull(exchange.getException());
        assertEquals("fr-FR", sentListVoicesRequest().getLanguageCode());
    }

    @Test
    void listVoicesWithTheDefaultLanguageCodeSetOnTheEndpointFiltersByIt() {
        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient&languageCode=en-US",
                e -> e.getIn().setBody(null));

        assertNull(exchange.getException());
        assertEquals("en-US", sentListVoicesRequest().getLanguageCode());
    }

    @Test
    void listVoicesWithTheLanguageCodeSetOnTheComponentFiltersByIt() {
        context.getComponent("google-text-to-speech", GoogleCloudTextToSpeechComponent.class).getConfiguration()
                .setLanguageCode("de-DE");

        Exchange exchange = template.request("google-text-to-speech:listVoices?client=#ttsClient",
                e -> e.getIn().setBody(null));

        assertNull(exchange.getException());
        assertEquals("de-DE", sentListVoicesRequest().getLanguageCode());
    }

    @Test
    void synthesizeStillUsesTheDefaultLanguageCode() {
        Exchange exchange = template.request("google-text-to-speech:synthesize?client=#ttsClient",
                e -> e.getIn().setBody("Hello"));

        assertNull(exchange.getException());
        ArgumentCaptor<VoiceSelectionParams> voice = ArgumentCaptor.forClass(VoiceSelectionParams.class);
        verify(client).synthesizeSpeech(any(SynthesisInput.class), voice.capture(), any(AudioConfig.class));
        assertEquals("en-US", voice.getValue().getLanguageCode());
        assertArrayEquals(AUDIO, exchange.getMessage().getBody(byte[].class));
    }

    private ListVoicesRequest sentListVoicesRequest() {
        ArgumentCaptor<ListVoicesRequest> request = ArgumentCaptor.forClass(ListVoicesRequest.class);
        verify(client).listVoices(request.capture());
        return request.getValue();
    }
}
