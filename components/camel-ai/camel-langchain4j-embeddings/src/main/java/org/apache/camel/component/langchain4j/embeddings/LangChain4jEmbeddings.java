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
package org.apache.camel.component.langchain4j.embeddings;

import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.spi.Metadata;

public final class LangChain4jEmbeddings {
    public static final String SCHEME = "langchain4j-embeddings";

    private LangChain4jEmbeddings() {
    }

    public static class Headers {
        @Metadata(description = "The Finish Reason.", javaType = "dev.langchain4j.model.output.FinishReason")
        public static final String FINISH_REASON = "CamelLangChain4jEmbeddingsFinishReason";

        @Metadata(description = "The Input Token Count.", javaType = "int")
        public static final String INPUT_TOKEN_COUNT = "CamelLangChain4jEmbeddingsInputTokenCount";

        @Metadata(description = "The Output Token Count.", javaType = "int")
        public static final String OUTPUT_TOKEN_COUNT = "CamelLangChain4jEmbeddingsOutputTokenCount";

        @Metadata(description = "The Total Token Count.", javaType = "int")
        public static final String TOTAL_TOKEN_COUNT = "CamelLangChain4jEmbeddingsTotalTokenCount";

        @Metadata(description = "A dense vector embedding of a text", javaType = "float[]")
        public static final String VECTOR = CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR;

        @Metadata(description = "A TextSegment representation of the vector embedding input text",
                  javaType = " dev.langchain4j.data.segment.TextSegment")
        public static final String TEXT_SEGMENT = CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_TEXT_SEGMENT;
    }
}
