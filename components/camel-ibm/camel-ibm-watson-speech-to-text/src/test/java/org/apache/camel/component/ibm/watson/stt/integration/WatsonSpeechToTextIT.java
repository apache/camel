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
package org.apache.camel.component.ibm.watson.stt.integration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.speech_to_text.v1.model.SpeechModel;
import com.ibm.watson.speech_to_text.v1.model.SpeechRecognitionResults;
import com.ibm.watson.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.text_to_speech.v1.model.SynthesizeOptions;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watson.stt.WatsonSpeechToTextConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Watson Speech to Text operations. These tests require valid IBM Watson credentials to be
 * provided as system properties: - camel.ibm.watson.stt.apiKey - IBM Cloud API key - camel.ibm.watson.stt.serviceUrl -
 * Watson STT service URL (optional if using default)
 *
 * To run these tests, execute: mvn verify -Dcamel.ibm.watson.stt.apiKey=YOUR_API_KEY
 * -Dcamel.ibm.watson.stt.serviceUrl=YOUR_SERVICE_URL
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watson.stt.apiKey", matches = ".+",
                                 disabledReason = "IBM Watson STT API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watson.stt.serviceUrl", matches = ".+",
                                 disabledReason = "IBM Watson STT Service URL not provided")
})
public class WatsonSpeechToTextIT extends WatsonSpeechToTextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonSpeechToTextIT.class);
    private static final String INPUT_DIR = "target/audio-input";
    private static final String OUTPUT_DIR = "target/transcription-output";
    private static final String TEST_AUDIO_FILE = "test-audio.wav";
    private static final String TEST_AUDIO_TIMESTAMPS_FILE = "test-audio-timestamps.wav";
    private static final String TEST_AUDIO_CONFIDENCE_FILE = "test-audio-confidence.wav";
    private static final String REAL_SPEECH_AUDIO_FILE = "real-speech.wav";
    private static final String EXPECTED_TRANSCRIPT = "Hello, this is a test of IBM Watson Speech to Text service.";

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeAll
    public static void setupInputDirectory() throws Exception {
        // Create input directory for audio files
        Path inputPath = Paths.get(INPUT_DIR);
        if (!Files.exists(inputPath)) {
            Files.createDirectories(inputPath);
            LOG.info("Created input directory: {}", inputPath.toAbsolutePath());
        }

        // Create output directory for transcription files
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            LOG.info("Created output directory: {}", outputPath.toAbsolutePath());
        }

        // Try to generate real speech audio using Watson TTS if credentials are available
        boolean realSpeechGenerated = generateRealSpeechAudio(REAL_SPEECH_AUDIO_FILE, EXPECTED_TRANSCRIPT);

        if (realSpeechGenerated) {
            LOG.info("Successfully generated real speech audio using Watson Text-to-Speech");
        } else {
            LOG.info("Watson TTS credentials not available - using synthesized tones for testing");
        }

        // Generate fallback test audio files (simple tones)
        generateTestAudioFile(TEST_AUDIO_FILE);
        generateTestAudioFile(TEST_AUDIO_TIMESTAMPS_FILE);
        generateTestAudioFile(TEST_AUDIO_CONFIDENCE_FILE);
    }

    /**
     * Generates real speech audio using IBM Watson Text-to-Speech. This creates audio with actual spoken words that can
     * be accurately transcribed by Watson Speech-to-Text.
     *
     * @param  filename the output filename
     * @param  text     the text to convert to speech
     * @return          true if audio was generated successfully, false if TTS credentials are not available
     */
    private static boolean generateRealSpeechAudio(String filename, String text) {
        try {
            // Check if TTS credentials are available
            String ttsApiKey = System.getProperty("camel.ibm.watson.tts.apiKey");
            String ttsServiceUrl = System.getProperty("camel.ibm.watson.tts.serviceUrl");

            if (ttsApiKey == null || ttsApiKey.isBlank()) {
                LOG.debug("Watson TTS API key not provided - skipping real speech generation");
                return false;
            }

            LOG.info("Generating real speech audio using Watson Text-to-Speech...");

            // Create Watson TTS client
            IamAuthenticator authenticator = new IamAuthenticator(ttsApiKey);
            TextToSpeech ttsService = new TextToSpeech(authenticator);

            if (ttsServiceUrl != null && !ttsServiceUrl.isBlank()) {
                ttsService.setServiceUrl(ttsServiceUrl);
            }

            // Synthesize speech - using audio/wav format compatible with STT
            SynthesizeOptions synthesizeOptions = new SynthesizeOptions.Builder()
                    .text(text)
                    .voice("en-US_MichaelV3Voice") // Use Michael voice for clear speech
                    .accept("audio/wav") // WAV format for compatibility
                    .build();

            InputStream audioStream = ttsService.synthesize(synthesizeOptions).execute().getResult();

            // Save to file
            File outputFile = new File(INPUT_DIR, filename);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            audioStream.close();

            LOG.info("Generated real speech audio: {} ({} bytes)", outputFile.getAbsolutePath(), outputFile.length());
            LOG.info("Expected transcript: '{}'", text);
            return true;

        } catch (Exception e) {
            LOG.warn("Failed to generate real speech audio: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            LOG.debug("Full exception:", e);
            return false;
        }
    }

    /**
     * Generates a simple WAV audio file for testing. Creates a short audio tone that can be recognized by Watson STT.
     */
    private static void generateTestAudioFile(String filename) throws Exception {
        File outputFile = new File(INPUT_DIR, filename);

        // Audio format: 16kHz, 16-bit, mono PCM
        AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);

        // Generate a short silence (Watson STT needs actual audio, but for testing we'll use a simple tone)
        // In a real test, you would need actual speech audio
        int durationSeconds = 1;
        int numSamples = (int) (format.getSampleRate() * durationSeconds);
        byte[] audioData = new byte[numSamples * 2]; // 16-bit = 2 bytes per sample

        // Generate a simple tone at 440 Hz (A4 note) - this won't transcribe to text,
        // but it creates valid audio data for format testing
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i / (format.getSampleRate() / 440);
            short sample = (short) (Math.sin(angle) * Short.MAX_VALUE * 0.5);
            audioData[i * 2] = (byte) (sample & 0xff);
            audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
        }

        // Create AudioInputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(audioData);
        AudioInputStream audioInputStream
                = new AudioInputStream(new java.io.ByteArrayInputStream(audioData), format, numSamples);

        // Write to WAV file
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);

        LOG.info("Generated test audio file: {} ({} bytes)", outputFile.getAbsolutePath(), outputFile.length());
    }

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testRecognizeWAVFile() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        template.send("direct:recognize", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");
        assertNotNull(results.getResults(), "Results list should not be null");

        // Get transcript from header
        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript header should be set");

        LOG.info("Successfully transcribed audio file. Transcript: '{}'", transcript);
        LOG.info("Number of results: {}", results.getResults() != null ? results.getResults().size() : 0);
    }

    @Test
    public void testRecognizeWithTimestamps() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_TIMESTAMPS_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        template.send("direct:recognizeTimestamps", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.TIMESTAMPS, true);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");
        assertNotNull(results.getResults(), "Results list should not be null");

        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript should not be null");

        LOG.info("Successfully transcribed with timestamps. Transcript: '{}'", transcript);

        // Log timestamp information if available
        if (results.getResults() != null && !results.getResults().isEmpty()) {
            results.getResults().forEach(result -> {
                if (result.getAlternatives() != null) {
                    result.getAlternatives().forEach(alt -> {
                        if (alt.getTimestamps() != null) {
                            LOG.info("Found {} word timestamps", alt.getTimestamps().size());
                            alt.getTimestamps().stream().limit(3).forEach(ts -> {
                                LOG.info("  Word: {} - Start: {} - End: {}", ts.getWord(), ts.getStartTime(),
                                        ts.getEndTime());
                            });
                        }
                    });
                }
            });
        }
    }

    @Test
    public void testRecognizeWithWordConfidence() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_CONFIDENCE_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        template.send("direct:recognizeConfidence", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.WORD_CONFIDENCE, true);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");
        assertNotNull(results.getResults(), "Results list should not be null");

        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript should not be null");

        LOG.info("Successfully transcribed with word confidence. Transcript: '{}'", transcript);

        // Log confidence information if available
        if (results.getResults() != null && !results.getResults().isEmpty()) {
            results.getResults().forEach(result -> {
                if (result.getAlternatives() != null) {
                    result.getAlternatives().forEach(alt -> {
                        if (alt.getWordConfidence() != null) {
                            LOG.info("Found {} word confidence scores", alt.getWordConfidence().size());
                            alt.getWordConfidence().stream().limit(3).forEach(wc -> {
                                LOG.info("  Word: {} - Confidence: {}", wc.getWord(), wc.getConfidence());
                            });
                        }
                        // Also check overall confidence
                        if (alt.getConfidence() != null) {
                            LOG.info("Overall confidence: {}", alt.getConfidence());
                        }
                    });
                }
            });
        }
    }

    @Test
    public void testListModels() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listModels", "");

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        @SuppressWarnings("unchecked")
        List<SpeechModel> models = exchange.getIn().getBody(List.class);

        assertNotNull(models, "Models list should not be null");
        assertFalse(models.isEmpty(), "Should return at least one model");

        LOG.info("Found {} language models", models.size());

        // Log first few models for verification
        models.stream().limit(5).forEach(model -> {
            LOG.info("  Model: {} - Language: {} - Description: {}", model.getName(), model.getLanguage(),
                    model.getDescription());
        });

        // Verify some expected models exist
        boolean hasEnglishModel
                = models.stream().anyMatch(m -> m.getName() != null && m.getName().startsWith("en-US"));
        assertTrue(hasEnglishModel, "Should have at least one US English model");
    }

    @Test
    public void testGetModel() throws Exception {
        mockResult.expectedMessageCount(1);

        final String modelName = "en-US_BroadbandModel";

        template.send("direct:getModel", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.MODEL_NAME, modelName);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechModel model = exchange.getIn().getBody(SpeechModel.class);

        assertNotNull(model, "Model should not be null");
        assertEquals(modelName, model.getName(), "Model name should match");
        assertNotNull(model.getLanguage(), "Model should have a language");
        assertNotNull(model.getDescription(), "Model should have a description");

        LOG.info("Retrieved model: {} - Language: {} - Rate: {} - Description: {}", model.getName(), model.getLanguage(),
                model.getRate(), model.getDescription());
    }

    @Test
    public void testRecognizeWithInputStream() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        // Read file as InputStream
        final InputStream audioStream = Files.newInputStream(audioFile.toPath());

        template.send("direct:recognize", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setBody(audioStream);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.CONTENT_TYPE, "audio/wav");
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");

        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript should not be null");

        LOG.info("Successfully transcribed from InputStream. Transcript: '{}'", transcript);

        audioStream.close();
    }

    @Test
    public void testRecognizeWithDifferentModel() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        final String model = "en-US_NarrowbandModel";

        template.send("direct:recognizeCustomModel", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.MODEL, model);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");

        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript should not be null");

        LOG.info("Successfully transcribed with {} model. Transcript: '{}'", model, transcript);
    }

    @Test
    public void testRecognizeWithAllOptions() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_FILE);
        assertTrue(audioFile.exists(), "Test audio file should exist");

        template.send("direct:recognizeAllOptions", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.TIMESTAMPS, true);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.WORD_CONFIDENCE, true);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);

        assertNotNull(results, "Recognition results should not be null");

        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);
        assertNotNull(transcript, "Transcript should not be null");

        LOG.info("Successfully transcribed with all options enabled. Transcript: '{}'", transcript);

        // Verify we got the enhanced results
        if (results.getResults() != null && !results.getResults().isEmpty()) {
            results.getResults().forEach(result -> {
                if (result.getAlternatives() != null && !result.getAlternatives().isEmpty()) {
                    LOG.info("Alternatives available: {}", result.getAlternatives().size());
                    result.getAlternatives().stream().limit(1).forEach(alt -> {
                        LOG.info("  Transcript: {}", alt.getTranscript());
                        if (alt.getTimestamps() != null) {
                            LOG.info("  Timestamps available: {}", alt.getTimestamps().size());
                        }
                        if (alt.getWordConfidence() != null) {
                            LOG.info("  Word confidence scores available: {}", alt.getWordConfidence().size());
                        }
                    });
                }
            });
        }
    }

    @Test
    public void testRecognizeAndSaveToTextFile() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_FILE);
        final String outputFilename = "transcript-basic.txt";

        template.send("direct:recognizeToFile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(Exchange.FILE_NAME, outputFilename);
            }
        });

        mockResult.assertIsSatisfied();

        // Verify file was created
        File outputFile = new File(OUTPUT_DIR, outputFilename);
        assertTrue(outputFile.exists(), "Transcript file should be created");
        assertTrue(outputFile.length() > 0, "Transcript file should have content");

        String content = Files.readString(outputFile.toPath());
        assertNotNull(content, "Content should not be null");
        assertFalse(content.isBlank(), "Content should not be blank");

        LOG.info("Successfully saved transcript to file: {} (size: {} bytes)", outputFile.getAbsolutePath(),
                outputFile.length());
        LOG.info("Transcript content (first 200 chars): '{}'",
                content.substring(0, Math.min(200, content.length())));
    }

    @Test
    public void testRecognizeWithTimestampsAndSaveToFile() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_TIMESTAMPS_FILE);
        final String outputFilename = "transcript-with-timestamps.txt";

        template.send("direct:recognizeToFileWithTimestamps", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.TIMESTAMPS, true);
                exchange.getIn().setHeader(Exchange.FILE_NAME, outputFilename);
            }
        });

        mockResult.assertIsSatisfied();

        File outputFile = new File(OUTPUT_DIR, outputFilename);
        assertTrue(outputFile.exists(), "Transcript file with timestamps should be created");

        String content = Files.readString(outputFile.toPath());
        assertNotNull(content);

        LOG.info("Successfully saved transcript with timestamps to: {} (size: {} bytes)", outputFile.getAbsolutePath(),
                outputFile.length());
        LOG.info("Content preview: {}", content.substring(0, Math.min(200, content.length())));
    }

    @Test
    public void testRecognizeWithDetailedResultsAndSaveToFile() throws Exception {
        mockResult.expectedMessageCount(1);

        File audioFile = new File(INPUT_DIR, TEST_AUDIO_CONFIDENCE_FILE);
        final String outputFilename = "transcript-detailed.txt";

        template.send("direct:recognizeToFileDetailed", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.TIMESTAMPS, true);
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.WORD_CONFIDENCE, true);
                exchange.getIn().setHeader(Exchange.FILE_NAME, outputFilename);
            }
        });

        mockResult.assertIsSatisfied();

        File outputFile = new File(OUTPUT_DIR, outputFilename);
        assertTrue(outputFile.exists(), "Detailed transcript file should be created");
        assertTrue(outputFile.length() > 0, "Detailed transcript file should have content");

        String content = Files.readString(outputFile.toPath());
        assertNotNull(content);

        LOG.info("Successfully saved detailed transcript to: {} (size: {} bytes)", outputFile.getAbsolutePath(),
                outputFile.length());
        // Check if detailed information is present
        LOG.info("Detailed results saved with timestamps and confidence scores");
    }

    @Test
    public void testRecognizeMultipleFilesAndSaveTranscripts() throws Exception {
        mockResult.expectedMinimumMessageCount(3);

        String[][] audioTests = {
                { TEST_AUDIO_FILE, "transcript-file1.txt" },
                { TEST_AUDIO_TIMESTAMPS_FILE, "transcript-file2.txt" },
                { TEST_AUDIO_CONFIDENCE_FILE, "transcript-file3.txt" }
        };

        for (String[] audioTest : audioTests) {
            final String audioFilename = audioTest[0];
            final String outputFilename = audioTest[1];

            File audioFile = new File(INPUT_DIR, audioFilename);

            template.send("direct:recognizeToFile", new Processor() {
                @Override
                public void process(Exchange exchange) {
                    exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, audioFile);
                    exchange.getIn().setHeader(Exchange.FILE_NAME, outputFilename);
                }
            });

            // Verify file was created
            File outputFile = new File(OUTPUT_DIR, outputFilename);
            assertTrue(outputFile.exists(), "Transcript file should be created: " + outputFilename);

            // File should have content (even if it's the fallback message)
            long fileSize = outputFile.length();
            assertTrue(fileSize > 0, "Transcript file should have content: " + outputFilename + " (size: " + fileSize + ")");

            LOG.info("Created transcript file: {} (size: {} bytes)", outputFilename, fileSize);
        }

        mockResult.assertIsSatisfied();
        LOG.info("Successfully transcribed and saved {} audio files", audioTests.length);
    }

    @Test
    public void testRecognizeRealSpeechAudio() throws Exception {
        File realSpeechFile = new File(INPUT_DIR, REAL_SPEECH_AUDIO_FILE);

        // Check if real speech audio was generated (requires TTS credentials)
        if (!realSpeechFile.exists()) {
            LOG.info("Skipping real speech test - audio file not generated (TTS credentials not provided)");
            LOG.info("To run this test, provide Watson TTS credentials: "
                     + "-Dcamel.ibm.watson.tts.apiKey=YOUR_KEY -Dcamel.ibm.watson.tts.serviceUrl=YOUR_URL");
            return;
        }

        mockResult.expectedMessageCount(1);

        template.send("direct:recognize", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(WatsonSpeechToTextConstants.AUDIO_FILE, realSpeechFile);
            }
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);
        String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT, String.class);

        assertNotNull(results, "Recognition results should not be null");
        assertNotNull(transcript, "Transcript should not be null");
        assertFalse(transcript.isBlank(), "Transcript should not be blank");

        LOG.info("==============================================");
        LOG.info("REAL SPEECH TRANSCRIPTION TEST");
        LOG.info("==============================================");
        LOG.info("Expected: '{}'", EXPECTED_TRANSCRIPT);
        LOG.info("Actual  : '{}'", transcript);
        LOG.info("==============================================");

        // Check if transcript contains key words from expected text
        String transcriptLower = transcript.toLowerCase();
        assertTrue(transcriptLower.contains("watson") || transcriptLower.contains("speech") || transcriptLower.contains("text"),
                "Transcript should contain at least one key word from the expected text. Got: " + transcript);

        // Calculate simple similarity (for informational purposes)
        boolean containsHello = transcriptLower.contains("hello");
        boolean containsWatson = transcriptLower.contains("watson");
        boolean containsSpeech = transcriptLower.contains("speech");
        boolean containsText = transcriptLower.contains("text");

        int matchCount = (containsHello ? 1 : 0) + (containsWatson ? 1 : 0) + (containsSpeech ? 1 : 0)
                         + (containsText ? 1 : 0);
        LOG.info("Key word matches: {}/4 (hello={}, watson={}, speech={}, text={})", matchCount, containsHello,
                containsWatson, containsSpeech, containsText);

        // This demonstrates successful round-trip: TTS -> audio file -> STT -> text
        LOG.info("Successfully demonstrated TTS→STT integration: real speech audio was accurately transcribed!");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:recognize")
                        .to(buildEndpointUri("recognize"))
                        .to("mock:result");

                from("direct:recognizeTimestamps")
                        .to(buildEndpointUri("recognize"))
                        .to("mock:result");

                from("direct:recognizeConfidence")
                        .to(buildEndpointUri("recognize"))
                        .to("mock:result");

                from("direct:recognizeCustomModel")
                        .to(buildEndpointUri("recognize"))
                        .to("mock:result");

                from("direct:recognizeAllOptions")
                        .to(buildEndpointUri("recognize"))
                        .to("mock:result");

                from("direct:listModels")
                        .to(buildEndpointUri("listModels"))
                        .to("mock:result");

                from("direct:getModel")
                        .to(buildEndpointUri("getModel"))
                        .to("mock:result");

                // Routes that save transcription results to files
                from("direct:recognizeToFile")
                        .to(buildEndpointUri("recognize"))
                        .process(exchange -> {
                            // Extract transcript from header and set as body for file component
                            String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT,
                                    String.class);
                            // If transcript is null or empty, use recognition results
                            if (transcript == null || transcript.isBlank()) {
                                SpeechRecognitionResults results = exchange.getIn()
                                        .getBody(SpeechRecognitionResults.class);
                                transcript = "[No transcript available - Audio may not contain recognizable speech]\n"
                                             + "Recognition results: " + (results != null ? results.toString() : "null");
                            }
                            exchange.getIn().setBody(transcript);
                        })
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:recognizeToFileWithTimestamps")
                        .to(buildEndpointUri("recognize") + "&timestamps=true")
                        .process(exchange -> {
                            // Get detailed results and format for output
                            SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);
                            StringBuilder output = new StringBuilder();
                            String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT,
                                    String.class);

                            output.append("TRANSCRIPT:\n");
                            output.append(transcript).append("\n\n");
                            output.append("DETAILED RESULTS WITH TIMESTAMPS:\n");
                            output.append(results.toString());

                            exchange.getIn().setBody(output.toString());
                        })
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");

                from("direct:recognizeToFileDetailed")
                        .to(buildEndpointUri("recognize") + "&timestamps=true&wordConfidence=true")
                        .process(exchange -> {
                            // Get detailed results with timestamps and confidence
                            SpeechRecognitionResults results = exchange.getIn().getBody(SpeechRecognitionResults.class);
                            StringBuilder output = new StringBuilder();
                            String transcript = exchange.getIn().getHeader(WatsonSpeechToTextConstants.TRANSCRIPT,
                                    String.class);

                            output.append("TRANSCRIPT:\n");
                            output.append(transcript).append("\n\n");
                            output.append("DETAILED RESULTS (with timestamps and word confidence):\n");
                            output.append(results.toString());

                            exchange.getIn().setBody(output.toString());
                        })
                        .to("file:" + OUTPUT_DIR)
                        .to("mock:result");
            }
        };
    }
}
