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
package org.apache.camel.component.langchain.embeddings;

import org.apache.camel.spi.Metadata;

public final class LangChainEmbeddings {
    public static final String SCHEME = "langchain-embeddings";

    private LangChainEmbeddings() {
    }

    public static class Headers {
        @Metadata(description = "The Finish Reason.", javaType = "dev.langchain4j.model.output.FinishReason")
        public static final String FINISH_REASON = "CamelLangChainEmbeddingsFinishReason";

        @Metadata(description = "The Input Token Count.", javaType = "int")
        public static final String INPUT_TOKEN_COUNT = "CamelLangChainEmbeddingsInputTokenCount";

        @Metadata(description = "The Output Token Count.", javaType = "int")
        public static final String OUTPUT_TOKEN_COUNT = "CamelLangChainEmbeddingsOutputTokenCount";

        @Metadata(description = "The Total Token Count.", javaType = "int")
        public static final String TOTAL_TOKEN_COUNT = "CamelLangChainEmbeddingsTotalTokenCount";

        @Metadata(description = "A dense vector embedding of a text", javaType = "float[]")
        public static final String VECTOR = "CamelLangChainEmbeddingsVector";
    }
}
