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

import com.ibm.watsonx.ai.core.auth.Authenticator;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration for IBM watsonx.ai component.
 */
@UriParams
public class WatsonxAiConfiguration implements Cloneable {

    // Authentication
    @UriParam(label = "security", security = "secret",
              description = "The IBM Cloud API key, exchanged at IBM Cloud IAM for an access token that is sent to"
                            + " watsonx.ai. Required unless oauthProfile or authenticator is set, or the operation is"
                            + " processToolCalls, which runs locally; when several are set, authenticator is used first,"
                            + " then oauthProfile.")
    private String apiKey;

    @UriParam(label = "security",
              description = "OAuth profile name for obtaining an access token via the OAuth 2.0 Client Credentials grant."
                            + " When set, the token of the configured identity provider is sent to watsonx.ai as a Bearer"
                            + " token instead of an IBM Cloud API key, and obtained again when it expires. Requires"
                            + " camel-oauth on the classpath.")
    private String oauthProfile;

    @UriParam(label = "security,advanced",
              description = "A custom authenticator of the watsonx.ai SDK (com.ibm.watsonx.ai.core.auth.Authenticator)"
                            + " that gives the token of every request, such as a CP4DAuthenticator for watsonx.ai software"
                            + " on Cloud Pak for Data. When set, apiKey and oauthProfile are not used.")
    private Authenticator authenticator;

    // Connection
    @UriParam(label = "common",
              description = "The watsonx.ai API URL of the region, such as https://us-south.ml.cloud.ibm.com or"
                            + " https://eu-de.ml.cloud.ibm.com. The endpoint does not start without it, unless its"
                            + " operation is runTool or listTools, which call the wxUrl instead, or processToolCalls, which"
                            + " runs locally.")
    private String baseUrl;

    @UriParam(label = "common",
              description = "The watsonx.ai platform (WX) API URL of the region, such as"
                            + " https://api.dataplatform.cloud.ibm.com/wx or"
                            + " https://api.eu-de.dataplatform.cloud.ibm.com/wx, which runTool and listTools call. Required"
                            + " for these two operations.")
    private String wxUrl;

    // Project/Space Context
    @UriParam(label = "common",
              description = "The ID of the watsonx.ai project in which the operations of foundation models"
                            + " (textGeneration, chat, embedding, rerank, tokenize, forecast and their streaming variants),"
                            + " detection, text extraction and text classification run. Set projectId or spaceId."
                            + " deploymentInfo looks the deployment up in this project when no space ID is set.")
    private String projectId;

    @UriParam(label = "common",
              description = "The ID of the deployment space, in the place of projectId, in which the same operations run."
                            + " deploymentInfo looks the deployment up in this space; there the CamelIBMWatsonxAiSpaceId"
                            + " header overrides it.")
    private String spaceId;

    // Model
    @UriParam(label = "producer",
              description = "The ID of the foundation model, such as ibm/granite-4-h-small, of textGeneration, chat,"
                            + " embedding, rerank, tokenize, forecast and their streaming variants. The"
                            + " CamelIBMWatsonxAiModelId header overrides it for chat and chatStreaming. The deployment"
                            + " operations use the model of the deployment instead.")
    private String modelId;

    @UriParam(label = "producer",
              description = "The ID of the deployment of deploymentInfo, deploymentGenerate, deploymentChat and"
                            + " deploymentForecast. The CamelIBMWatsonxAiDeploymentId header overrides it; when neither is"
                            + " set, these operations fail.")
    private String deploymentId;

    // Operation
    @UriParam(label = "producer",
              description = "The operation to perform. The CamelIBMWatsonxAiOperation header overrides it; when neither"
                            + " is set, the exchange fails.")
    private WatsonxAiOperations operation;

    // Text Generation Parameters
    @UriParam(label = "producer",
              description = "The sampling temperature of text generation and chat (the streaming and deployment"
                            + " operations included), from 0 to 2: higher values give more random text. The"
                            + " CamelIBMWatsonxAiTemperature header overrides it.")
    private Double temperature;

    @UriParam(label = "producer",
              description = "The maximum number of tokens that textGeneration, textGenerationStreaming and"
                            + " deploymentGenerate generate. Chat uses maxCompletionTokens instead. The"
                            + " CamelIBMWatsonxAiMaxNewTokens header overrides it.")
    private Integer maxNewTokens;

    @UriParam(label = "producer",
              description = "The nucleus sampling of text generation and chat: the model picks the next token from the"
                            + " most likely tokens whose probabilities add up to this value (0 to 1). The"
                            + " CamelIBMWatsonxAiTopP header overrides it.")
    private Double topP;

    @UriParam(label = "producer",
              description = "The top-k sampling of text generation: the model picks the next token from this number of"
                            + " most likely tokens. Chat does not use it. The CamelIBMWatsonxAiTopK header overrides it.")
    private Integer topK;

    @UriParam(label = "producer",
              description = "The penalty of text generation for repeating tokens it already generated: 1 means no"
                            + " penalty, and higher values repeat less. Chat does not use it (see frequencyPenalty). The"
                            + " CamelIBMWatsonxAiRepetitionPenalty header overrides it.")
    private Double repetitionPenalty;

    // Chat Parameters
    @UriParam(label = "producer",
              description = "The maximum number of tokens of the answer of chat, chatStreaming and deploymentChat. The"
                            + " CamelIBMWatsonxAiMaxNewTokens header overrides it.")
    private Integer maxCompletionTokens;

    @UriParam(label = "producer",
              description = "The penalty of chat for tokens in proportion to how often they already appear, from -2 to 2:"
                            + " positive values make the answer repeat itself less. There is no header for it.")
    private Double frequencyPenalty;

    @UriParam(label = "producer",
              description = "The penalty of chat for any token that already appeared, from -2 to 2: positive values make"
                            + " the model move on to new topics. There is no header for it.")
    private Double presencePenalty;

    @UriParam(label = "producer",
              description = "How the model chooses the tools of chat: auto (the model decides), required (the model must"
                            + " call a tool), none (the model must not call a tool), or the name of the tool the model must"
                            + " call")
    private String toolChoice;

    // Chat output, reasoning, guardrails and streaming
    @UriParam(label = "producer", enums = "text,json",
              description = "The format of the chat answer: text, or json for a JSON object. Set jsonSchema for an answer"
                            + " that follows a JSON schema")
    private String responseFormat;

    @UriParam(label = "producer",
              description = "A JSON schema (as JSON text) that the chat answer follows (the json_schema response format)")
    private String jsonSchema;

    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether the chat answer must follow the jsonSchema strictly")
    private Boolean jsonSchemaStrict = true;

    @UriParam(label = "producer",
              description = "Guided decoding of chat: the answer is one of these comma separated choices")
    private String guidedChoice;

    @UriParam(label = "producer",
              description = "Guided decoding of chat: the answer matches this regular expression")
    private String guidedRegex;

    @UriParam(label = "producer",
              description = "Guided decoding of chat: the answer follows this context free grammar (EBNF)")
    private String guidedGrammar;

    @UriParam(label = "producer",
              description = "The random seed of chat sampling, for repeatable answers")
    private Integer seed;

    @UriParam(label = "producer",
              description = "Comma separated sequences that stop the generation of the chat answer")
    private String stop;

    @UriParam(label = "producer",
              description = "How many answers chat generates for the request (n). The body is the first one, and the"
                            + " CamelIBMWatsonxAiAssistantMessages header has them all")
    private Integer numberOfChoices;

    @UriParam(label = "producer",
              description = "Whether the model of chat reasons before it answers, for the models that support it (chat"
                            + " template thinking)")
    private Boolean thinking;

    @UriParam(label = "producer",
              description = "Whether watsonx.ai returns the reasoning of the model, in the"
                            + " CamelIBMWatsonxAiReasoningContent header")
    private Boolean includeReasoning;

    @UriParam(label = "producer", enums = "low,medium,high",
              description = "How much the model of chat reasons: low, medium or high, for the models that support it")
    private String reasoningEffort;

    @UriParam(label = "producer",
              description = "For models that write their reasoning in the answer between tags: the tag of the reasoning,"
                            + " and optionally the tag of the answer after a comma, such as think,response. The reasoning goes"
                            + " to the CamelIBMWatsonxAiReasoningContent header and the answer to the body")
    private String thinkingTags;

    @UriParam(label = "producer",
              description = "Chat guardrail: whether watsonx.ai checks the input and the answer for hate, abuse and"
                            + " profanity (HAP)")
    private Boolean moderationHap;

    @UriParam(label = "producer",
              description = "Chat guardrail: whether watsonx.ai checks the input and the answer for personal identifiable"
                            + " information (PII)")
    private Boolean moderationPii;

    @UriParam(label = "producer",
              description = "Chat guardrail: whether watsonx.ai checks the input with Granite Guardian (watsonx.ai applies"
                            + " it to the input only)")
    private Boolean moderationGraniteGuardian;

    @UriParam(label = "producer", defaultValue = "0.5",
              description = "The threshold (0 to 1) of the HAP and Granite Guardian chat guardrails")
    private Float moderationThreshold = 0.5f;

    @UriParam(label = "producer",
              description = "The endpoint URI, such as seda:chunks, that receives each chunk of textGenerationStreaming"
                            + " and chatStreaming as a message when the CamelIBMWatsonxAiStreamConsumer header is not set."
                            + " The chunks are sent from the threads of the watsonx.ai SDK, with the exchange ID of the"
                            + " streaming exchange in the CamelCorrelationId header.")
    private String streamEndpoint;

    // Embedding Parameters
    @UriParam(label = "producer",
              description = "The maximum number of tokens of each input text of embedding: a longer input is truncated"
                            + " from the end, so that the request does not fail.")
    private Integer truncateInputTokens;

    // Rerank Parameters
    @UriParam(label = "producer",
              description = "The number of best ranked inputs that rerank returns. The CamelIBMWatsonxAiRerankTopN header"
                            + " overrides it.")
    private Integer rerankTopN;

    @UriParam(label = "producer",
              description = "Whether each result of rerank includes its input text, besides its index and score.")
    private Boolean returnDocuments;

    // Time series
    @UriParam(label = "producer",
              description = "The number of time steps to forecast (forecast and deploymentForecast). The model forecasts"
                            + " its own default length when not set")
    private Integer predictionLength;

    // Text Extraction (COS)
    @UriParam(label = "producer",
              description = "The endpoint URL of the IBM Cloud Object Storage region of the document and result buckets,"
                            + " such as https://s3.us-south.cloud-object-storage.appdomain.cloud. Required for text"
                            + " extraction and text classification.")
    private String cosUrl;

    @UriParam(label = "producer",
              description = "The ID of the connection asset, in the project or space, that gives watsonx.ai access to the"
                            + " documentBucket. Required for text extraction and text classification.")
    private String documentConnectionId;

    @UriParam(label = "producer",
              description = "The Cloud Object Storage bucket of the documents of text extraction and text classification:"
                            + " the upload operations upload into it, and the operations with a file path read the document"
                            + " from it. Required for these operations.")
    private String documentBucket;

    @UriParam(label = "producer",
              description = "The ID of the connection asset, in the project or space, that gives watsonx.ai access to the"
                            + " resultBucket. Required for text extraction.")
    private String resultConnectionId;

    @UriParam(label = "producer",
              description = "The Cloud Object Storage bucket where text extraction writes its output files, which"
                            + " textExtractionReadFile and textExtractionDeleteFile read and delete unless the"
                            + " CamelIBMWatsonxAiBucketName header names another bucket. Required for text extraction.")
    private String resultBucket;

    // Text extraction and classification parameters
    @UriParam(label = "producer",
              description = "The outputs of text extraction, comma separated: md (the default), json, html, plain_text,"
                            + " page_images. The operations that return the text (AndFetch) take one output, but"
                            + " page_images")
    private String extractionOutputs;

    @UriParam(label = "producer", enums = "standard,high_quality",
              description = "The quality of text extraction: standard or high_quality")
    private String extractionMode;

    @UriParam(label = "producer", enums = "auto,disabled,enabled,forced",
              description = "The optical character recognition of text extraction and classification: auto, disabled,"
                            + " enabled or forced")
    private String ocrMode;

    @UriParam(label = "producer",
              description = "The languages of the documents of text extraction and classification, comma separated ISO"
                            + " 639 codes, such as en,fr")
    private String documentLanguages;

    @UriParam(label = "producer",
              description = "Whether text extraction and classification correct the rotation of the pages")
    private Boolean autoRotationCorrection;

    @UriParam(label = "producer",
              enums = "disabled,enabled_placeholder,enabled_text,enabled_verbalization,enabled_verbalization_all",
              description = "What text extraction does with the images of the document")
    private String embeddedImages;

    @UriParam(label = "producer", description = "The resolution, in dots per inch, of the images that text extraction outputs.")
    private Integer outputDpi;

    @UriParam(label = "producer",
              description = "Whether text extraction gives the tokens of the text, with their positions")
    private Boolean outputTokens;

    @UriParam(label = "producer", enums = "disabled,invoice,ubill,generic_with_semantic",
              description = "The key-value pair extraction of text extraction: disabled, invoice, ubill or"
                            + " generic_with_semantic. When not set, text extraction extracts no key-value pairs.")
    private String kvpMode;

    @UriParam(label = "producer", enums = "exact,binary",
              description = "How text classification classifies: exact (the document type) or binary (whether it is one)")
    private String classificationMode;

    @UriParam(label = "producer",
              description = "Whether the operations that upload and wait (UploadAndFetch) remove the uploaded document"
                            + " from Cloud Object Storage once the result is read (asynchronously)")
    private Boolean removeUploadedFile;

    @UriParam(label = "producer",
              description = "Whether the text extraction operations that wait (AndFetch) remove the output file from Cloud"
                            + " Object Storage once read (asynchronously)")
    private Boolean removeOutputFile;

    @UriParam(label = "producer", javaType = "java.time.Duration",
              description = "How long the text extraction and classification operations that wait (AndFetch) wait for"
                            + " the result, in milliseconds. Defaults to the timeout of the endpoint")
    private Long processingTimeout;

    // Detection
    @UriParam(label = "producer",
              description = "Whether detect looks for personal identifiable information (PII), when neither the detectors"
                            + " option nor the CamelIBMWatsonxAiDetectors header is set. When neither detectPii nor"
                            + " detectHap is set, detect uses both detectors.")
    private Boolean detectPii;

    @UriParam(label = "producer",
              description = "Whether detect looks for hate, abuse and profanity (HAP), when neither the detectors option"
                            + " nor the CamelIBMWatsonxAiDetectors header is set. When neither detectPii nor detectHap is"
                            + " set, detect uses both detectors.")
    private Boolean detectHap;

    @UriParam(label = "producer",
              description = "The threshold (0 to 1) of the detectors of detect, detectContext and detectGenerated, but"
                            + " the PII detector, which takes none. The defaults of watsonx.ai apply when not set.")
    private Double detectionThreshold;

    @UriParam(label = "producer",
              description = "The detectors, comma separated, in the place of detectPii and detectHap: hap, pii and"
                            + " granite_guardian for detect, context_relevance and faithfulness for detectContext (default"
                            + " context_relevance), answer_relevance for detectGenerated (default answer_relevance). The"
                            + " detectionThreshold applies to all but pii")
    private String detectors;

    @UriParam(label = "producer",
              description = "The watsonx.governance instance that runs the detectors of detectContext and detectGenerated"
                            + " (and of detect, in the place of the project or space), which IBM Cloud requires for them")
    private String governanceInstanceId;

    // Advanced
    @UriParam(label = "advanced",
              description = "The timeout of each HTTP request to watsonx.ai and to IBM Cloud IAM, in milliseconds; the"
                            + " watsonx.ai SDK uses 60 seconds when not set. The streaming operations wait for the end of"
                            + " the stream at most this timeout and 30 seconds more.")
    private Long timeout;

    @UriParam(label = "security", defaultValue = "true",
              description = "Whether the watsonx.ai SDK verifies the TLS certificates of watsonx.ai, IBM Cloud IAM and"
                            + " Cloud Object Storage. Set it to false only for testing, as the connections then accept any"
                            + " certificate.",
              security = "insecure:ssl", insecureValue = "false")
    private Boolean verifySsl = true;

    @UriParam(label = "advanced", defaultValue = "false", description = "Whether to log HTTP requests to the watsonx.ai API")
    private Boolean logRequests = false;

    @UriParam(label = "advanced", defaultValue = "false", description = "Whether to log HTTP responses from the watsonx.ai API")
    private Boolean logResponses = false;

    public WatsonxAiConfiguration copy() {
        try {
            return (WatsonxAiConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    // Getters and Setters

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOauthProfile() {
        return oauthProfile;
    }

    public void setOauthProfile(String oauthProfile) {
        this.oauthProfile = oauthProfile;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWxUrl() {
        return wxUrl;
    }

    public void setWxUrl(String wxUrl) {
        this.wxUrl = wxUrl;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(String spaceId) {
        this.spaceId = spaceId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public WatsonxAiOperations getOperation() {
        return operation;
    }

    public void setOperation(WatsonxAiOperations operation) {
        this.operation = operation;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxNewTokens() {
        return maxNewTokens;
    }

    public void setMaxNewTokens(Integer maxNewTokens) {
        this.maxNewTokens = maxNewTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public void setRepetitionPenalty(Double repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public Integer getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(Integer maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
    }

    public String getResponseFormat() {
        return responseFormat;
    }

    public void setResponseFormat(String responseFormat) {
        this.responseFormat = responseFormat;
    }

    public String getJsonSchema() {
        return jsonSchema;
    }

    public void setJsonSchema(String jsonSchema) {
        this.jsonSchema = jsonSchema;
    }

    public Boolean getJsonSchemaStrict() {
        return jsonSchemaStrict;
    }

    public void setJsonSchemaStrict(Boolean jsonSchemaStrict) {
        this.jsonSchemaStrict = jsonSchemaStrict;
    }

    public String getGuidedChoice() {
        return guidedChoice;
    }

    public void setGuidedChoice(String guidedChoice) {
        this.guidedChoice = guidedChoice;
    }

    public String getGuidedRegex() {
        return guidedRegex;
    }

    public void setGuidedRegex(String guidedRegex) {
        this.guidedRegex = guidedRegex;
    }

    public String getGuidedGrammar() {
        return guidedGrammar;
    }

    public void setGuidedGrammar(String guidedGrammar) {
        this.guidedGrammar = guidedGrammar;
    }

    public Integer getSeed() {
        return seed;
    }

    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    public String getStop() {
        return stop;
    }

    public void setStop(String stop) {
        this.stop = stop;
    }

    public Integer getNumberOfChoices() {
        return numberOfChoices;
    }

    public void setNumberOfChoices(Integer numberOfChoices) {
        this.numberOfChoices = numberOfChoices;
    }

    public Boolean getThinking() {
        return thinking;
    }

    public void setThinking(Boolean thinking) {
        this.thinking = thinking;
    }

    public Boolean getIncludeReasoning() {
        return includeReasoning;
    }

    public void setIncludeReasoning(Boolean includeReasoning) {
        this.includeReasoning = includeReasoning;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public String getThinkingTags() {
        return thinkingTags;
    }

    public void setThinkingTags(String thinkingTags) {
        this.thinkingTags = thinkingTags;
    }

    public Boolean getModerationHap() {
        return moderationHap;
    }

    public void setModerationHap(Boolean moderationHap) {
        this.moderationHap = moderationHap;
    }

    public Boolean getModerationPii() {
        return moderationPii;
    }

    public void setModerationPii(Boolean moderationPii) {
        this.moderationPii = moderationPii;
    }

    public Boolean getModerationGraniteGuardian() {
        return moderationGraniteGuardian;
    }

    public void setModerationGraniteGuardian(Boolean moderationGraniteGuardian) {
        this.moderationGraniteGuardian = moderationGraniteGuardian;
    }

    public Float getModerationThreshold() {
        return moderationThreshold;
    }

    public void setModerationThreshold(Float moderationThreshold) {
        this.moderationThreshold = moderationThreshold;
    }

    public String getStreamEndpoint() {
        return streamEndpoint;
    }

    public void setStreamEndpoint(String streamEndpoint) {
        this.streamEndpoint = streamEndpoint;
    }

    public Integer getTruncateInputTokens() {
        return truncateInputTokens;
    }

    public void setTruncateInputTokens(Integer truncateInputTokens) {
        this.truncateInputTokens = truncateInputTokens;
    }

    public Integer getRerankTopN() {
        return rerankTopN;
    }

    public void setRerankTopN(Integer rerankTopN) {
        this.rerankTopN = rerankTopN;
    }

    public Boolean getReturnDocuments() {
        return returnDocuments;
    }

    public void setReturnDocuments(Boolean returnDocuments) {
        this.returnDocuments = returnDocuments;
    }

    public Integer getPredictionLength() {
        return predictionLength;
    }

    public void setPredictionLength(Integer predictionLength) {
        this.predictionLength = predictionLength;
    }

    public String getCosUrl() {
        return cosUrl;
    }

    public void setCosUrl(String cosUrl) {
        this.cosUrl = cosUrl;
    }

    public String getDocumentConnectionId() {
        return documentConnectionId;
    }

    public void setDocumentConnectionId(String documentConnectionId) {
        this.documentConnectionId = documentConnectionId;
    }

    public String getDocumentBucket() {
        return documentBucket;
    }

    public void setDocumentBucket(String documentBucket) {
        this.documentBucket = documentBucket;
    }

    public String getResultConnectionId() {
        return resultConnectionId;
    }

    public void setResultConnectionId(String resultConnectionId) {
        this.resultConnectionId = resultConnectionId;
    }

    public String getResultBucket() {
        return resultBucket;
    }

    public void setResultBucket(String resultBucket) {
        this.resultBucket = resultBucket;
    }

    public String getExtractionOutputs() {
        return extractionOutputs;
    }

    public void setExtractionOutputs(String extractionOutputs) {
        this.extractionOutputs = extractionOutputs;
    }

    public String getExtractionMode() {
        return extractionMode;
    }

    public void setExtractionMode(String extractionMode) {
        this.extractionMode = extractionMode;
    }

    public String getOcrMode() {
        return ocrMode;
    }

    public void setOcrMode(String ocrMode) {
        this.ocrMode = ocrMode;
    }

    public String getDocumentLanguages() {
        return documentLanguages;
    }

    public void setDocumentLanguages(String documentLanguages) {
        this.documentLanguages = documentLanguages;
    }

    public Boolean getAutoRotationCorrection() {
        return autoRotationCorrection;
    }

    public void setAutoRotationCorrection(Boolean autoRotationCorrection) {
        this.autoRotationCorrection = autoRotationCorrection;
    }

    public String getEmbeddedImages() {
        return embeddedImages;
    }

    public void setEmbeddedImages(String embeddedImages) {
        this.embeddedImages = embeddedImages;
    }

    public Integer getOutputDpi() {
        return outputDpi;
    }

    public void setOutputDpi(Integer outputDpi) {
        this.outputDpi = outputDpi;
    }

    public Boolean getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Boolean outputTokens) {
        this.outputTokens = outputTokens;
    }

    public String getKvpMode() {
        return kvpMode;
    }

    public void setKvpMode(String kvpMode) {
        this.kvpMode = kvpMode;
    }

    public String getClassificationMode() {
        return classificationMode;
    }

    public void setClassificationMode(String classificationMode) {
        this.classificationMode = classificationMode;
    }

    public Boolean getRemoveUploadedFile() {
        return removeUploadedFile;
    }

    public void setRemoveUploadedFile(Boolean removeUploadedFile) {
        this.removeUploadedFile = removeUploadedFile;
    }

    public Boolean getRemoveOutputFile() {
        return removeOutputFile;
    }

    public void setRemoveOutputFile(Boolean removeOutputFile) {
        this.removeOutputFile = removeOutputFile;
    }

    public Long getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Long processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Boolean getDetectPii() {
        return detectPii;
    }

    public void setDetectPii(Boolean detectPii) {
        this.detectPii = detectPii;
    }

    public Boolean getDetectHap() {
        return detectHap;
    }

    public void setDetectHap(Boolean detectHap) {
        this.detectHap = detectHap;
    }

    public Double getDetectionThreshold() {
        return detectionThreshold;
    }

    public void setDetectionThreshold(Double detectionThreshold) {
        this.detectionThreshold = detectionThreshold;
    }

    public String getDetectors() {
        return detectors;
    }

    public void setDetectors(String detectors) {
        this.detectors = detectors;
    }

    public String getGovernanceInstanceId() {
        return governanceInstanceId;
    }

    public void setGovernanceInstanceId(String governanceInstanceId) {
        this.governanceInstanceId = governanceInstanceId;
    }

    public Long getTimeout() {
        return timeout;
    }

    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }

    public Boolean getVerifySsl() {
        return verifySsl;
    }

    public void setVerifySsl(Boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests) {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses) {
        this.logResponses = logResponses;
    }
}
