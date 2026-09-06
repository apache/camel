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
package org.apache.camel.component.openai;

import org.apache.camel.spi.Metadata;

/**
 * Constants for OpenAI component headers and properties.
 */
public final class OpenAIConstants {

    // Input Headers
    @Metadata(description = "The user message to send to the OpenAI chat completion API", javaType = "String")
    public static final String USER_MESSAGE = "CamelOpenAIUserMessage";
    @Metadata(description = "The system message to provide context and instructions to the model", javaType = "String")
    public static final String SYSTEM_MESSAGE = "CamelOpenAISystemMessage";
    @Metadata(description = "The developer message to provide additional instructions to the model", javaType = "String")
    public static final String DEVELOPER_MESSAGE = "CamelOpenAIDeveloperMessage";
    @Metadata(description = "The model to use for chat completion", javaType = "String")
    public static final String MODEL = "CamelOpenAIModel";
    @Metadata(description = "Controls randomness in the response. Higher values (e.g., 0.8) make output more random, lower values (e.g., 0.2) make it more deterministic",
              javaType = "Double")
    public static final String TEMPERATURE = "CamelOpenAITemperature";
    @Metadata(description = "An alternative to temperature for controlling randomness. Uses nucleus sampling where the model considers tokens with top_p probability mass",
              javaType = "Double")
    public static final String TOP_P = "CamelOpenAITopP";
    @Metadata(description = "The maximum number of tokens to generate in the completion", javaType = "Integer")
    public static final String MAX_TOKENS = "CamelOpenAIMaxTokens";
    @Metadata(description = "Previous response id for server-side conversation state on the Responses API", javaType = "String")
    public static final String PREVIOUS_RESPONSE_ID = "CamelOpenAIPreviousResponseId";
    @Metadata(description = "Whether to stream the response back incrementally", javaType = "Boolean")
    public static final String STREAMING = "CamelOpenAIStreaming";
    @Metadata(description = "The Java class name (FQCN) to use for structured output parsing", javaType = "String")
    public static final String OUTPUT_CLASS = "CamelOpenAIOutputClass";
    @Metadata(description = "The JSON schema to use for structured output validation", javaType = "String")
    public static final String JSON_SCHEMA = "CamelOpenAIJsonSchema";
    @Metadata(description = "Whether to strip <think>...</think> blocks from the response body", javaType = "Boolean")
    public static final String STRIP_THINKING = "CamelOpenAIStripThinking";
    @Metadata(description = "The MIME type of the message body when sending a file or binary content (File, WrappedFile, "
                            + "byte[] or InputStream) to the model. Takes precedence over component content-type headers "
                            + "and automatic MIME type detection",
              javaType = "String")
    public static final String MEDIA_TYPE = "CamelOpenAIMediaType";

    // Output Headers
    @Metadata(description = "The thinking content extracted from <think>...</think> blocks in the model response",
              javaType = "String")
    public static final String THINKING_CONTENT = "CamelOpenAIThinkingContent";
    @Metadata(description = "The reasoning content from the model response reasoning_content field, "
                            + "used by thinking models like Qwen3 and DeepSeek-R1",
              javaType = "String")
    public static final String REASONING_CONTENT = "CamelOpenAIReasoningContent";
    @Metadata(description = "The model used for the completion response", javaType = "String")
    public static final String RESPONSE_MODEL = "CamelOpenAIResponseModel";
    @Metadata(description = "The unique identifier for the completion response", javaType = "String")
    public static final String RESPONSE_ID = "CamelOpenAIResponseId";
    @Metadata(description = "The reason the completion finished (e.g., stop, length, content_filter)", javaType = "String")
    public static final String FINISH_REASON = "CamelOpenAIFinishReason";
    @Metadata(description = "The number of tokens used in the prompt for the latest API call", javaType = "Long")
    public static final String PROMPT_TOKENS = "CamelOpenAIPromptTokens";
    @Metadata(description = "The number of tokens used in the completion for the latest API call", javaType = "Long")
    public static final String COMPLETION_TOKENS = "CamelOpenAICompletionTokens";
    @Metadata(description = "The total number of tokens used (prompt + completion) for the latest API call",
              javaType = "Long")
    public static final String TOTAL_TOKENS = "CamelOpenAITotalTokens";

    // MCP Tool Call Headers
    @Metadata(description = "Number of tool call iterations performed in the agentic loop", javaType = "Integer")
    public static final String TOOL_ITERATIONS = "CamelOpenAIToolIterations";
    @Metadata(description = "List of tool names called during the agentic loop", javaType = "java.util.List<String>")
    public static final String MCP_TOOL_CALLS = "CamelOpenAIMcpToolCalls";
    @Metadata(description = "Whether the response came directly from a tool with returnDirect=true, "
                            + "rather than from the LLM",
              javaType = "Boolean")
    public static final String MCP_RETURN_DIRECT = "CamelOpenAIMcpReturnDirect";
    @Metadata(description = "Cumulative prompt tokens consumed across all agentic loop iterations", javaType = "Long")
    public static final String AGENTIC_PROMPT_TOKENS = "CamelOpenAIAgenticPromptTokens";
    @Metadata(description = "Cumulative completion tokens consumed across all agentic loop iterations", javaType = "Long")
    public static final String AGENTIC_COMPLETION_TOKENS = "CamelOpenAIAgenticCompletionTokens";
    @Metadata(description = "Cumulative total tokens consumed across all agentic loop iterations", javaType = "Long")
    public static final String AGENTIC_TOTAL_TOKENS = "CamelOpenAIAgenticTotalTokens";

    @Metadata(description = "Per-iteration breakdown of the OpenAI MCP agentic loop, including tool calls, "
                            + "truncated arguments/results, token usage, and duration for each iteration",
              javaType = "java.util.List<org.apache.camel.component.openai.AgenticIterationTrace>")
    public static final String AGENTIC_TRACE = "CamelOpenAIAgenticTrace";

    // Output Exchange Properties
    @Metadata(description = "The complete OpenAI chat completion response object",
              javaType = "com.openai.models.chat.completions.ChatCompletion")
    public static final String RESPONSE = "CamelOpenAIResponse";
    @Metadata(description = "The complete OpenAI Responses API response object",
              javaType = "com.openai.models.responses.Response")
    public static final String RESPONSES_RESPONSE = "CamelOpenAIResponsesResponse";
    @Metadata(description = "The complete OpenAI moderation response object",
              javaType = "com.openai.models.moderations.ModerationCreateResponse")
    public static final String MODERATION_RESPONSE = "CamelOpenAIModerationResponse";
    @Metadata(description = "The complete OpenAI image generation or edit response object",
              javaType = "com.openai.models.images.ImagesResponse")
    public static final String IMAGE_RESPONSE = "CamelOpenAIImageResponse";
    @Metadata(description = "The complete OpenAI embeddings response object",
              javaType = "com.openai.models.embeddings.CreateEmbeddingResponse")
    public static final String EMBEDDINGS_RESPONSE = "CamelOpenAIEmbeddingsResponse";
    @Metadata(description = "The complete OpenAI audio transcription response object",
              javaType = "com.openai.models.audio.transcriptions.TranscriptionCreateResponse")
    public static final String AUDIO_TRANSCRIPTION_RESPONSE = "CamelOpenAIAudioTranscriptionResponse";
    @Metadata(description = "The complete OpenAI audio translation response object",
              javaType = "com.openai.models.audio.translations.TranslationCreateResponse")
    public static final String AUDIO_TRANSLATION_RESPONSE = "CamelOpenAIAudioTranslationResponse";

    // Embeddings Input Headers
    @Metadata(description = "The model to use for embeddings", javaType = "String")
    public static final String EMBEDDING_MODEL = "CamelOpenAIEmbeddingModel";
    @Metadata(description = "Number of output dimensions", javaType = "Integer")
    public static final String EMBEDDING_DIMENSIONS = "CamelOpenAIEmbeddingDimensions";

    // Embeddings Output Headers
    @Metadata(description = "The embedding model used in the response", javaType = "String")
    public static final String EMBEDDING_RESPONSE_MODEL = "CamelOpenAIEmbeddingResponseModel";
    @Metadata(description = "Number of embeddings returned", javaType = "Integer")
    public static final String EMBEDDING_COUNT = "CamelOpenAIEmbeddingCount";
    @Metadata(description = "Vector dimensions of the embeddings", javaType = "Integer")
    public static final String EMBEDDING_VECTOR_SIZE = "CamelOpenAIEmbeddingVectorSize";

    // Similarity Helper Headers
    @Metadata(description = "Reference embedding vector for similarity comparison", javaType = "List<Float>")
    public static final String REFERENCE_EMBEDDING = "CamelOpenAIReferenceEmbedding";
    @Metadata(description = "Calculated cosine similarity score (0.0 to 1.0)", javaType = "Double")
    public static final String SIMILARITY_SCORE = "CamelOpenAISimilarityScore";
    @Metadata(description = "Original text content when embeddings operation is used", javaType = "String or List<String>")
    public static final String ORIGINAL_TEXT = "CamelOpenAIOriginalText";

    // Moderation Input Headers
    @Metadata(description = "The model to use for moderation (e.g., omni-moderation-latest)", javaType = "String")
    public static final String MODERATION_MODEL = "CamelOpenAIModerationModel";

    // Moderation Output Headers
    @Metadata(description = "Whether the moderation API flagged the input as violating the usage policies. "
                            + "For a batch of inputs this is true when at least one input was flagged",
              javaType = "Boolean")
    public static final String MODERATION_FLAGGED = "CamelOpenAIModerationFlagged";
    @Metadata(description = "One verdict per moderated input, in the order of the inputs. Each entry holds the keys "
                            + "'input', 'flagged', 'categories' and 'categoryScores', so a batch can be split and "
                            + "routed per item",
              javaType = "java.util.List<java.util.Map<String, Object>>")
    public static final String MODERATION_RESULTS = "CamelOpenAIModerationResults";
    @Metadata(description = "The moderation categories and whether each one was violated, for a single input. "
                            + "Not set for a list body, where 'CamelOpenAIModerationResults' carries the verdicts",
              javaType = "java.util.Map<String, Boolean>")
    public static final String MODERATION_CATEGORIES = "CamelOpenAIModerationCategories";
    @Metadata(description = "The moderation confidence score per category, for a single input. Not set for a list "
                            + "body, where 'CamelOpenAIModerationResults' carries the verdicts",
              javaType = "java.util.Map<String, Double>")
    public static final String MODERATION_CATEGORY_SCORES = "CamelOpenAIModerationCategoryScores";

    // Keys of a single entry of the CamelOpenAIModerationResults header
    public static final String MODERATION_RESULT_INPUT = "input";
    public static final String MODERATION_RESULT_FLAGGED = "flagged";
    public static final String MODERATION_RESULT_CATEGORIES = "categories";
    public static final String MODERATION_RESULT_CATEGORY_SCORES = "categoryScores";
    @Metadata(description = "The moderation model used in the response", javaType = "String")
    public static final String MODERATION_RESPONSE_MODEL = "CamelOpenAIModerationResponseModel";

    // Audio Transcription Input Headers
    @Metadata(description = "The model to use for audio transcription", javaType = "String")
    public static final String AUDIO_MODEL = "CamelOpenAIAudioModel";
    @Metadata(description = "The language of the input audio (ISO-639-1)", javaType = "String")
    public static final String AUDIO_LANGUAGE = "CamelOpenAIAudioLanguage";
    @Metadata(description = "The response format for audio transcription (json, text, srt, verbose_json, vtt)",
              javaType = "String")
    public static final String AUDIO_RESPONSE_FORMAT = "CamelOpenAIAudioResponseFormat";
    @Metadata(description = "Sampling temperature for audio transcription (0.0 to 1.0)", javaType = "Double")
    public static final String AUDIO_TEMPERATURE = "CamelOpenAIAudioTemperature";
    @Metadata(description = "Optional text to guide the model's style or continue a previous audio segment",
              javaType = "String")
    public static final String AUDIO_PROMPT = "CamelOpenAIAudioPrompt";
    @Metadata(description = "Comma-separated timestamp granularities: word, segment, or word,segment (verbose_json only)",
              javaType = "String")
    public static final String AUDIO_TIMESTAMP_GRANULARITIES = "CamelOpenAIAudioTimestampGranularities";

    // Audio Transcription/Translation Output Headers
    @Metadata(description = "Duration of the audio in seconds (verbose_json only)", javaType = "Double")
    public static final String AUDIO_DURATION = "CamelOpenAIAudioDuration";
    @Metadata(description = "Language detected in the audio (verbose_json only)", javaType = "String")
    public static final String AUDIO_DETECTED_LANGUAGE = "CamelOpenAIAudioDetectedLanguage";

    // Audio Speech (Text-to-Speech) Input Headers
    @Metadata(description = "The model to use for text-to-speech (e.g., gpt-4o-mini-tts, tts-1, tts-1-hd)",
              javaType = "String")
    public static final String SPEECH_MODEL = "CamelOpenAISpeechModel";
    @Metadata(description = "The voice to use for the generated audio (e.g., alloy, echo, fable, onyx, nova, shimmer)",
              javaType = "String")
    public static final String SPEECH_VOICE = "CamelOpenAISpeechVoice";
    @Metadata(description = "The audio format for text-to-speech output (mp3, opus, aac, flac, wav, pcm)",
              javaType = "String")
    public static final String SPEECH_RESPONSE_FORMAT = "CamelOpenAISpeechResponseFormat";
    @Metadata(description = "The speed of the generated audio (0.25 to 4.0, where 1.0 is normal speed)",
              javaType = "Double")
    public static final String SPEECH_SPEED = "CamelOpenAISpeechSpeed";
    @Metadata(description = "Optional instructions to control the voice of the generated audio "
                            + "(does not work with tts-1 or tts-1-hd)",
              javaType = "String")
    public static final String SPEECH_INSTRUCTIONS = "CamelOpenAISpeechInstructions";

    // Image Generation/Edit Input Headers
    @Metadata(description = "The model to use for image generation or editing (e.g., gpt-image-1, dall-e-3, dall-e-2)",
              javaType = "String")
    public static final String IMAGE_MODEL = "CamelOpenAIImageModel";
    @Metadata(description = "The prompt describing the image to generate, or the edit to apply. Takes precedence over "
                            + "the imagePrompt endpoint option and, for image-generation, over the message body",
              javaType = "String")
    public static final String IMAGE_PROMPT = "CamelOpenAIImagePrompt";
    @Metadata(description = "The size of the generated image (e.g., 1024x1024, 1536x1024, auto)", javaType = "String")
    public static final String IMAGE_SIZE = "CamelOpenAIImageSize";
    @Metadata(description = "The quality of the generated image (auto, high, medium, low for GPT image models; "
                            + "hd, standard for dall-e-3; standard for dall-e-2)",
              javaType = "String")
    public static final String IMAGE_QUALITY = "CamelOpenAIImageQuality";
    @Metadata(description = "The response format of the generated image (url or b64_json). Only supported by "
                            + "dall-e-2 and dall-e-3; GPT image models always return base64",
              javaType = "String")
    public static final String IMAGE_RESPONSE_FORMAT = "CamelOpenAIImageResponseFormat";
    @Metadata(description = "The number of images to generate", javaType = "Integer")
    public static final String IMAGE_COUNT = "CamelOpenAIImageCount";
    @Metadata(description = "The background of the generated image (transparent, opaque, auto). "
                            + "Only supported by GPT image models",
              javaType = "String")
    public static final String IMAGE_BACKGROUND = "CamelOpenAIImageBackground";
    @Metadata(description = "The output format of the generated image (png, jpeg, webp). "
                            + "Only supported by GPT image models",
              javaType = "String")
    public static final String IMAGE_OUTPUT_FORMAT = "CamelOpenAIImageOutputFormat";
    @Metadata(description = "The compression level (0-100) for the webp or jpeg output formats. "
                            + "Only supported by GPT image models",
              javaType = "Integer")
    public static final String IMAGE_OUTPUT_COMPRESSION = "CamelOpenAIImageOutputCompression";
    @Metadata(description = "The style of the generated image (vivid or natural). Only supported by dall-e-3",
              javaType = "String")
    public static final String IMAGE_STYLE = "CamelOpenAIImageStyle";
    @Metadata(description = "The content moderation level for image generation (low or auto). "
                            + "Only supported by GPT image models",
              javaType = "String")
    public static final String IMAGE_MODERATION = "CamelOpenAIImageModeration";
    @Metadata(description = "How closely the edit must match the style and features of the input image (high or low). "
                            + "Only supported by the image-edit operation on gpt-image-1 and gpt-image-1.5",
              javaType = "String")
    public static final String IMAGE_INPUT_FIDELITY = "CamelOpenAIImageInputFidelity";
    @Metadata(description = "An optional PNG mask for the image-edit operation, where the fully transparent areas "
                            + "indicate where the image should be edited",
              javaType = "byte[], java.io.File, java.nio.file.Path or java.io.InputStream")
    public static final String IMAGE_MASK = "CamelOpenAIImageMask";

    // Image Generation/Edit Output Headers
    @Metadata(description = "The number of images returned in the response", javaType = "Integer")
    public static final String IMAGE_RESULT_COUNT = "CamelOpenAIImageResultCount";
    @Metadata(description = "The prompt as revised by the model, when a single image is returned (dall-e-3)",
              javaType = "String")
    public static final String IMAGE_REVISED_PROMPT = "CamelOpenAIImageRevisedPrompt";
    @Metadata(description = "The prompts as revised by the model, one entry per returned image (dall-e-3)",
              javaType = "java.util.List<String>")
    public static final String IMAGE_REVISED_PROMPTS = "CamelOpenAIImageRevisedPrompts";
    @Metadata(description = "The number of input tokens billed for the image request. "
                            + "Only reported by GPT image models",
              javaType = "Long")
    public static final String IMAGE_INPUT_TOKENS = "CamelOpenAIImageInputTokens";
    @Metadata(description = "The number of output tokens billed for the image request. "
                            + "Only reported by GPT image models",
              javaType = "Long")
    public static final String IMAGE_OUTPUT_TOKENS = "CamelOpenAIImageOutputTokens";
    @Metadata(description = "The total number of tokens billed for the image request. "
                            + "Only reported by GPT image models",
              javaType = "Long")
    public static final String IMAGE_TOTAL_TOKENS = "CamelOpenAIImageTotalTokens";

    private OpenAIConstants() {
        // Utility class
    }
}
