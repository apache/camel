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

package org.apache.camel.component.milvus.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.UpsertParam;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.util.ObjectHelper;

/**
 * Maps a LangChain4j Embeddings to a Milvus InsertParam/Upsert Param to write an embeddings vector on a Milvus
 * Database.
 */
@DataTypeTransformer(
        name = "milvus:embeddings",
        description = "Prepares the message to become an object writable by Milvus component")
public class MilvusEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding =
                message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        String textFieldName = message.getHeader(MilvusHeaders.TEXT_FIELD_NAME, () -> "text", String.class);
        String vectorFieldName = message.getHeader(MilvusHeaders.VECTOR_FIELD_NAME, () -> "vector", String.class);
        String collectionName = message.getHeader(MilvusHeaders.COLLECTION_NAME, () -> "embeddings", String.class);
        String keyName = message.getHeader(MilvusHeaders.KEY_NAME, () -> "id", String.class);
        Object keyValue = message.getHeader(MilvusHeaders.KEY_VALUE, () -> null);
        TextSegment text = message.getBody(TextSegment.class);
        final MilvusAction action = message.getHeader(MilvusHeaders.ACTION, MilvusAction.class);
        switch (action) {
            case INSERT -> insertEmbeddingOperation(
                    message, embedding, vectorFieldName, textFieldName, text, collectionName, keyValue, keyName);
            case UPSERT -> upsertEmbeddingOperation(
                    message, embedding, vectorFieldName, textFieldName, text, collectionName, keyValue, keyName);
            default -> throw new IllegalStateException("The only operations supported are insert and upsert");
        }
    }

    private static void insertEmbeddingOperation(
            Message message,
            Embedding embedding,
            String vectorFieldName,
            String textFieldName,
            TextSegment text,
            String collectionName,
            Object keyValue,
            String keyName) {
        List<InsertParam.Field> fields = new ArrayList<>();
        ArrayList list = new ArrayList<>();
        list.add(embedding.vectorAsList());
        fields.add(new InsertParam.Field(vectorFieldName, list));
        fields.add(new InsertParam.Field(textFieldName, Collections.singletonList(text.text())));

        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(keyName)) {
            ArrayList keyValues = new ArrayList<>();
            keyValues.add(keyValue);
            fields.add(new InsertParam.Field(keyName, keyValues));
        }

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        message.setBody(insertParam);
    }

    private static void upsertEmbeddingOperation(
            Message message,
            Embedding embedding,
            String vectorFieldName,
            String textFieldName,
            TextSegment text,
            String collectionName,
            Object keyValue,
            String keyName) {
        List<InsertParam.Field> fields = new ArrayList<>();
        ArrayList list = new ArrayList<>();
        list.add(embedding.vectorAsList());
        fields.add(new UpsertParam.Field(vectorFieldName, list));
        fields.add(new UpsertParam.Field(textFieldName, Collections.singletonList(text.text())));
        if (ObjectHelper.isNotEmpty(keyValue) && ObjectHelper.isNotEmpty(keyName)) {
            ArrayList keyValues = new ArrayList<>();
            keyValues.add(keyValue);
            fields.add(new UpsertParam.Field(keyName, keyValues));
        }

        UpsertParam upsertParam = UpsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        message.setBody(upsertParam);
    }
}
