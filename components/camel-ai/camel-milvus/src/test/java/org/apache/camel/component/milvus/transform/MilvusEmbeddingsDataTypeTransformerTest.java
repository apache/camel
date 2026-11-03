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
package org.apache.camel.component.milvus.transform;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.UpsertParam;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.milvus.MilvusAction;
import org.apache.camel.component.milvus.MilvusHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.SimpleTypeConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusEmbeddingsDataTypeTransformerTest {

    private final MilvusEmbeddingsDataTypeTransformer transformer = new MilvusEmbeddingsDataTypeTransformer();

    @Test
    void theBodyTypesSayTheBody() throws Exception {
        // CAMEL-25928: a TextSegment, or a String the converter of camel-langchain4j-embeddings turns into one, with the
        // embedding in a header, as transformerIn says, and the InsertParam or UpsertParam transformerOut says
        BodyTypes bodyTypes = MilvusEmbeddingsDataTypeTransformer.class.getAnnotation(BodyTypes.class);
        assertThat(bodyTypes.transformerIn().value()).containsExactly(Carrier.TEXT, Carrier.POJO);
        assertThat(bodyTypes.transformerOut().value()).containsExactly(Carrier.POJO);
        assertThat(bodyTypes.transformerOut().javaType()).isEmpty();

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // the converter camel-langchain4j-embeddings brings, which is not on the classpath of this module
            context.getTypeConverterRegistry().addTypeConverter(TextSegment.class, String.class,
                    new SimpleTypeConverter(false, (type, exchange, value) -> TextSegment.from((String) value)));
            context.start();

            assertThat(transform(context, TextSegment.from("hello"), MilvusAction.INSERT)).isInstanceOf(InsertParam.class);
            assertThat(transform(context, "hello", MilvusAction.INSERT)).isInstanceOf(InsertParam.class);
            assertThat(transform(context, TextSegment.from("hello"), MilvusAction.UPSERT)).isInstanceOf(UpsertParam.class);
            assertThat(transform(context, "hello", MilvusAction.UPSERT)).isInstanceOf(UpsertParam.class);

            // a null body fails, and so does a message without the action header
            assertThatThrownBy(() -> transform(context, null, MilvusAction.INSERT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> transform(context, TextSegment.from("hello"), null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private Object transform(DefaultCamelContext context, Object body, MilvusAction action) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                new Embedding(new float[] { 0.1f, 0.2f, 0.3f }));
        exchange.getMessage().setHeader(MilvusHeaders.ACTION, action);
        exchange.getMessage().setBody(body);
        transformer.transform(exchange.getMessage(), DataType.ANY, DataType.ANY);
        return exchange.getMessage().getBody();
    }
}
