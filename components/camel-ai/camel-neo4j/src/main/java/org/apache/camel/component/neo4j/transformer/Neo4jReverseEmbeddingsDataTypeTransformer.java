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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.Message;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a List of retrieved LangChain4j Embeddings with similarity search to a List of String for LangChain4j RAG
 **/
@DataTypeTransformer(name = "neo4j:rag",
                     description = "Prepares the similarity search LangChain4j embeddings to become a List of String for LangChain4j RAG")
public class Neo4jReverseEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final List<Map<String, Object>> embeddings = message.getBody(List.class);

        List<String> result = embeddings.stream()
                .map(embedding -> (String) embedding.getOrDefault("text", ""))
                .collect(Collectors.toList());

        message.setBody(result);

    }
}
