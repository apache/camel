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
package org.apache.camel.component.langchain.embeddings;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Points;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;

public class LangchainEmbeddingsTestSupport {
    public static final String POINT_ID_HEADER = "point.id";

    private LangchainEmbeddingsTestSupport() {
    }

    // TODO: this should be turned into a proper converter or data type
    public static class AsPointStruct {
        @Handler
        public Points.PointStruct asPointStruct(Exchange e) {
            Embedding embedding = e.getMessage().getHeader(LangchainEmbeddings.Headers.VECTOR, Embedding.class);
            TextSegment text = e.getMessage().getBody(TextSegment.class);
            Points.PointId id = e.getMessage().getHeader(POINT_ID_HEADER, Points.PointId.class);

            var builder = Points.PointStruct.newBuilder();
            builder.setId(id);
            builder.setVectors(VectorsFactory.vectors(embedding.vector()));

            if (text != null) {
                // this is the default for langchain4j
                // https://github.com/langchain4j/langchain4j/blob/3e432486ffc5cb80861e118cbc974f478c3accfc/langchain4j-qdrant/src/main/java/dev/langchain4j/store/embedding/qdrant/QdrantEmbeddingStore.java#L261
                builder.putPayload("text_segment", ValueFactory.value(text.text()));

                text.metadata()
                        .asMap()
                        .forEach((key, value) -> builder.putPayload(key, ValueFactory.value(value)));
            }

            return builder.build();
        }
    }
}
