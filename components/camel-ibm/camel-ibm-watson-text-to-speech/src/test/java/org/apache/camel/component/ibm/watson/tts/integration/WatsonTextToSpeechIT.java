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

package org.apache.camel.component.ibm.watson.tts.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.ibm.watson.text_to_speech.v1.model.Pronunciation;
import com.ibm.watson.text_to_speech.v1.model.Voice;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watson.tts.WatsonTextToSpeechConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Watson Text to Speech operations. These tests require valid IBM Watson credentials to be
 * provided as system properties: - camel.ibm.watson.tts.apiKey - IBM Cloud API key - camel.ibm.watson.tts.serviceUrl -
 * Watson TTS service URL (optional if using default)
 *
 * To run these tests, execute: mvn verify -Dcamel.ibm.watson.tts.apiKey=YOUR_API_KEY
 * -Dcamel.ibm.watson.tts.serviceUrl=YOUR_SERVICE_URL
 */
@EnabledIfSystemProperties({
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.tts.apiKey",
            matches = ".+",
            disabledReason = "IBM Watson TTS API Key not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.tts.serviceUrl",
            matches = ".+",
            disabledReason = "IBM Watson TTS Service URL not provided")
})
public class WatsonTextToSpeechIT extends WatsonTextToSpeechTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonTextToSpeechIT.class);
    private static final String OUTPUT_DIR = "target/audio-output";

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeAll
    public static void setupOutputDirectory() throws Exception {
        // Create output directory for audio files
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            LOG.info("Created output directory: {}", outputPath.toAbsolutePath());
        }
    }

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testSynthesizeDefaultVoice() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Hello, this is a test of IBM Watson Text to Speech.";

        template.sendBody("direct:synthesize", text);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        InputStream audioStream = exchange.getIn().getBody(InputStream.class);

        assertNotNull(audioStream, "Audio stream should not be null");

        // Verify audio stream has content
        byte[] buffer = new byte[1024];
        int bytesRead = audioStream.read(buffer);
        assertTrue(bytesRead > 0, "Audio stream should contain data");

        LOG.info("Successfully synthesized text with default voice. Bytes read: {}", bytesRead);

        // Close the stream
        audioStream.close();
    }

    @Test
    public void testSynthesizeWithCustomVoice() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "Hello from Allison!";
        final String voice = "en-US_AllisonV3Voice";

        template.send("direct:synthesizeCustom", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(text);
                exchange.getIn().setHeader(WatsonTextToSpeechConstants.VOICE, voice);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        InputStream audioStream = exchange.getIn().getBody(InputStream.class);

        assertNotNull(audioStream);

        // Verify header is set
        assertEquals(voice, exchange.getIn().getHeader(WatsonTextToSpeechConstants.VOICE));

        // Verify audio has content
        byte[] buffer = new byte[1024];
        int bytesRead = audioStream.read(buffer);
        assertTrue(bytesRead > 0);

        LOG.info("Successfully synthesized text with custom voice: {}. Bytes read: {}", voice, bytesRead);

        audioStream.close();
    }

    @Test
    public void testSynthesizeWithMP3Format() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "This will be converted to MP3 format.";

        template.send("direct:synthesizeMP3", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(text);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        InputStream audioStream = exchange.getIn().getBody(InputStream.class);

        assertNotNull(audioStream);

        // Verify format header
        assertEquals("audio/mp3", exchange.getIn().getHeader(WatsonTextToSpeechConstants.ACCEPT));

        // Read some data to verify
        byte[] buffer = new byte[1024];
        int bytesRead = audioStream.read(buffer);
        assertTrue(bytesRead > 0);

        LOG.info("Successfully synthesized text in MP3 format. Bytes read: {}", bytesRead);

        audioStream.close();
    }

    @Test
    public void testListVoices() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listVoices", "");

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<Voice> voices = exchange.getIn().getBody(List.class);

        assertNotNull(voices, "Voices list should not be null");
        assertFalse(voices.isEmpty(), "Should return at least one voice");

        LOG.info("Found {} voices", voices.size());

        // Log first few voices for verification
        voices.stream().limit(5).forEach(voice -> {
            LOG.info(
                    "  Voice: {} - Language: {} - Gender: {}", voice.getName(), voice.getLanguage(), voice.getGender());
        });

        // Verify some expected voices exist
        boolean hasEnglishVoice = voices.stream()
                .anyMatch(v -> v.getLanguage() != null && v.getLanguage().startsWith("en"));
        assertTrue(hasEnglishVoice, "Should have at least one English voice");
    }

    @Test
    public void testGetVoice() throws Exception {
        mockResult.expectedMessageCount(1);

        final String voiceName = "en-US_MichaelV3Voice";

        template.send("direct:getVoice", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonTextToSpeechConstants.VOICE_NAME, voiceName);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Voice voice = exchange.getIn().getBody(Voice.class);

        assertNotNull(voice, "Voice should not be null");
        assertEquals(voiceName, voice.getName(), "Voice name should match");
        assertNotNull(voice.getLanguage(), "Voice should have a language");
        assertNotNull(voice.getGender(), "Voice should have a gender");

        LOG.info(
                "Retrieved voice: {} - Language: {} - Gender: {} - Description: {}",
                voice.getName(),
                voice.getLanguage(),
                voice.getGender(),
                voice.getDescription());
    }

    @Test
    public void testGetPronunciation() throws Exception {
        mockResult.expectedMessageCount(1);

        final String word = "synthesize";

        template.send("direct:getPronunciation", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonTextToSpeechConstants.WORD, word);
                exchange.getIn().setHeader(WatsonTextToSpeechConstants.FORMAT, "ipa");
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Pronunciation pronunciation = exchange.getIn().getBody(Pronunciation.class);

        assertNotNull(pronunciation, "Pronunciation should not be null");
        assertNotNull(pronunciation.getPronunciation(), "Pronunciation text should not be null");

        LOG.info("Pronunciation of '{}': {}", word, pronunciation.getPronunciation());
    }

    @Test
    public void testSynthesizeWithDifferentLanguages() throws Exception {
        // Test French
        mockResult.expectedMessageCount(1);

        template.send("direct:synthesizeFrench", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody("Bonjour, comment allez-vous?");
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        InputStream audioStream = exchange.getIn().getBody(InputStream.class);
        assertNotNull(audioStream);

        byte[] buffer = new byte[1024];
        int bytesRead = audioStream.read(buffer);
        assertTrue(bytesRead > 0);

        LOG.info("Successfully synthesized French text. Bytes read: {}", bytesRead);

        audioStream.close();
    }

    @Test
    public void testSynthesizeLongerText() throws Exception {
        mockResult.expectedMessageCount(1);

        final String longText = "IBM Watson Text to Speech is a cloud-based service that converts written text "
                + "into natural-sounding speech. The service supports multiple languages and voices, "
                + "allowing you to create engaging audio experiences for your applications. "
                + "You can customize the pronunciation, add pauses, and control the speaking rate.";

        template.sendBody("direct:synthesize", longText);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        InputStream audioStream = exchange.getIn().getBody(InputStream.class);

        assertNotNull(audioStream);

        // Read more data for longer text
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = audioStream.read(buffer)) != -1) {
            totalBytes += bytesRead;
        }

        assertTrue(totalBytes > 1000, "Longer text should produce more audio data");

        LOG.info("Successfully synthesized longer text. Total bytes: {}", totalBytes);

        audioStream.close();
    }

    @Test
    public void testSynthesizeAndSaveToMP3File() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "This is a test of IBM Watson Text to Speech being saved to an MP3 file. "
                + "The audio quality should be excellent and the file should be playable.";
        final String filename = "test-output.mp3";

        template.send("direct:synthesizeToMP3File", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(text);
                exchange.getIn().setHeader(Exchange.FILE_NAME, filename);
            }
        });

        mockResult.assertIsSatisfied();

        // Verify file was created
        File outputFile = new File(OUTPUT_DIR, filename);
        assertTrue(outputFile.exists(), "MP3 file should be created");
        assertTrue(outputFile.length() > 0, "MP3 file should have content");

        LOG.info("Successfully saved MP3 file: {} (size: {} bytes)", outputFile.getAbsolutePath(), outputFile.length());

        // Verify it's a valid MP3 by checking first few bytes (MP3 ID3 tag or frame sync)
        byte[] header = Files.readAllBytes(outputFile.toPath());
        assertTrue(header.length > 10, "File should have sufficient data");
        // MP3 files typically start with ID3 tag (0x49 0x44 0x33) or frame sync (0xFF 0xFB)
        boolean isValidMP3 = (header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33) // ID3
                || (header[0] == (byte) 0xFF && (header[1] & 0xE0) == 0xE0); // Frame sync

        assertTrue(isValidMP3, "File should have valid MP3 header");
    }

    @Test
    public void testSynthesizeAndSaveToWAVFile() throws Exception {
        mockResult.expectedMessageCount(1);

        final String text = "This is a test of saving audio as a WAV file. "
                + "WAV is an uncompressed audio format with excellent quality.";
        final String filename = "test-output.wav";

        template.send("direct:synthesizeToWAVFile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(text);
                exchange.getIn().setHeader(Exchange.FILE_NAME, filename);
            }
        });

        mockResult.assertIsSatisfied();

        // Verify file was created
        File outputFile = new File(OUTPUT_DIR, filename);
        assertTrue(outputFile.exists(), "WAV file should be created");
        assertTrue(outputFile.length() > 0, "WAV file should have content");

        LOG.info("Successfully saved WAV file: {} (size: {} bytes)", outputFile.getAbsolutePath(), outputFile.length());

        // Verify it's a valid WAV by checking RIFF header
        byte[] header = new byte[12];
        Files.newInputStream(outputFile.toPath()).read(header);

        // WAV files start with "RIFF" followed by file size, then "WAVE"
        boolean isValidWAV = header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'A'
                && header[10] == 'V'
                && header[11] == 'E';

        assertTrue(isValidWAV, "File should have valid WAV/RIFF header");
    }

    @Test
    public void testSynthesizeMultipleVoicesToFiles() throws Exception {
        mockResult.expectedMinimumMessageCount(3);

        // Test multiple voices and save each to a file
        String[][] voiceTests = {
            {"en-US_MichaelV3Voice", "Hello from Michael!", "michael.mp3"},
            {"en-US_AllisonV3Voice", "Hello from Allison!", "allison.mp3"},
            {"en-GB_KateV3Voice", "Hello from Kate!", "kate.mp3"}
        };

        for (String[] voiceTest : voiceTests) {
            final String voice = voiceTest[0];
            final String text = voiceTest[1];
            final String filename = voiceTest[2];

            template.send("direct:synthesizeToMP3File", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setBody(text);
                    exchange.getIn().setHeader(WatsonTextToSpeechConstants.VOICE, voice);
                    exchange.getIn().setHeader(Exchange.FILE_NAME, filename);
                }
            });

            // Verify file was created
            File outputFile = new File(OUTPUT_DIR, filename);
            assertTrue(outputFile.exists(), "File should be created for voice: " + voice);
            assertTrue(outputFile.length() > 0, "File should have content for voice: " + voice);

            LOG.info("Created audio file for {}: {} ({} bytes)", voice, filename, outputFile.length());
        }

        mockResult.assertIsSatisfied();
    }

    @Test
    public void testSynthesizeMultipleLanguagesToFiles() throws Exception {
        mockResult.expectedMinimumMessageCount(4);

        // Test multiple languages
        String[][] languageTests = {
            {"en-US_MichaelV3Voice", "Hello, this is a test in English.", "english.mp3"},
            {"es-ES_EnriqueV3Voice", "Hola, esta es una prueba en español.", "spanish.mp3"},
            {"fr-FR_NicolasV3Voice", "Bonjour, ceci est un test en français.", "french.mp3"},
            {"de-DE_DieterV3Voice", "Hallo, das ist ein Test auf Deutsch.", "german.mp3"}
        };

        for (String[] languageTest : languageTests) {
            final String voice = languageTest[0];
            final String text = languageTest[1];
            final String filename = languageTest[2];

            template.send("direct:synthesizeToMP3File", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setBody(text);
                    exchange.getIn().setHeader(WatsonTextToSpeechConstants.VOICE, voice);
                    exchange.getIn().setHeader(Exchange.FILE_NAME, filename);
                }
            });

            File outputFile = new File(OUTPUT_DIR, filename);
            assertTrue(outputFile.exists(), "File should be created for: " + filename);

            LOG.info("Created multilingual audio file: {} ({} bytes)", filename, outputFile.length());
        }

        mockResult.assertIsSatisfied();
        LOG.info("Successfully created audio files in 4 different languages");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:synthesize").to(buildEndpointUri("synthesize")).to("mock:result");

                from("direct:synthesizeCustom")
                        .to(buildEndpointUri("synthesize"))
                        .to("mock:result");

                from("direct:synthesizeMP3")
                        .to(buildEndpointUri("synthesize") + "&accept=audio/mp3")
                        .to("mock:result");

                from("direct:synthesizeFrench")
                        .to(buildEndpointUri("synthesize") + "&voice=fr-FR_NicolasV3Voice")
                        .to("mock:result");

                from("direct:listVoices").to(buildEndpointUri("listVoices")).to("mock:result");

                from("direct:getVoice").to(buildEndpointUri("getVoice")).to("mock:result");

                from("direct:getPronunciation")
                        .to(buildEndpointUri("getPronunciation"))
                        .to("mock:result");

                // Routes that save synthesized audio to files
                from("direct:synthesizeToMP3File")
                        .to(buildEndpointUri("synthesize") + "&accept=audio/mp3")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:synthesizeToWAVFile")
                        .to(buildEndpointUri("synthesize") + "&accept=audio/wav")
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");
            }
        };
    }
}
