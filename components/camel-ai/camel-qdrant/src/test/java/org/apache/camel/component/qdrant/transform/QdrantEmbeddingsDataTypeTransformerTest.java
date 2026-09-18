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
package org.apache.camel.component.qdrant.transform;

import java.util.Map;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantEmbeddingsDataTypeTransformerTest {

    @Test
    void mapsMixedMetadataTypesToTypedPayload() throws Exception {
        // A TextSegment carrying String and numeric metadata, as document splitters routinely produce
        // (chunk index, page number, ...). The transformer must not blindly cast every value to String.
        Metadata metadata = new Metadata()
                .put("source", "doc.txt")
                .put("index", 3)
                .put("score", 0.75);
        TextSegment segment = TextSegment.from("hello world", metadata);
        Embedding embedding = new Embedding(new float[] { 0.1f, 0.2f, 0.3f });

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            Exchange exchange = new DefaultExchange(context);
            exchange.getMessage().setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR, embedding);
            exchange.getMessage().setBody(segment);

            new QdrantEmbeddingsDataTypeTransformer().transform(exchange.getMessage(), DataType.ANY, DataType.ANY);

            Points.PointStruct point = exchange.getMessage().getBody(Points.PointStruct.class);
            assertThat(point).isNotNull();

            Map<String, Value> payload = point.getPayloadMap();
            assertThat(payload.get("text_segment").getStringValue()).isEqualTo("hello world");
            assertThat(payload.get("source").getStringValue()).isEqualTo("doc.txt");
            assertThat(payload.get("index").getIntegerValue()).isEqualTo(3L);
            assertThat(payload.get("score").getDoubleValue()).isEqualTo(0.75);
        }
    }
}
