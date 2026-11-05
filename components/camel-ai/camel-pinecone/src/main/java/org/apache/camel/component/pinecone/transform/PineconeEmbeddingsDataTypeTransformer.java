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

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pinecone.PineconeVectorDbHeaders;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a LangChain4j Embeddings to a Pinecone InsertParam/Upsert Param to write an embeddings vector on a Pinecone
 * Database.
 */
@DataTypeTransformer(name = "pinecone:embeddings",
                     description = "Prepares the message to become an object writable by Pinecone component")
@BodyTypes(transformerIn = @BodyType(value = Carrier.ANY,
                                     description = "A LangChain4j TextSegment, or a String the converter of"
                                                   + " camel-langchain4j-embeddings turns into one, whose text goes in the"
                                                   + " text_segment field of the CamelPineconeMetadata header, which UPSERT"
                                                   + " and UPDATE store with the vector; another body stores no text. The"
                                                   + " vector comes from the CamelLangChain4jEmbeddingsVector header, which"
                                                   + " must be set"),
           transformerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<java.lang.Float>",
                                      description = "The vector of the embedding"))
public class PineconeEmbeddingsDataTypeTransformer extends Transformer {

    // the metadata field of the text, which the pinecone:rag transformer and the Pinecone store of LangChain4j read
    private static final String TEXT_SEGMENT = "text_segment";

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Embedding embedding = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);
        if (embedding == null) {
            throw new IllegalArgumentException(
                    "Missing embedding vector header '" + CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR
                                               + "'. Ensure the langchain4j-embeddings component is called before this transformer.");
        }
        TextSegment text = message.getBody(TextSegment.class);
        String indexId = message.getHeader(PineconeVectorDbHeaders.INDEX_ID, UUID.randomUUID(), String.class);
        String indexName = message.getHeader(PineconeVectorDbHeaders.INDEX_NAME, "embeddings", String.class);

        message.setHeader(PineconeVectorDbHeaders.INDEX_NAME, indexName);
        message.setHeader(PineconeVectorDbHeaders.INDEX_ID, indexId);
        if (text != null) {
            // added to the metadata the caller may have set, which is kept
            Struct metadata = message.getHeader(PineconeVectorDbHeaders.METADATA, Struct.class);
            Struct.Builder builder = metadata != null ? metadata.toBuilder() : Struct.newBuilder();
            builder.putFields(TEXT_SEGMENT, Value.newBuilder().setStringValue(text.text()).build());
            message.setHeader(PineconeVectorDbHeaders.METADATA, builder.build());
        }
        message.setBody(embedding.vectorAsList());
    }
}
