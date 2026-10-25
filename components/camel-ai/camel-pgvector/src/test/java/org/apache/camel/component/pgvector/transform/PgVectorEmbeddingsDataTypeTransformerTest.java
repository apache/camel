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
package org.apache.camel.component.pgvector.transform;

import java.util.Arrays;
import java.util.List;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pgvector.PgVectorHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PgVectorEmbeddingsDataTypeTransformerTest {

    private final PgVectorEmbeddingsDataTypeTransformer transformer = new PgVectorEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: any body, as transformerIn says, of which a TextSegment or a String is stored as the text, with the
        // embedding in a header, and the List of Float transformerOut says
        BodyTypes bodyTypes = PgVectorEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes.transformerIn().value()).containsExactly(Carrier.ANY);
        assertThat(bodyTypes.transformerOut().value()).containsExactly(Carrier.LIST);
        assertThat(bodyTypes.transformerOut().javaType()).isEqualTo("java.util.List<java.lang.Float>");

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // the converter camel-langchain4j-embeddings brings, which is not on the classpath of this module
            context.getTypeConverterRegistry().addTypeConverter(TextSegment.class, String.class,
                    new SimpleTypeConverter(false, (type, exchange, value) -> TextSegment.from((String) value)));
            context.start();

            for (Object body : Arrays.asList(TextSegment.from("hello", Metadata.from("source", "doc.txt")), "hello", 5,
                    null)) {
                Exchange exchange = transform(context, body, true);
                assertThat(exchange.getMessage().getBody()).as(String.valueOf(body)).isInstanceOf(List.class);
                assertThat(exchange.getMessage().getBody(List.class)).containsExactly(0.1f, 0.2f, 0.3f);
                if (body instanceof TextSegment || body instanceof String) {
                    assertThat(exchange.getMessage().getHeader(PgVectorHeaders.TEXT_CONTENT)).isEqualTo("hello");
                } else {
                    // another body stores no text
                    assertThat(exchange.getMessage().getHeader(PgVectorHeaders.TEXT_CONTENT)).isNull();
                }
            }
            assertThat(transform(context, TextSegment.from("hello", Metadata.from("source", "doc.txt")), true)
                    .getMessage().getHeader(PgVectorHeaders.METADATA)).isEqualTo("{\"source\":\"doc.txt\"}");

            // a message without the embedding fails
            assertThatThrownBy(() -> transform(context, TextSegment.from("hello"), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private Exchange transform(DefaultCamelContext context, Object body, boolean embedding) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        if (embedding) {
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
        }
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange;
    }
}
