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

package org.apache.camel.component.springai.embeddings;

import org.apache.camel.spi.Metadata;

public class SpringAiEmbeddingsHeaders {

    @Metadata(
            description = "The embedding response metadata",
            javaType = "org.springframework.ai.embedding.EmbeddingResponseMetadata")
    public static final String EMBEDDING_METADATA = "CamelSpringAiEmbeddingMetadata";

    @Metadata(description = "The index of the embedding in the response", javaType = "Integer")
    public static final String EMBEDDING_INDEX = "CamelSpringAiEmbeddingIndex";

    @Metadata(description = "The Embedding object", javaType = "org.springframework.ai.embedding.Embedding")
    public static final String EMBEDDING = "CamelSpringAiEmbedding";

    @Metadata(description = "The input text that was embedded", javaType = "String")
    public static final String INPUT_TEXT = "CamelSpringAiEmbeddingInputText";

    @Metadata(
            description = "List of Embedding objects",
            javaType = "java.util.List<org.springframework.ai.embedding.Embedding>")
    public static final String EMBEDDINGS = "CamelSpringAiEmbeddings";

    @Metadata(description = "List of input texts that were embedded", javaType = "java.util.List<String>")
    public static final String INPUT_TEXTS = "CamelSpringAiEmbeddingInputTexts";
}
