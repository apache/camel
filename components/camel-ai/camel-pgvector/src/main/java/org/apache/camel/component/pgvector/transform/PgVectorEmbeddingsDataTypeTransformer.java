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
package org.apache.camel.component.pgvector.transform;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pgvector.PgVectorHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a LangChain4j Embeddings to a PgVector upsert message to write an embeddings vector on a PostgreSQL pgvector
 * database.
 */
@DataTypeTransformer(name = "pgvector:embeddings",
                     description = "Prepares the message to become an object writable by PgVector component")
public class PgVectorEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding
                = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        if (embedding == null) {
            throw new IllegalArgumentException(
                    "Missing embedding vector header '" + CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR
                                               + "'. Ensure the langchain4j-embeddings component is called before this transformer.");
        }
        TextSegment text = message.getBody(TextSegment.class);

        String id = message.getHeader(PgVectorHeaders.RECORD_ID, () -> UUID.randomUUID().toString(), String.class);
        message.setHeader(PgVectorHeaders.RECORD_ID, id);

        message.setBody(embedding.vectorAsList());

        if (text != null) {
            message.setHeader(PgVectorHeaders.TEXT_CONTENT, text.text());
            Metadata metadata = text.metadata();
            if (metadata != null && !metadata.toMap().isEmpty()) {
                message.setHeader(PgVectorHeaders.METADATA, toJson(metadata.toMap()));
            }
        }
    }

    private static String toJson(Map<String, Object> map) {
        return "{" + map.entrySet().stream()
                .map(e -> "\"" + escapeJson(String.valueOf(e.getKey())) + "\":" + formatJsonValue(e.getValue()))
                .collect(Collectors.joining(","))
               + "}";
    }

    private static String formatJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
