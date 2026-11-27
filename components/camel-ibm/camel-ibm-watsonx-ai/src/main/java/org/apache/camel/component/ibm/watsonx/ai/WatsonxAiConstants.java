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
    @Metadata(description = "The operation to perform. Overrides the operation option.",
              javaType = "org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations")
    String OPERATION = HEADER_PREFIX + "Operation";

    // Input/Output
    @Metadata(description = "The input text, in the place of the body, of textGeneration, textGenerationStreaming,"
                            + " tokenize, deploymentGenerate, detect, detectContext and detectGenerated (for which it is"
                            + " the generated text).",
              javaType = "String")
    String INPUT = HEADER_PREFIX + "Input";

    @Metadata(description = "The input texts, in the place of the body, of embedding (the texts to embed) and rerank (the"
                            + " texts to rank), as a List of Strings.",
              javaType = "java.util.List<String>")
    String INPUTS = HEADER_PREFIX + "Inputs";

    @Metadata(description = "The generated text of textGeneration, deploymentGenerate, chat, deploymentChat and the"
                            + " streaming operations, which is also the body. For chat, the content of the first answer.",
              javaType = "String")
    String GENERATED_TEXT = HEADER_PREFIX + "GeneratedText";

    // Model/Deployment
    @Metadata(label = "producer",
              description = "The model of chat and chatStreaming for this request, overriding the modelId option."
                            + " embedding, rerank, tokenize, forecast and deploymentForecast set it to the model that"
                            + " answered.",
              javaType = "String")
    String MODEL_ID = HEADER_PREFIX + "ModelId";

    @Metadata(description = "The ID of the deployment of the deployment operations. Overrides the deploymentId option.",
              javaType = "String")
    String DEPLOYMENT_ID = HEADER_PREFIX + "DeploymentId";

    @Metadata(description = "The ID of the deployment space in which deploymentInfo looks the deployment up. Overrides"
                            + " the spaceId option for deploymentInfo only.",
              javaType = "String")
    String SPACE_ID = HEADER_PREFIX + "SpaceId";

    @Metadata(description = "The name of the deployment, set by deploymentInfo.", javaType = "String")
    String DEPLOYMENT_NAME = HEADER_PREFIX + "DeploymentName";

    @Metadata(description = "The type of the asset that the deployment serves, set by deploymentInfo.", javaType = "String")
    String DEPLOYMENT_ASSET_TYPE = HEADER_PREFIX + "DeploymentAssetType";

    @Metadata(description = "The state of the deployment, set by deploymentInfo.", javaType = "String")
    String DEPLOYMENT_STATUS = HEADER_PREFIX + "DeploymentStatus";

    // Text Generation Parameters
    @Metadata(description = "The sampling temperature of text generation and chat. Overrides the temperature option.",
              javaType = "Double")
    String TEMPERATURE = HEADER_PREFIX + "Temperature";

    @Metadata(description = "The maximum number of tokens to generate. Overrides the maxNewTokens option for text"
                            + " generation and the maxCompletionTokens option for chat.",
              javaType = "Integer")
    String MAX_NEW_TOKENS = HEADER_PREFIX + "MaxNewTokens";

    @Metadata(description = "The nucleus sampling of text generation and chat. Overrides the topP option.", javaType = "Double")
    String TOP_P = HEADER_PREFIX + "TopP";

    @Metadata(description = "The top-k sampling of text generation. Overrides the topK option.", javaType = "Integer")
    String TOP_K = HEADER_PREFIX + "TopK";

    @Metadata(description = "The repetition penalty of text generation. Overrides the repetitionPenalty option.",
              javaType = "Double")
    String REPETITION_PENALTY = HEADER_PREFIX + "RepetitionPenalty";

    // Chat
    @Metadata(description = "The chat messages (ChatMessage objects, or Strings as user messages) to send, in the place of"
                            + " the body. When the model answers chat with tool calls, chat sets it to the messages it sent,"
                            + " which processToolCalls extends with the tool calls and the tool results, so the next chat"
                            + " sends the whole conversation",
              javaType = "java.util.List")
    String MESSAGES = HEADER_PREFIX + "Messages";

    @Metadata(description = "The system message of chat, chatStreaming and deploymentChat, sent before the user message"
                            + " when the CamelIBMWatsonxAiMessages header is not set. Ignored when the body is a"
                            + " ChatMessage or a List of messages.",
              javaType = "String")
    String SYSTEM_MESSAGE = HEADER_PREFIX + "SystemMessage";

    @Metadata(description = "The user message of chat, chatStreaming and deploymentChat, in the place of the body, when"
                            + " the CamelIBMWatsonxAiMessages header is not set.",
              javaType = "String")
    String USER_MESSAGE = HEADER_PREFIX + "UserMessage";

    @Metadata(description = "The tools that the model of chat, chatStreaming and deploymentChat may call, as a List of"
                            + " com.ibm.watsonx.ai.chat.model.Tool.",
              javaType = "java.util.List")
    String TOOLS = HEADER_PREFIX + "Tools";

    @Metadata(description = "How the model chooses the tools of chat, overriding the toolChoice option: auto, required, none,"
                            + " or the name of the tool the model must call",
              javaType = "String")
    String TOOL_CHOICE = HEADER_PREFIX + "ToolChoice";

    // Chat output, reasoning and guardrails
    @Metadata(description = "The format of the chat answer, overriding the responseFormat and jsonSchema options: text or"
                            + " json",
              javaType = "String")
    String RESPONSE_FORMAT = HEADER_PREFIX + "ResponseFormat";

    @Metadata(description = "The JSON schema the chat answer follows, overriding the jsonSchema and responseFormat options:"
                            + " JSON text or a Map",
              javaType = "Object")
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
    @Metadata(description = "The embedding vectors of embedding, one List of Floats per input, which is also the body.",
              javaType = "java.util.List")
    String EMBEDDINGS = HEADER_PREFIX + "Embeddings";

    // Rerank
    @Metadata(description = "The query that rerank ranks the inputs against. Required for rerank.", javaType = "String")
    String RERANK_QUERY = HEADER_PREFIX + "RerankQuery";

    @Metadata(description = "The number of best ranked inputs that rerank returns. Overrides the rerankTopN option.",
              javaType = "Integer")
    String RERANK_TOP_N = HEADER_PREFIX + "RerankTopN";

    // Tokenization
    @Metadata(description = "The number of tokens of the input of tokenize, which is also the body.", javaType = "Integer")
    String TOKEN_COUNT = HEADER_PREFIX + "TokenCount";

    @Metadata(description = "The tokens of the input of tokenize.", javaType = "java.util.List<String>")
    String TOKENS = HEADER_PREFIX + "Tokens";

    // File Input
    @Metadata(description = "The local file that the upload operations of text extraction and text classification upload,"
                            + " when the body is not a file.",
              javaType = "java.io.File")
    String FILE = HEADER_PREFIX + "File";

    @Metadata(description = "The name of the document that the upload operations upload when the body is not a file, else"
                            + " taken from the CamelAwsS3Key, CamelAzureStorageBlobBlobName or CamelFileName header. For"
                            + " textExtractionReadFile, textExtractionDeleteFile and textClassificationDeleteFile, the name"
                            + " of the file to read or delete, in the place of the body.",
              javaType = "String")
    String FILE_NAME = HEADER_PREFIX + "FileName";

    // Text Extraction
    @Metadata(description = "The path of the document in the documentBucket that textExtraction, textExtractionAndFetch,"
                            + " textClassification and textClassificationAndFetch process, in the place of the body.",
              javaType = "String")
    String FILE_PATH = HEADER_PREFIX + "FilePath";

    @Metadata(description = "The name of the output file of text extraction, in the result bucket", javaType = "String")
    String OUTPUT_FILE_NAME = HEADER_PREFIX + "OutputFileName";

    @Metadata(description = "The ID of the text extraction request: set by textExtraction, textExtractionUpload and"
                            + " textExtractionFetch, and read by textExtractionFetch and textExtractionDeleteRequest in the"
                            + " place of the body.",
              javaType = "String")
    String EXTRACTION_ID = HEADER_PREFIX + "ExtractionId";

    @Metadata(description = "The status of the text extraction request, such as submitted, running, completed or failed,"
                            + " set by textExtraction, textExtractionUpload and textExtractionFetch.",
              javaType = "String")
    String EXTRACTION_STATUS = HEADER_PREFIX + "ExtractionStatus";

    @Metadata(description = "The extracted text of textExtractionUploadAndFetch and textExtractionAndFetch, which is also"
                            + " the body.",
              javaType = "String")
    String EXTRACTED_TEXT = HEADER_PREFIX + "ExtractedText";

    // COS File Operations
    @Metadata(description = "The Cloud Object Storage bucket of the file that textExtractionReadFile,"
                            + " textExtractionDeleteFile (default the resultBucket) and textClassificationDeleteFile"
                            + " (default the documentBucket) read or delete.",
              javaType = "String")
    String BUCKET_NAME = HEADER_PREFIX + "BucketName";

    @Metadata(description = "Whether textExtractionUploadFile or textClassificationUploadFile uploaded the file, which is"
                            + " also the body.",
              javaType = "Boolean")
    String UPLOAD_SUCCESS = HEADER_PREFIX + "UploadSuccess";

    @Metadata(description = "Whether the DeleteFile or DeleteRequest operation of text extraction or text classification"
                            + " deleted the file or the request, which is also the body.",
              javaType = "Boolean")
    String DELETE_SUCCESS = HEADER_PREFIX + "DeleteSuccess";

    // Text Classification
    @Metadata(description = "The ID of the text classification request: set by textClassification,"
                            + " textClassificationUpload and textClassificationFetch, and read by textClassificationFetch"
                            + " and textClassificationDeleteRequest in the place of the body.",
              javaType = "String")
    String CLASSIFICATION_ID = HEADER_PREFIX + "ClassificationId";

    @Metadata(description = "The status of the text classification request, such as submitted, running, completed or"
                            + " failed, set by the text classification operations that start, fetch or wait for it.",
              javaType = "String")
    String CLASSIFICATION_STATUS = HEADER_PREFIX + "ClassificationStatus";

    @Metadata(description = "The document type that text classification found, set by textClassificationFetch and the"
                            + " AndFetch operations of text classification.",
              javaType = "String")
    String CLASSIFICATION_RESULT = HEADER_PREFIX + "ClassificationResult";

    @Metadata(description = "Whether text classification classified the document, set by textClassificationFetch and the"
                            + " AndFetch operations of text classification.",
              javaType = "Boolean")
    String DOCUMENT_CLASSIFIED = HEADER_PREFIX + "DocumentClassified";

    @Metadata(description = "The error message of a failed text extraction or text classification request, set by"
                            + " textExtractionFetch and textClassificationFetch.",
              javaType = "String")
    String ERROR_MESSAGE = HEADER_PREFIX + "ErrorMessage";

    @Metadata(description = "The error code of a failed text extraction or text classification request, set by"
                            + " textExtractionFetch and textClassificationFetch.",
              javaType = "String")
    String ERROR_CODE = HEADER_PREFIX + "ErrorCode";

    // Detection
    @Metadata(description = "The detectors of detect, detectContext and detectGenerated, as a List of"
                            + " com.ibm.watsonx.ai.detection.detector.BaseDetector. Overrides the detectors, detectPii and"
                            + " detectHap options.",
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
    @Metadata(description = "The java.util.function.Consumer of String that receives each chunk of"
                            + " textGenerationStreaming and chatStreaming. Overrides the streamEndpoint option; one of them"
                            + " is required for these operations.",
              javaType = "java.util.function.Consumer")
    String STREAM_CONSUMER = HEADER_PREFIX + "StreamConsumer";

    // Usage/Metadata
    @Metadata(description = "The number of tokens of the input, set by text generation, chat, embedding and rerank.",
              javaType = "Integer")
    String INPUT_TOKEN_COUNT = HEADER_PREFIX + "InputTokenCount";

    @Metadata(description = "The number of tokens that text generation or chat generated.", javaType = "Integer")
    String OUTPUT_TOKEN_COUNT = HEADER_PREFIX + "OutputTokenCount";

    @Metadata(description = "Why the model stopped generating, as watsonx.ai reports it, such as max_tokens or eos_token"
                            + " for text generation and stop, length or tool_calls for chat.",
              javaType = "String")
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

    @Metadata(description = "The forecast of forecast and deploymentForecast, which is also the body.",
              javaType = "java.util.List")
    String FORECAST_RESULTS = HEADER_PREFIX + "ForecastResults";

    @Metadata(description = "The number of data points that forecast or deploymentForecast received.", javaType = "Integer")
    String FORECAST_INPUT_DATA_POINTS = HEADER_PREFIX + "ForecastInputDataPoints";

    @Metadata(description = "The number of data points that forecast or deploymentForecast returned.", javaType = "Integer")
    String FORECAST_OUTPUT_DATA_POINTS = HEADER_PREFIX + "ForecastOutputDataPoints";

    // Foundation Models
    @Metadata(description = "The foundation models that listModels returns, which are also the body.",
              javaType = "java.util.List")
    String FOUNDATION_MODELS = HEADER_PREFIX + "FoundationModels";

    @Metadata(description = "The tasks of the foundation models that listTasks returns, which are also the body.",
              javaType = "java.util.List")
    String FOUNDATION_MODEL_TASKS = HEADER_PREFIX + "FoundationModelTasks";

    @Metadata(description = "The filter of listModels, in the syntax of the filters parameter of watsonx.ai, such as"
                            + " function_text_chat, modelid_ibm/granite-4-h-small or function_rerank,!function_embedding:and"
                            + " (or a com.ibm.watsonx.ai.foundationmodel.filter.Filter)",
              javaType = "String")
    String FOUNDATION_MODEL_FILTER = HEADER_PREFIX + "FoundationModelFilter";

    @Metadata(description = "Whether listModels includes the tech preview models", javaType = "Boolean")
    String FOUNDATION_MODEL_TECH_PREVIEW = HEADER_PREFIX + "FoundationModelTechPreview";

    // Tool Service
    @Metadata(description = "The name of the utility tool that runTool runs with the body as its input, such as"
                            + " GoogleSearch or Wikipedia. Required for runTool unless the CamelIBMWatsonxAiToolRequest"
                            + " header is set.",
              javaType = "String")
    String TOOL_NAME = HEADER_PREFIX + "ToolName";

    @Metadata(description = "The complete request of runTool, in the place of the CamelIBMWatsonxAiToolName and"
                            + " CamelIBMWatsonxAiToolConfig headers and the body.",
              javaType = "com.ibm.watsonx.ai.tool.ToolRequest")
    String TOOL_REQUEST = HEADER_PREFIX + "ToolRequest";

    @Metadata(description = "The configuration of the utility tool, as a Map, when runTool builds the request from the"
                            + " CamelIBMWatsonxAiToolName header.",
              javaType = "java.util.Map")
    String TOOL_CONFIG = HEADER_PREFIX + "ToolConfig";

    @Metadata(description = "The utility tools that listTools returns, which are also the body.", javaType = "java.util.List")
    String UTILITY_TOOLS = HEADER_PREFIX + "UtilityTools";

    @Metadata(description = "The tool registry that processToolCalls runs the tool calls of the model with. Required for"
                            + " processToolCalls, which keeps it on the message for the next chat.",
              javaType = "com.ibm.watsonx.ai.chat.ToolRegistry")
    String TOOL_REGISTRY = HEADER_PREFIX + "ToolRegistry";

    @Metadata(description = "The tool calls of the answer of chat, chatStreaming or deploymentChat. Removed when the"
                            + " answer has none, and by processToolCalls once it ran them.",
              javaType = "java.util.List<com.ibm.watsonx.ai.chat.model.ToolCall>")
    String TOOL_CALLS = HEADER_PREFIX + "ToolCalls";

    @Metadata(description = "Whether the answer of chat, chatStreaming or deploymentChat calls tools. processToolCalls"
                            + " sets it to false once it ran them.",
              javaType = "Boolean")
    String HAS_TOOL_CALLS = HEADER_PREFIX + "HasToolCalls";

    @Metadata(description = "The first answer of chat, chatStreaming or deploymentChat, with its tool calls."
                            + " processToolCalls reads it, which it requires, and removes it.",
              javaType = "com.ibm.watsonx.ai.chat.model.AssistantMessage")
    String ASSISTANT_MESSAGE = HEADER_PREFIX + "AssistantMessage";
}
