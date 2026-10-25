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

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PineconeEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final PineconeEmbeddingsDataTypeTransformer transformer = new PineconeEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, as the vector comes from a header, and the List of Float
        // transformerOut says
        BodyTypes bodyTypes = PineconeEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.ANY }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.LIST }, bodyTypes.transformerOut().value());
        assertEquals("java.util.List<java.lang.Float>", bodyTypes.transformerOut().javaType());

        for (Object body : Arrays.asList(TextSegment.from("hello"), "hello", 5, null)) {
            Exchange exchange = new DefaultExchange(camelContext);
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
            exchange.getMessage().setBody(body);
            transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
            assertEquals(List.of(0.1f, 0.2f, 0.3f), exchange.getMessage().getBody(), String.valueOf(body));
        }

        // a message without the embedding fails
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setBody("hello");
        assertThrows(NullPointerException.class,
                () -> transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY));
    }
}
