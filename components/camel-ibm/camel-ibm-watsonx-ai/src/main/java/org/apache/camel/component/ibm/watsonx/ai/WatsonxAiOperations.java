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

/**
 * Defines the operations available for the IBM watsonx.ai component.
 */
public enum WatsonxAiOperations {

    // Text Generation (TextGenerationService)
    textGeneration,
    textGenerationStreaming,

    // Chat (ChatService)
    chat,
    chatStreaming,

    // Embeddings (EmbeddingService)
    embedding,

    // Rerank (RerankService)
    rerank,

    // Tokenization (TokenizationService)
    tokenize,

    // Text Extraction (TextExtractionService)
    textExtraction,
    textExtractionFetch,
    textExtractionUpload,
    textExtractionUploadAndFetch,
    textExtractionUploadFile,
    textExtractionReadFile,
    textExtractionDeleteFile,
    textExtractionDeleteRequest,

    // Text Classification (TextClassificationService)
    textClassification,
    textClassificationFetch,
    textClassificationUpload,
    textClassificationUploadAndFetch,
    textClassificationUploadFile,
    textClassificationDeleteFile,
    textClassificationDeleteRequest,

    // Detection - PII/HAP (DetectionService)
    detect,

    // Time Series (TimeSeriesService)
    forecast,

    // Foundation Models (FoundationModelService)
    listModels,
    listTasks,

    // Deployment (DeploymentService)
    deploymentInfo,
    deploymentGenerate,
    deploymentChat,
    deploymentForecast,

    // Tool Service (experimental)
    runTool,
    listTools,
    processToolCalls
}
