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
package org.apache.camel.component.infinispan.remote.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanRemoteEmbedding;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanVectorQueryBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

@DataTypeTransformer(name = "infinispan:embeddings",
                     description = "Prepares the message to become an object writable by the Infinispan component")
public class InfinispanEmbeddingsDataTypeTransformer extends Transformer {
    private static final Set<InfinispanOperation> ALLOWED_EMBEDDING_OPERATIONS
            = Set.of(InfinispanOperation.PUT, InfinispanOperation.PUTASYNC, InfinispanOperation.PUTIFABSENT,
                    InfinispanOperation.PUTIFABSENTASYNC, InfinispanOperation.REPLACE, InfinispanOperation.REPLACEASYNC,
                    InfinispanOperation.QUERY);

    @Override
    public void transform(Message message, DataType from, DataType to) throws Exception {
        InfinispanOperation operation
                = message.getHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT, InfinispanOperation.class);
        Embedding embedding = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);

        if (ALLOWED_EMBEDDING_OPERATIONS.contains(operation) && embedding != null) {
            if (operation.equals(InfinispanOperation.QUERY)) {
                InfinispanQueryBuilder builder
                        = message.getHeader(InfinispanConstants.QUERY_BUILDER, InfinispanQueryBuilder.class);
                if (builder == null) {
                    message.setHeader(InfinispanConstants.QUERY_BUILDER, new InfinispanVectorQueryBuilder(embedding.vector()));
                }
            } else {
                String text = null;
                List<String> metadataKeys = null;
                List<String> metadataValues = null;

                TextSegment textSegment
                        = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_TEXT_SEGMENT, TextSegment.class);
                if (textSegment != null) {
                    text = textSegment.text();
                    metadataKeys = new ArrayList<>();
                    metadataValues = new ArrayList<>();

                    Map<String, Object> metadata = textSegment.metadata().toMap();
                    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                        metadataKeys.add(entry.getKey());
                        metadataValues.add(entry.getValue().toString());
                    }
                }

                InfinispanRemoteEmbedding item
                        = new InfinispanRemoteEmbedding(
                                message.getMessageId(), embedding.vector(), text, metadataKeys, metadataValues);

                if (operation.equals(InfinispanOperation.REPLACE) || operation.equals(InfinispanOperation.REPLACEASYNC)) {
                    InfinispanRemoteEmbedding oldValue
                            = message.getHeader(InfinispanConstants.OLD_VALUE, InfinispanRemoteEmbedding.class);
                    if (oldValue != null) {
                        message.setHeader(InfinispanConstants.KEY, oldValue.getId());
                    }
                } else {
                    message.setHeader(InfinispanConstants.KEY, message.getMessageId());
                }

                message.setHeader(InfinispanConstants.VALUE, item);
            }
        }
    }
}
