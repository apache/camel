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

package org.apache.camel.component.pinecone.transform;

import java.util.UUID;

import dev.langchain4j.data.embedding.Embedding;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pinecone.PineconeVectorDbHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a LangChain4j Embeddings to a Pinecone InsertParam/Upsert Param to write an embeddings vector on a Pinecone
 * Database.
 */
@DataTypeTransformer(
        name = "pinecone:embeddings",
        description = "Prepares the message to become an object writable by Pinecone component")
public class PineconeEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding =
                message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        String indexId = message.getHeader(PineconeVectorDbHeaders.INDEX_ID, UUID.randomUUID(), String.class);
        String indexName = message.getHeader(PineconeVectorDbHeaders.INDEX_NAME, "embeddings", String.class);

        message.setHeader(PineconeVectorDbHeaders.INDEX_NAME, indexName);
        message.setHeader(PineconeVectorDbHeaders.INDEX_ID, indexId);
        message.setBody(embedding.vectorAsList());
    }
}
