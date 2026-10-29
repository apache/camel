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

import java.util.Arrays;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanRemoteEmbedding;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanVectorQueryBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InfinispanEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final InfinispanEmbeddingsDataTypeTransformer transformer = new InfinispanEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, as the embedding and the text come from headers, and the body is
        // kept, as transformerOut says
        BodyTypes bodyTypes = InfinispanEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.ANY }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.UNCHANGED }, bodyTypes.transformerOut().value());

        for (Object body : Arrays.asList(TextSegment.from("hello"), "hello", 5, null)) {
            // PUT, the default operation: the embedding goes in the value header
            Exchange exchange = transform(body, null, true);
            assertSame(body, exchange.getMessage().getBody(), String.valueOf(body));
            InfinispanRemoteEmbedding value = assertInstanceOf(InfinispanRemoteEmbedding.class,
                    exchange.getMessage().getHeader(InfinispanConstants.VALUE));
            assertEquals("hello", value.getText());
            assertEquals(exchange.getMessage().getMessageId(), exchange.getMessage().getHeader(InfinispanConstants.KEY));

            // QUERY: a vector query in the query builder header
            exchange = transform(body, InfinispanOperation.QUERY, true);
            assertSame(body, exchange.getMessage().getBody(), String.valueOf(body));
            assertInstanceOf(InfinispanVectorQueryBuilder.class,
                    exchange.getMessage().getHeader(InfinispanConstants.QUERY_BUILDER));

            // without the embedding header nothing is set
            exchange = transform(body, null, false);
            assertSame(body, exchange.getMessage().getBody(), String.valueOf(body));
            assertNull(exchange.getMessage().getHeader(InfinispanConstants.VALUE));
            assertNull(exchange.getMessage().getHeader(InfinispanConstants.KEY));
        }
    }

    private Exchange transform(Object body, InfinispanOperation operation, boolean embedding) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        if (operation != null) {
            exchange.getMessage().setHeader(InfinispanConstants.OPERATION, operation);
        }
        if (embedding) {
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
        }
        exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_TEXT_SEGMENT, TextSegment.from("hello"));
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange;
    }
}
