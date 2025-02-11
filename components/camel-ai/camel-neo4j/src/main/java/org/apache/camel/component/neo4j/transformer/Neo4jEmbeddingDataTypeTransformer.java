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
package org.apache.camel.component.neo4j.transformer;

import java.util.UUID;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.neo4j.Neo4jConstants;
import org.apache.camel.component.neo4j.Neo4jEmbedding;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

@DataTypeTransformer(name = "neo4j:embeddings",
                     description = "Prepares the message to become an object writable by Neo4j component")
public class Neo4jEmbeddingDataTypeTransformer extends Transformer {
    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Embedding embedding
                = message.getHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, Embedding.class);

        final TextSegment text = message.getBody(TextSegment.class);

        final String id = message.getHeader(Neo4jConstants.Headers.VECTOR_ID, () -> UUID.randomUUID(), String.class);

        Neo4jEmbedding neo4jEmbedding = new Neo4jEmbedding(id, text.text(), embedding.vector());

        message.setBody(neo4jEmbedding);
    }
}
