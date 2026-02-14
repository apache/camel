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

/**
 * Enum representing the supported Hugging Face tasks for the camel-huggingface component.
 *
 * <p>
 * This enum defines the tasks that can be specified in the component URI (e.g., "huggingface:text-classification").
 * Each task corresponds to a Hugging Face pipeline or model type, and is associated with a specific predictor class for
 * handling input/output. For built-in tasks, the component automatically selects the predictor. For custom tasks not
 * listed here, provide a predictorBean in the configuration (e.g., a bean implementing {@link TaskPredictor}).
 * </p>
 *
 * <p>
 * Custom tasks allow users to extend the component with unsupported Hugging Face pipelines or models by implementing a
 * custom {@link TaskPredictor}) and registering it as a bean in the Camel registry.
 * </p>
 *
 * <p>
 * See the documentation for each task for model compatibility and examples.
 * </p>
 */
public enum HuggingFaceTask {
    /**
     * Text classification task (e.g., sentiment analysis). Supported models: Classification-tuned models like
     * distilbert-base-uncased-finetuned-sst-2-english.
     */
    TEXT_CLASSIFICATION(TextClassificationPredictor.class),
    /**
     * Text generation task (e.g., completion or creative writing). Supported models: Generative models like gpt2 or
     * mistralai/Mistral-7B-Instruct-v0.2.
     */
    TEXT_GENERATION(TextGenerationPredictor.class),
    /**
     * Question answering task (extractive QA from context). Supported models: QA-tuned models like
     * distilbert-base-cased-distilled-squad.
     */
    QUESTION_ANSWERING(QuestionAnsweringPredictor.class),
    /**
     * Summarization task (condensing text). Supported models: Summarization-tuned models like facebook/bart-large-cnn.
     */
    SUMMARIZATION(SummarizationPredictor.class),
    /**
     * Zero-shot classification task (classify with arbitrary labels). Supported models: NLI-based models like
     * facebook/bart-large-mnli.
     */
    ZERO_SHOT_CLASSIFICATION(ZeroShotClassificationPredictor.class),
    /**
     * Sentence embeddings task (text embeddings). Supported models: Embedding-tuned models like
     * sentence-transformers/all-MiniLM-L6-v2.
     */
    SENTENCE_EMBEDDINGS(SentenceEmbeddingsPredictor.class),
    /**
     * Text-to-image generation task. Supported models: Diffusion-based models like CompVis/stable-diffusion-v1-4.
     */
    TEXT_TO_IMAGE(TextToImagePredictor.class),
    /**
     * Automatic speech recognition task (speech-to-text). Supported models: ASR-tuned models like
     * facebook/wav2vec2-base-960h or openai/whisper-small.
     */
    AUTOMATIC_SPEECH_RECOGNITION(AutomaticSpeechRecognitionPredictor.class),
    /**
     * Text-to-speech task (text-to-audio). Supported models: TTS-tuned models like facebook/mms-tts-eng.
     */
    TEXT_TO_SPEECH(TextToSpeechPredictor.class),
    /**
     * Chat task (conversational text generation). Supported models: Instruct-tuned models like
     * mistralai/Mistral-7B-Instruct-v0.2.
     */
    CHAT(ChatPredictor.class);

    private final Class<? extends TaskPredictor> predictorClass;

    HuggingFaceTask(Class<? extends TaskPredictor> predictorClass) {
        this.predictorClass = predictorClass;
    }

    public Class<? extends TaskPredictor> getPredictorClass() {
        return predictorClass;
    }
}
