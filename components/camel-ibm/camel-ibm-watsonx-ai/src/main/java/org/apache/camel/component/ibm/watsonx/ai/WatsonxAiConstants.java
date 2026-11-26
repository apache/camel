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
package org.apache.camel.component.ibm.watsonx.ai;

import org.apache.camel.spi.Metadata;

/**
 * Constants for IBM watsonx.ai component headers.
 */
public interface WatsonxAiConstants {

    String HEADER_PREFIX = "CamelIBMWatsonxAi";

    // Operation
    @Metadata(description = "The operation to perform",
              javaType = "org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations")
    String OPERATION = HEADER_PREFIX + "Operation";

    // Input/Output
    @Metadata(description = "The input text/prompt for generation", javaType = "String")
    String INPUT = HEADER_PREFIX + "Input";

    @Metadata(description = "The list of inputs for batch operations", javaType = "java.util.List<String>")
    String INPUTS = HEADER_PREFIX + "Inputs";

    @Metadata(description = "The generated text output", javaType = "String")
    String GENERATED_TEXT = HEADER_PREFIX + "GeneratedText";

    // Model/Deployment
    @Metadata(label = "producer",
              description = "The model of chat and chatStreaming for this request, overriding the modelId option; forecast"
                            + " and deploymentForecast report on the response the model that answered",
              javaType = "String")
    String MODEL_ID = HEADER_PREFIX + "ModelId";

    @Metadata(description = "The deployment ID to use", javaType = "String")
    String DEPLOYMENT_ID = HEADER_PREFIX + "DeploymentId";

    @Metadata(description = "The space ID for deployment operations", javaType = "String")
    String SPACE_ID = HEADER_PREFIX + "SpaceId";

    @Metadata(description = "The deployment name", javaType = "String")
    String DEPLOYMENT_NAME = HEADER_PREFIX + "DeploymentName";

    @Metadata(description = "The deployed asset type", javaType = "String")
    String DEPLOYMENT_ASSET_TYPE = HEADER_PREFIX + "DeploymentAssetType";

    @Metadata(description = "The deployment status state", javaType = "String")
    String DEPLOYMENT_STATUS = HEADER_PREFIX + "DeploymentStatus";

    // Text Generation Parameters
    @Metadata(description = "Temperature for randomness (0.0 to 2.0)", javaType = "Double")
    String TEMPERATURE = HEADER_PREFIX + "Temperature";

    @Metadata(description = "Maximum new tokens to generate", javaType = "Integer")
    String MAX_NEW_TOKENS = HEADER_PREFIX + "MaxNewTokens";

    @Metadata(description = "Top P (nucleus sampling)", javaType = "Double")
    String TOP_P = HEADER_PREFIX + "TopP";

    @Metadata(description = "Top K (top-k sampling)", javaType = "Integer")
    String TOP_K = HEADER_PREFIX + "TopK";

    @Metadata(description = "Repetition penalty", javaType = "Double")
    String REPETITION_PENALTY = HEADER_PREFIX + "RepetitionPenalty";

    // Chat
    @Metadata(description = "The chat messages (ChatMessage objects, or Strings as user messages) to send, in the place of"
                            + " the body. When the model answers chat with tool calls, chat sets it to the messages it sent,"
                            + " which processToolCalls extends with the tool calls and the tool results, so the next chat"
                            + " sends the whole conversation",
              javaType = "java.util.List")
    String MESSAGES = HEADER_PREFIX + "Messages";

    @Metadata(description = "The system message for chat (used to build messages if MESSAGES header is not set)",
              javaType = "String")
    String SYSTEM_MESSAGE = HEADER_PREFIX + "SystemMessage";

    @Metadata(description = "The user message for chat (used to build messages if MESSAGES header is not set, alternative to body)",
              javaType = "String")
    String USER_MESSAGE = HEADER_PREFIX + "UserMessage";

    @Metadata(description = "The tools available for function calling", javaType = "java.util.List")
    String TOOLS = HEADER_PREFIX + "Tools";

    @Metadata(description = "How the model chooses the tools of chat, overriding the toolChoice option: auto, required, none,"
                            + " or the name of the tool the model must call",
              javaType = "String")
    String TOOL_CHOICE = HEADER_PREFIX + "ToolChoice";

    // Chat output, reasoning and guardrails
    @Metadata(description = "The format of the chat answer, overriding the responseFormat option: text or json",
              javaType = "String")
    String RESPONSE_FORMAT = HEADER_PREFIX + "ResponseFormat";

    @Metadata(description = "The JSON schema the chat answer follows, overriding the jsonSchema option: JSON text or a Map",
              javaType = "String")
    String JSON_SCHEMA = HEADER_PREFIX + "JsonSchema";

    @Metadata(description = "Guided decoding of chat, overriding the guidedChoice option: the choices of the answer, as a"
                            + " List or comma separated",
              javaType = "java.util.List<String>")
    String GUIDED_CHOICE = HEADER_PREFIX + "GuidedChoice";

    @Metadata(description = "The random seed of chat sampling, overriding the seed option", javaType = "Integer")
    String SEED = HEADER_PREFIX + "Seed";

    @Metadata(description = "The stop sequences of chat, overriding the stop option: a List, or comma separated",
              javaType = "java.util.List<String>")
    String STOP = HEADER_PREFIX + "Stop";

    @Metadata(description = "How much the model of chat reasons, overriding the reasoningEffort option: low, medium or"
                            + " high",
              javaType = "String")
    String REASONING_EFFORT = HEADER_PREFIX + "ReasoningEffort";

    @Metadata(label = "producer", description = "The reasoning of the model that preceded the chat answer, when it gives one",
              javaType = "String")
    String REASONING_CONTENT = HEADER_PREFIX + "ReasoningContent";

    @Metadata(label = "producer", description = "The refusal of the model to answer, when it refuses", javaType = "String")
    String REFUSAL = HEADER_PREFIX + "Refusal";

    @Metadata(label = "producer",
              description = "All the answers of chat when it generates more than one (numberOfChoices), the body being the"
                            + " first",
              javaType = "java.util.List<com.ibm.watsonx.ai.chat.model.AssistantMessage>")
    String ASSISTANT_MESSAGES = HEADER_PREFIX + "AssistantMessages";

    @Metadata(label = "producer",
              description = "The findings of the chat guardrails (moderationHap, moderationPii, moderationGraniteGuardian),"
                            + " by detector",
              javaType = "java.util.Map")
    String MODERATION_RESULTS = HEADER_PREFIX + "ModerationResults";

    // Embeddings
    @Metadata(description = "The embedding vectors result", javaType = "java.util.List")
    String EMBEDDINGS = HEADER_PREFIX + "Embeddings";

    // Rerank
    @Metadata(description = "The query for reranking", javaType = "String")
    String RERANK_QUERY = HEADER_PREFIX + "RerankQuery";

    @Metadata(description = "Number of top results to return for reranking", javaType = "Integer")
    String RERANK_TOP_N = HEADER_PREFIX + "RerankTopN";

    // Tokenization
    @Metadata(description = "The token count", javaType = "Integer")
    String TOKEN_COUNT = HEADER_PREFIX + "TokenCount";

    @Metadata(description = "The tokens of the input text", javaType = "java.util.List<String>")
    String TOKENS = HEADER_PREFIX + "Tokens";

    // File Input
    @Metadata(description = "The file to upload", javaType = "java.io.File")
    String FILE = HEADER_PREFIX + "File";

    @Metadata(description = "The file name when using InputStream input", javaType = "String")
    String FILE_NAME = HEADER_PREFIX + "FileName";

    // Text Extraction
    @Metadata(description = "The file path for extraction or classification (for files already in COS), such as"
                            + " textExtractionAndFetch",
              javaType = "String")
    String FILE_PATH = HEADER_PREFIX + "FilePath";

    @Metadata(description = "The name of the output file of text extraction, in the result bucket", javaType = "String")
    String OUTPUT_FILE_NAME = HEADER_PREFIX + "OutputFileName";

    @Metadata(description = "The extraction request ID", javaType = "String")
    String EXTRACTION_ID = HEADER_PREFIX + "ExtractionId";

    @Metadata(description = "The extraction status", javaType = "String")
    String EXTRACTION_STATUS = HEADER_PREFIX + "ExtractionStatus";

    @Metadata(description = "The extracted text content", javaType = "String")
    String EXTRACTED_TEXT = HEADER_PREFIX + "ExtractedText";

    // COS File Operations
    @Metadata(description = "The COS bucket name for file operations", javaType = "String")
    String BUCKET_NAME = HEADER_PREFIX + "BucketName";

    @Metadata(description = "Whether the upload operation was successful", javaType = "Boolean")
    String UPLOAD_SUCCESS = HEADER_PREFIX + "UploadSuccess";

    @Metadata(description = "Whether the delete operation was successful", javaType = "Boolean")
    String DELETE_SUCCESS = HEADER_PREFIX + "DeleteSuccess";

    // Text Classification
    @Metadata(description = "The classification request ID", javaType = "String")
    String CLASSIFICATION_ID = HEADER_PREFIX + "ClassificationId";

    @Metadata(description = "The classification status", javaType = "String")
    String CLASSIFICATION_STATUS = HEADER_PREFIX + "ClassificationStatus";

    @Metadata(description = "The classification result (document type)", javaType = "String")
    String CLASSIFICATION_RESULT = HEADER_PREFIX + "ClassificationResult";

    @Metadata(description = "Whether the document was classified", javaType = "Boolean")
    String DOCUMENT_CLASSIFIED = HEADER_PREFIX + "DocumentClassified";

    @Metadata(description = "Error message when classification or extraction fails", javaType = "String")
    String ERROR_MESSAGE = HEADER_PREFIX + "ErrorMessage";

    @Metadata(description = "Error code when classification or extraction fails", javaType = "String")
    String ERROR_CODE = HEADER_PREFIX + "ErrorCode";

    // Detection
    @Metadata(description = "List of detectors to use, overriding the detectors option",
              javaType = "java.util.List<com.ibm.watsonx.ai.detection.detector.BaseDetector>")
    String DETECTORS = HEADER_PREFIX + "Detectors";

    @Metadata(description = "The context of detectContext: the documents, as a List of texts or URLs, or one String",
              javaType = "java.util.List<String>")
    String DETECTION_CONTEXT = HEADER_PREFIX + "DetectionContext";

    @Metadata(description = "The prompt that the generated text of the body of detectGenerated answers", javaType = "String")
    String DETECTION_PROMPT = HEADER_PREFIX + "DetectionPrompt";

    @Metadata(description = "Whether harmful content was detected: true when a detector found something, not counting a"
                            + " Granite Guardian answer of No",
              javaType = "Boolean")
    String DETECTED = HEADER_PREFIX + "Detected";

    @Metadata(description = "Detection results grouped by type: the detection, score and text (and start and end for"
                            + " detect, evidence for detectContext) of each",
              javaType = "java.util.Map")
    String DETECTION_RESULTS = HEADER_PREFIX + "DetectionResults";

    @Metadata(description = "Count of the detections that watsonx.ai returned, including Granite Guardian answers of No",
              javaType = "Integer")
    String DETECTION_COUNT = HEADER_PREFIX + "DetectionCount";

    // Streaming
    @Metadata(description = "Consumer for streaming text", javaType = "java.util.function.Consumer")
    String STREAM_CONSUMER = HEADER_PREFIX + "StreamConsumer";

    // Usage/Metadata
    @Metadata(description = "Input token count", javaType = "Integer")
    String INPUT_TOKEN_COUNT = HEADER_PREFIX + "InputTokenCount";

    @Metadata(description = "Output token count", javaType = "Integer")
    String OUTPUT_TOKEN_COUNT = HEADER_PREFIX + "OutputTokenCount";

    @Metadata(description = "Stop reason for generation", javaType = "String")
    String STOP_REASON = HEADER_PREFIX + "StopReason";

    // Time Series / Forecast
    @Metadata(description = "The input schema for time series forecast: an InputSchema, or a Map with the keys"
                            + " timestamp_column, id_columns, freq and target_columns (a List or a comma separated String"
                            + " for the columns)",
              javaType = "com.ibm.watsonx.ai.timeseries.InputSchema")
    String FORECAST_INPUT_SCHEMA = HEADER_PREFIX + "ForecastInputSchema";

    @Metadata(description = "The forecast data: a ForecastData, or a Map of column name to the List of its values",
              javaType = "com.ibm.watsonx.ai.timeseries.ForecastData")
    String FORECAST_DATA = HEADER_PREFIX + "ForecastData";

    @Metadata(description = "The number of time steps to forecast, overriding the predictionLength option",
              javaType = "Integer")
    String FORECAST_PREDICTION_LENGTH = HEADER_PREFIX + "ForecastPredictionLength";

    @Metadata(description = "The known future values of the exogenous columns, for deploymentForecast only: a ForecastData,"
                            + " or a Map of column name to the List of its values",
              javaType = "com.ibm.watsonx.ai.timeseries.ForecastData")
    String FORECAST_FUTURE_DATA = HEADER_PREFIX + "ForecastFutureData";

    @Metadata(description = "The forecast results", javaType = "java.util.List")
    String FORECAST_RESULTS = HEADER_PREFIX + "ForecastResults";

    @Metadata(description = "Number of input data points", javaType = "Integer")
    String FORECAST_INPUT_DATA_POINTS = HEADER_PREFIX + "ForecastInputDataPoints";

    @Metadata(description = "Number of output data points", javaType = "Integer")
    String FORECAST_OUTPUT_DATA_POINTS = HEADER_PREFIX + "ForecastOutputDataPoints";

    // Foundation Models
    @Metadata(description = "List of foundation models", javaType = "java.util.List")
    String FOUNDATION_MODELS = HEADER_PREFIX + "FoundationModels";

    @Metadata(description = "List of foundation model tasks", javaType = "java.util.List")
    String FOUNDATION_MODEL_TASKS = HEADER_PREFIX + "FoundationModelTasks";

    @Metadata(description = "The filter of listModels, in the syntax of the filters parameter of watsonx.ai, such as"
                            + " function_text_chat, modelid_ibm/granite-4-h-small or function_rerank,!function_embedding:and"
                            + " (or a com.ibm.watsonx.ai.foundationmodel.filter.Filter)",
              javaType = "String")
    String FOUNDATION_MODEL_FILTER = HEADER_PREFIX + "FoundationModelFilter";

    @Metadata(description = "Whether listModels includes the tech preview models", javaType = "Boolean")
    String FOUNDATION_MODEL_TECH_PREVIEW = HEADER_PREFIX + "FoundationModelTechPreview";

    // Tool Service
    @Metadata(description = "The tool name to run", javaType = "String")
    String TOOL_NAME = HEADER_PREFIX + "ToolName";

    @Metadata(description = "The tool request object", javaType = "com.ibm.watsonx.ai.tool.ToolRequest")
    String TOOL_REQUEST = HEADER_PREFIX + "ToolRequest";

    @Metadata(description = "The tool configuration", javaType = "java.util.Map")
    String TOOL_CONFIG = HEADER_PREFIX + "ToolConfig";

    @Metadata(description = "List of available utility tools", javaType = "java.util.List")
    String UTILITY_TOOLS = HEADER_PREFIX + "UtilityTools";

    @Metadata(description = "Tool registry for chat with tool calling capabilities",
              javaType = "com.ibm.watsonx.ai.chat.ToolRegistry")
    String TOOL_REGISTRY = HEADER_PREFIX + "ToolRegistry";

    @Metadata(description = "List of tool calls requested by the assistant, removed when the answer has none",
              javaType = "java.util.List<com.ibm.watsonx.ai.chat.model.ToolCall>")
    String TOOL_CALLS = HEADER_PREFIX + "ToolCalls";

    @Metadata(description = "Whether the assistant response contains tool calls", javaType = "Boolean")
    String HAS_TOOL_CALLS = HEADER_PREFIX + "HasToolCalls";

    @Metadata(description = "The full assistant message from chat response",
              javaType = "com.ibm.watsonx.ai.chat.model.AssistantMessage")
    String ASSISTANT_MESSAGE = HEADER_PREFIX + "AssistantMessage";
}
