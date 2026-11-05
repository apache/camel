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

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pinecone.PineconeVectorDbHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineconeEmbeddingsDataTypeTransformerTest {

    private final DefaultCamelContext camelContext = new DefaultCamelContext();

    private final PineconeEmbeddingsDataTypeTransformer transformer = new PineconeEmbeddingsDataTypeTransformer();

    @BeforeEach
    void startContext() {
        // the converter camel-langchain4j-embeddings brings, which is not on the classpath of this module
        camelContext.getTypeConverterRegistry().addTypeConverter(TextSegment.class, String.class,
                new SimpleTypeConverter(false, (type, exchange, value) -> TextSegment.from((String) value)));
        camelContext.start();
    }

    @AfterEach
    void stopContext() {
        camelContext.stop();
    }

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, of which a TextSegment or a String is stored as the text, with the
        // vector in a header, and the List of Float transformerOut says
        BodyTypes bodyTypes = PineconeEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertArrayEquals(new Carrier[] { Carrier.ANY }, bodyTypes.transformerIn().value());
        assertArrayEquals(new Carrier[] { Carrier.LIST }, bodyTypes.transformerOut().value());
        assertEquals("java.util.List<java.lang.Float>", bodyTypes.transformerOut().javaType());

        for (Object body : Arrays.asList(TextSegment.from("hello"), "hello", 5, null)) {
            Message message = transform(body, new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
            assertEquals(List.of(0.1f, 0.2f, 0.3f), message.getBody(), String.valueOf(body));
            Struct metadata = message.getHeader(PineconeVectorDbHeaders.METADATA, Struct.class);
            if (body instanceof TextSegment || body instanceof String) {
                // CAMEL-26300: the text, in the text_segment metadata the pinecone:rag transformer reads
                assertNotNull(metadata, String.valueOf(body));
                assertEquals("hello", metadata.getFieldsOrThrow("text_segment").getStringValue(), String.valueOf(body));
            } else {
                // another body stores no text
                assertNull(metadata, String.valueOf(body));
            }
        }
    }

    @Test
    void theTextIsAddedToTheMetadataOfTheHeader() throws Exception {
        // CAMEL-26300: the metadata the caller set is kept
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
        exchange.getMessage().setHeader(PineconeVectorDbHeaders.METADATA, Struct.newBuilder()
                .putFields("source", Value.newBuilder().setStringValue("doc.txt").build())
                .build());
        exchange.getMessage().setBody(TextSegment.from("hello"));
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

        Struct metadata = exchange.getMessage().getHeader(PineconeVectorDbHeaders.METADATA, Struct.class);
        assertEquals(2, metadata.getFieldsCount(), metadata::toString);
        assertEquals("doc.txt", metadata.getFieldsOrThrow("source").getStringValue());
        assertEquals("hello", metadata.getFieldsOrThrow("text_segment").getStringValue());
    }

    @Test
    void aMessageWithoutTheEmbeddingFailsWithAClearMessage() {
        // CAMEL-26300: an IllegalArgumentException that names the missing header, as pgvector:embeddings gives, instead of
        // a NullPointerException
        IllegalArgumentException noEmbedding = assertThrows(IllegalArgumentException.class, () -> transform("hello", null));
        assertTrue(noEmbedding.getMessage().contains("CamelLangChain4jEmbeddingsVector"), noEmbedding.getMessage());
    }

    private Message transform(Object body, Embedding embedding) throws Exception {
        Exchange exchange = new DefaultExchange(camelContext);
        if (embedding != null) {
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, embedding);
        }
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange.getMessage();
    }
}
