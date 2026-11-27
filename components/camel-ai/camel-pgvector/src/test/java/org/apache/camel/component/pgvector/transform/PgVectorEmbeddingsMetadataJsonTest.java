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

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.component.pgvector.PgVectorHeaders;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pgvector:embeddings transformer writes the metadata of the TextSegment as valid JSON, with every control
 * character escaped.
 */
class PgVectorEmbeddingsMetadataJsonTest {

    private static final String KEY = "title\u001f";
    private static final String VALUE = "a\fb\u0001c\\d\"e\nf\tg\rh";

    @Test
    void controlCharactersOfTheMetadataAreEscaped() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR,
                    new Embedding(new float[] { 0.1f, 0.2f }));
            exchange.getMessage().setBody(TextSegment.from("hello", Metadata.from(KEY, VALUE)));

            new PgVectorEmbeddingsDataTypeTransformer().transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            String json = exchange.getMessage().getHeader(PgVectorHeaders.METADATA, String.class);
            assertThat(json).isEqualTo("{\"title\\u001f\":\"a\\u000cb\\u0001c\\\\d\\\"e\\nf\\tg\\rh\"}");
            assertThat(json.chars()).allMatch(c -> c >= 0x20);

            JsonObject parsed = (JsonObject) Jsoner.deserialize(json);
            assertThat(parsed).hasSize(1).containsEntry(KEY, VALUE);
        }
    }
}
