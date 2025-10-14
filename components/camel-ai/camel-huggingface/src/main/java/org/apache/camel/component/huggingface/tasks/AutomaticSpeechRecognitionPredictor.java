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
package org.apache.camel.component.huggingface.tasks;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.modality.audio.Audio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConstants;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;

/**
 * Predictor for the AUTOMATIC_SPEECH_RECOGNITION task, transcribing spoken audio to text.
 *
 * <p>
 * This predictor uses Hugging Face's automatic-speech-recognition pipeline to convert audio input into transcribed
 * text. It is designed for tasks like voice-to-text conversion, speech captioning, meeting transcription, or voice
 * command processing.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code ai.djl.modality.audio.Audio}: A DJL Audio object containing the raw audio waveform data and sampling
 * rate.</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The transcribed text (e.g., "Hello world this is a test").</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The same transcribed text string (for convenience).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "facebook/wav2vec2-base-960h" or "openai/whisper-small".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto" — use "cpu" for CPU-only execution).</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: Raw audio bytes (WAV/MP3 supported). Models must support automatic-speech-recognition pipeline
 * (e.g., wav2vec2, Whisper, HuBERT).</li>
 * <li><b>Output</b>: String containing the transcribed text. Compatible models return this format via HF pipeline.</li>
 * </ul>
 *
 * <p>
 * To ensure model interchangeability, use ASR-tuned models. Whisper models generally offer superior performance on
 * synthetic or noisy audio compared to older models like wav2vec2.
 * </p>
 */
public class AutomaticSpeechRecognitionPredictor extends AbstractTaskPredictor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AutomaticSpeechRecognitionPredictor(HuggingFaceEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected String getRequirements() {
        return """
                transformers>=4.30.0
                torch>=2.0.0
                accelerate
                numpy
                """;
    }

    @Override
    protected String getPythonScript() {
        return loadPythonScript("automatic_speech_recognition.py", config.getModelId(), config.getRevision(),
                config.getDevice());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();

        Audio audio;
        if (body instanceof Audio) {
            audio = (Audio) body;
        } else {
            throw new IllegalArgumentException(
                    "Body must be ai.djl.modality.audio.Audio for AUTOMATIC_SPEECH_RECOGNITION");
        }

        float[] waveform = audio.getData();  // Normalized float32 [-1, 1]

        if (waveform == null || waveform.length == 0) {
            throw new IllegalArgumentException("Audio waveform is empty");
        }

        // Serialize waveform as JSON list of floats
        String waveformJson = objectMapper.writeValueAsString(waveform);

        Input input = new Input();
        input.add("data", waveformJson.getBytes("UTF-8"));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String resultJson = output.getAsString("data");

        if (resultJson.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference error: " + resultJson);
        }

        // Extract the transcript string
        JsonNode node = objectMapper.readTree(resultJson);
        String transcript = node.get("text").asText();

        exchange.getMessage().setBody(transcript);
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, transcript);
    }
}
