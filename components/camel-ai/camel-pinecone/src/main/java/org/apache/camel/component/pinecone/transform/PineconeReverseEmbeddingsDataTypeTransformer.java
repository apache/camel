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

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.protobuf.Value;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.ScoredVectorWithUnsignedIndices;
import org.apache.camel.Message;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a List of retrieved LangChain4j Embeddings with similarity search to a List of String for LangChain4j RAG
 */
@DataTypeTransformer(name = "pinecone:rag",
                     description = "Maps a List of retrieved LangChain4j Embeddings with similarity search to a List of String for LangChain4j RAG")
@BodyTypes(transformerIn = @BodyType(value = { Carrier.POJO, Carrier.LIST, Carrier.NULL },
                                     description = "The QueryResponseWithUnsignedIndices of a QUERY or QUERY_BY_ID, or the List"
                                                   + " of ScoredVectorWithUnsignedIndices of its matches, with the text in the"
                                                   + " text_segment metadata, which the query returns with the"
                                                   + " CamelPineconeQueryIncludeMetadata header set to true. A match without"
                                                   + " the text gives an empty String, and a null body an empty List"),
           transformerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<java.lang.String>"))
public class PineconeReverseEmbeddingsDataTypeTransformer extends Transformer {
    @Override
    public void transform(Message message, DataType fromType, DataType toType) throws Exception {
        List<ScoredVectorWithUnsignedIndices> embeddings;
        if (message.getBody() instanceof QueryResponseWithUnsignedIndices response) {
            // the body a QUERY or QUERY_BY_ID leaves
            embeddings = response.getMatchesList();
        } else {
            embeddings = message.getBody(List.class);
        }

        if (embeddings == null || embeddings.isEmpty()) {
            message.setBody(Collections.emptyList());
            return;
        }

        List<String> result = embeddings.stream()
                .map(ScoredVectorWithUnsignedIndices::getMetadata)
                .map(metadata -> metadata.containsFields("text_segment")
                        ? metadata.getFieldsOrDefault("text_segment", Value.getDefaultInstance()).getStringValue()
                        : "")
                .collect(Collectors.toList());

        message.setBody(result);
    }
}
