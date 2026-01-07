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
package org.apache.camel.component.chroma;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.chroma.services.ChromaService;
import org.apache.camel.test.infra.chroma.services.ChromaServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import tech.amikos.chromadb.Embedding;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChromaTestSupport extends CamelTestSupport {

    @RegisterExtension
    static ChromaService CHROMA = ChromaServiceFactory.createSingletonService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        ChromaComponent component = context.getComponent("chroma", ChromaComponent.class);
        component.getConfiguration().setHost(CHROMA.getEndpoint());
        component.getConfiguration().setEmbeddingFunction(new TestEmbeddingFunction());

        return context;
    }

    /**
     * A simple embedding function for testing that generates deterministic embeddings based on the hash of the input
     * text.
     */
    public static class TestEmbeddingFunction implements EmbeddingFunction {
        private static final int EMBEDDING_DIMENSION = 384;

        @Override
        public List<Embedding> embedDocuments(List<String> documents) {
            List<Embedding> result = new ArrayList<>();
            for (String doc : documents) {
                result.add(createEmbedding(doc));
            }
            return result;
        }

        @Override
        public List<Embedding> embedDocuments(String[] documents) {
            List<Embedding> result = new ArrayList<>();
            for (String doc : documents) {
                result.add(createEmbedding(doc));
            }
            return result;
        }

        @Override
        public Embedding embedQuery(String query) {
            return createEmbedding(query);
        }

        private Embedding createEmbedding(String text) {
            float[] values = new float[EMBEDDING_DIMENSION];
            int hash = text.hashCode();
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                // Generate deterministic values based on hash
                values[i] = (float) Math.sin(hash + i) * 0.5f;
            }
            return new Embedding(values);
        }
    }
}
