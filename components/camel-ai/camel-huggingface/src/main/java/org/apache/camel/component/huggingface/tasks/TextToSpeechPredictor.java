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

import java.nio.charset.StandardCharsets;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.modality.audio.Audio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceConstants;

/**
 * Predictor for the TEXT_TO_SPEECH task, generating raw audio waveform from text input.
 *
 * <p>
 * This predictor uses Hugging Face's text-to-speech pipeline to synthesize audio from text. It is designed for tasks
 * like voice assistants, audiobooks, or accessibility features.
 * </p>
 *
 * <p>
 * <b>Input Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: The text prompt to convert to speech.</li>
 * </ul>
 *
 * <p>
 * <b>Output Contract (Camel Message Body):</b>
 * </p>
 * <ul>
 * <li>{@code String}: JSON dict with "audio" (list of floats [-1,1]) and "sampling_rate" (int).</li>
 * </ul>
 *
 * <p>
 * <b>Camel Headers Used:</b>
 * </p>
 * <ul>
 * <li>{@code HuggingFaceConstants.OUTPUT}: The same JSON result string (for convenience).</li>
 * </ul>
 *
 * <p>
 * <b>Relevant HuggingFaceConfiguration Properties:</b>
 * </p>
 * <ul>
 * <li>{@code modelId}: Required String, e.g., "facebook/mms-tts-eng".</li>
 * <li>{@code revision}: Optional String, model revision (default "main").</li>
 * <li>{@code device}: Optional String, inference device (default "auto").</li>
 * </ul>
 *
 * <p>
 * <b>Python Model Input/Output Expectations:</b>
 * </p>
 * <ul>
 * <li><b>Input</b>: String text prompt. Models must support TTS pipeline (e.g., MMS-TTS, VITS).</li>
 * <li><b>Output</b>: JSON dict with "audio" (list of floats) and "sampling_rate" (int). Compatible models return this
 * format via HF pipeline.</li>
 * </ul>
 * <p>
 * To ensure model interchangeability, use TTS-tuned models.
 * </p>
 */
public class TextToSpeechPredictor extends AbstractTaskPredictor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public TextToSpeechPredictor(HuggingFaceConfiguration config) {
        super(config);
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
        return """
                from transformers import pipeline
                from djl_python import Input, Output
                import json
                import torch
                import numpy as np
                import logging

                pipe = pipeline(
                    task='text-to-speech',
                    model='%s',
                    revision='%s',
                    device_map='%s'
                )

                def handle(inputs: Input):
                    try:
                        if inputs.content.size() == 0:
                            logging.info("Handling warmup call - returning empty output")
                            return Output()

                        input_str = inputs.get_as_string("data")

                        torch.manual_seed(42)

                        result = pipe(input_str)

                        # Squeeze to 1D and convert to list (handles nested arrays)
                        result['audio'] = np.squeeze(result['audio']).tolist()

                        outputs = Output()
                        outputs.add(json.dumps(result), "data")
                        return outputs

                    except Exception as e:
                        logging.error("Error in handle function: " + str(e))
                        outputs = Output()
                        outputs.add(json.dumps({"error": str(e)}), "data")
                        return outputs
                """.formatted(config.getModelId(), config.getRevision(), config.getDevice());
    }

    @Override
    protected Input prepareInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (!(body instanceof String)) {
            throw new IllegalArgumentException("Body must be String for TEXT_TO_SPEECH");
        }
        Input input = new Input();
        input.add("data", ((String) body).getBytes(StandardCharsets.UTF_8));
        return input;
    }

    @Override
    protected void processOutput(Exchange exchange, Output output) throws Exception {
        String resultJson = output.getAsString("data");

        if (resultJson.contains("\"error\"")) {
            throw new RuntimeCamelException("Python inference failed: " + resultJson);
        }

        // Parse JSON {"audio": [floats...], "sampling_rate": int}
        JsonNode node = objectMapper.readTree(resultJson);
        JsonNode audioNode = node.get("audio");
        int samplingRate = node.get("sampling_rate").asInt();

        // Convert list to float[]
        float[] audioData = objectMapper.convertValue(audioNode, float[].class);

        // Reconstruct DJL Audio (mono, 1 channel)
        Audio audio = new Audio(audioData, samplingRate, 1);

        exchange.getMessage().setBody(audio);
        exchange.getMessage().setHeader("Content-Type", "application/octet-stream");
        exchange.getMessage().setHeader(HuggingFaceConstants.OUTPUT, audio);
    }
}
