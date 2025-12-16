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
package org.apache.camel.component.qdrant.transform;

import java.util.UUID;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.qdrant.QdrantHeaders;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a LangChain4j Embeddings to a Qdrant PointStruct to write an embeddings vector on a Qdrant Database.
 */
@DataTypeTransformer(name = "qdrant:embeddings",
                     description = "Prepares the message to become an object writable by Qdrant component")
public class QdrantEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        TextSegment text = message.getBody(TextSegment.class);
        Common.PointId id
                = message.getHeader(QdrantHeaders.POINT_ID, () -> PointIdFactory.id(UUID.randomUUID()), Common.PointId.class);

        var builder = Points.PointStruct.newBuilder();
        builder.setId(id);
        builder.setVectors(VectorsFactory.vectors(embedding.vector()));

        if (text != null) {
            builder.putPayload("text_segment", ValueFactory.value(text.text()));
            Metadata metadata = text.metadata();
            metadata.toMap()
                    .forEach((key, value) -> builder.putPayload(key, ValueFactory.value((String) value)));

        }

        message.setBody(builder.build());
    }
}
