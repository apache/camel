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
import org.apache.camel.Message;
import org.apache.camel.component.milvus.Milvus;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a LangChain4j Embeddings to a Milvus InsertParam/Upsert Param to write an embeddings vector on a Milvus
 * Database.
 */
@DataTypeTransformer(name = "milvus:embeddings",
                     description = "Prepares the message to become an object writable by Milvus component")
public class MilvusEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding = message.getHeader("CamelLangChain4jEmbeddingsVector", Embedding.class);
        String textFieldName = message.getHeader(Milvus.Headers.TEXT_FIELD_NAME, () -> "text", String.class);
        String vectorFieldName = message.getHeader(Milvus.Headers.VECTOR_FIELD_NAME, () -> "vector", String.class);
        String collectionName = message.getHeader(Milvus.Headers.COLLECTION_NAME, () -> "embeddings", String.class);
        TextSegment text = message.getBody(TextSegment.class);
        List<InsertParam.Field> fields = new ArrayList<>();
        ArrayList list = new ArrayList<>();
        list.add(embedding.vectorAsList());
        fields.add(new InsertParam.Field(vectorFieldName, list));
        fields.add(new InsertParam.Field(textFieldName, Collections.singletonList(text.text())));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        message.setBody(insertParam);
    }
}
