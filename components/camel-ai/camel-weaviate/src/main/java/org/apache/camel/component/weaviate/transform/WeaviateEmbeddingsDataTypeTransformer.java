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

package org.apache.camel.component.weaviate.transform;

import java.util.HashMap;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.weaviate.WeaviateVectorDbAction;
import org.apache.camel.component.weaviate.WeaviateVectorDbHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ObjectHelper;

/**
 * Maps a LangChain4j Embeddings to a Weaviate Create/UpdateByID to write an embeddings vector on a Weaviate Database.
 */
@DataTypeTransformer(
        name = "weaviate:embeddings",
        description = "Prepares the message to become an object writable by Weaviate component")
public class WeaviateEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding =
                message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        String textFieldName = message.getHeader(WeaviateVectorDbHeaders.TEXT_FIELD_NAME, () -> "text", String.class);
        String vectorFieldName =
                message.getHeader(WeaviateVectorDbHeaders.VECTOR_FIELD_NAME, () -> "vector", String.class);
        String collectionName =
                message.getHeader(WeaviateVectorDbHeaders.COLLECTION_NAME, () -> "embeddings", String.class);
        String keyName = message.getHeader(WeaviateVectorDbHeaders.KEY_NAME, () -> "id", String.class);
        Object keyValue = message.getHeader(WeaviateVectorDbHeaders.KEY_VALUE, () -> null);
        TextSegment text = message.getBody(TextSegment.class);
        final WeaviateVectorDbAction action =
                message.getHeader(WeaviateVectorDbHeaders.ACTION, WeaviateVectorDbAction.class);
        switch (action) {
            case CREATE -> createEmbeddingOperation(
                    message, embedding, vectorFieldName, textFieldName, text, collectionName, keyValue, keyName);
            case UPDATE_BY_ID -> updateEmbeddingOperation(
                    message, embedding, vectorFieldName, textFieldName, text, collectionName, keyValue, keyName);
            case QUERY -> queryEmbeddingOperation(
                    message, embedding, vectorFieldName, textFieldName, text, collectionName, keyValue, keyName);
            default -> throw new IllegalStateException("The only operations supported are create and updatebyid");
        }
    }

    private static void createEmbeddingOperation(
            Message message,
            Embedding embedding,
            String vectorFieldName,
            String textFieldName,
            TextSegment text,
            String collectionName,
            Object keyValue,
            String keyName) {
        message.setBody(embedding.vectorAsList(), List.class);

        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(keyName)) {
            HashMap<String, Object> maps = new HashMap<String, Object>();
            maps.put(keyName, keyValue);
            message.setHeader(WeaviateVectorDbHeaders.PROPERTIES, maps);
        }
    }

    private static void updateEmbeddingOperation(
            Message message,
            Embedding embedding,
            String vectorFieldName,
            String textFieldName,
            TextSegment text,
            String collectionName,
            Object keyValue,
            String keyName) {
        message.setBody(embedding.vectorAsList(), List.class);

        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(keyName)) {
            HashMap<String, Object> maps = new HashMap<String, Object>();
            maps.put(keyName, keyValue);
            message.setHeader(WeaviateVectorDbHeaders.PROPERTIES, maps);
        }
    }

    private static void queryEmbeddingOperation(
            Message message,
            Embedding embedding,
            String vectorFieldName,
            String textFieldName,
            TextSegment text,
            String collectionName,
            Object keyValue,
            String keyName) {
        message.setBody(embedding.vectorAsList(), List.class);

        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(keyName)) {
            HashMap<String, Object> maps = new HashMap<String, Object>();
            maps.put(keyName, keyValue);
            message.setHeader(WeaviateVectorDbHeaders.PROPERTIES, maps);
        }
    }
}
