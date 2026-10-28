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

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.neo4j.Neo4jEmbedding;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jEmbeddingDataTypeTransformerTest {

    private final Neo4jEmbeddingDataTypeTransformer transformer = new Neo4jEmbeddingDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: a TextSegment, or a String the converter of camel-langchain4j-embeddings turns into one, with the
        // embedding in a header, as transformerIn says, and the Neo4jEmbedding transformerOut says
        BodyTypes bodyTypes = Neo4jEmbeddingDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.TEXT, Carrier.POJO }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.POJO }, bodyTypes.transformerOut().value());
        assertEquals(Neo4jEmbedding.class.getName(), bodyTypes.transformerOut().javaType());

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // the converter camel-langchain4j-embeddings brings, which is not on the classpath of this module
            context.getTypeConverterRegistry().addTypeConverter(TextSegment.class, String.class,
                    new SimpleTypeConverter(false, (type, exchange, value) -> TextSegment.from((String) value)));
            context.start();

            for (Object body : new Object[] { TextSegment.from("hello"), "hello" }) {
                Object out = transform(context, body, true);
                assertInstanceOf(Neo4jEmbedding.class, out, body.getClass().getName());
                assertEquals("hello", ((Neo4jEmbedding) out).getText());
            }

            // a null body fails, and so does a message without the embedding
            assertThrows(NullPointerException.class, () -> transform(context, null, true));
            assertThrows(NullPointerException.class, () -> transform(context, TextSegment.from("hello"), false));
        }
    }

    private Object transform(DefaultCamelContext context, Object body, boolean embedding) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        if (embedding) {
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
        }
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange.getMessage().getBody();
    }
}
