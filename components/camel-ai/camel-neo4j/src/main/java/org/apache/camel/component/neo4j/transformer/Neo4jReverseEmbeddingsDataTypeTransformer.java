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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Maps a List of retrieved LangChain4j Embeddings with similarity search to a List of String for LangChain4j RAG
 **/
@DataTypeTransformer(name = "neo4j:rag",
                     description = "Prepares the similarity search LangChain4j embeddings to become a List of String for LangChain4j RAG")
@BodyTypes(transformerIn = @BodyType(value = Carrier.LIST,
                                     javaType = "java.util.List<java.util.Map<java.lang.String, java.lang.Object>>",
                                     description = "The rows of a VECTOR_SIMILARITY_SEARCH, whose text values are kept as"
                                                   + " String: a value of another type, such as a number, is converted, and a"
                                                   + " row without text gives an empty String. A null body fails"),
           transformerOut = @BodyType(value = Carrier.LIST, javaType = "java.util.List<java.lang.String>"))
public class Neo4jReverseEmbeddingsDataTypeTransformer extends Transformer {

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final List<Map<String, Object>> embeddings = message.getBody(List.class);
        final Exchange exchange = message.getExchange();

        List<String> result = embeddings.stream()
                .map(embedding -> toText(exchange, embedding.get("text")))
                .collect(Collectors.toList());

        message.setBody(result);

    }

    /**
     * The text of a row. The text of a node is a String, but a value of another type, such as a number, is converted
     * instead of cast, and a row without text gives an empty String.
     */
    private static String toText(Exchange exchange, Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        String text = exchange.getContext().getTypeConverter().convertTo(String.class, exchange, value);
        return text != null ? text : "";
    }
}
