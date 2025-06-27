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
package org.apache.camel.component.infinispan.remote;

import java.time.Duration;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.ai.CamelLangchain4jAttributes;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil;
import org.apache.camel.spi.DataType;
import org.awaitility.Awaitility;
import org.infinispan.api.annotations.indexing.option.VectorSimilarity;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.configuration.StringConfiguration;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InfinispanRemoteEmbeddingStoreIT extends InfinispanRemoteTestSupport {
    private static final String CACHE_NAME = "camel-infinispan-embeddings";
    private static final int DIMENSION = 3;
    private static final String ENTITY_TYPE_NAME = EmbeddingStoreUtil.DEFAULT_TYPE_NAME_PREFIX + DIMENSION;

    @BeforeEach
    protected void beforeEach() {
        getCache(CACHE_NAME).clear();
        Awaitility.await().atMost(Duration.ofSeconds(1)).until(() -> cacheContainer.isStarted());
    }

    @Test
    public void embeddingStore() throws Exception {
        Embedding embedding = Embedding.from(new float[] { 1.0f, 2.0f, 3.0f });
        fluentTemplate.to("direct:put")
                .withBody(embedding)
                .send();

        List<Object> results = fluentTemplate.toF("direct:query")
                .withBody(embedding)
                .request(List.class);
        assertEquals(1, results.size());
    }

    @Test
    public void dimensionUnspecifiedThrowsException() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
            new InfinispanRemoteManager(context, configuration).start();
        });
    }

    @Test
    public void embeddingStoreDisabled() {
        InfinispanRemoteConfiguration configuration = createInfinispanRemoteConfiguration();
        configuration.setEmbeddingStoreEnabled(false);

        InfinispanRemoteManager manager = new InfinispanRemoteManager(context, configuration);
        try {
            manager.start();

            BasicCache<Object, Object> metadataCache
                    = manager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            Object metadata = metadataCache.get(EmbeddingStoreUtil.getSchemeFileName(configuration));
            assertNull(metadata);
        } finally {
            manager.stop();
        }
    }

    @Test
    public void registerSchemaDisabled() {
        InfinispanRemoteConfiguration configuration = createInfinispanRemoteConfiguration();
        configuration.setEmbeddingStoreRegisterSchema(false);
        configuration.setEmbeddingStoreDimension(999);

        InfinispanRemoteManager manager = new InfinispanRemoteManager(context, configuration);
        try {
            manager.start();

            BasicCache<Object, Object> metadataCache
                    = manager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            Object metadata = metadataCache.get(EmbeddingStoreUtil.getSchemeFileName(configuration));
            assertNull(metadata);
        } finally {
            manager.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(VectorSimilarity.class)
    public void registerSchema(VectorSimilarity similarity) {
        int dimension = 900 + similarity.ordinal();
        String typeName = EmbeddingStoreUtil.DEFAULT_TYPE_NAME_PREFIX + dimension;

        InfinispanRemoteConfiguration configuration = createInfinispanRemoteConfiguration();
        configuration.setEmbeddingStoreDimension(dimension);
        configuration.setEmbeddingStoreTypeName(typeName);
        configuration.setEmbeddingStoreVectorSimilarity(similarity);

        InfinispanRemoteManager manager = new InfinispanRemoteManager(context, configuration);
        BasicCache<Object, Object> metadataCache = null;
        try {
            manager.start();

            metadataCache = manager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);
            Object metadata = metadataCache.get(EmbeddingStoreUtil.getSchemeFileName(configuration));
            assertNotNull(metadata);
        } finally {
            if (metadataCache != null) {
                metadataCache.remove(EmbeddingStoreUtil.getSchemeFileName(configuration));
            }
            manager.stop();
        }
    }

    @Override
    protected RemoteCacheManager getCacheContainer() {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setEmbeddingStoreDimension(DIMENSION);
        ConfigurationBuilder builder = getConfiguration();
        EmbeddingStoreUtil.configureMarshaller(configuration, builder);
        return new RemoteCacheManager(builder.build());
    }

    @Override
    protected void getOrCreateCache() {
        String configuration = "<distributed-cache name=\"" + CACHE_NAME + "\">\n"
                               + "<indexing storage=\"local-heap\">\n"
                               + "<indexed-entities>\n"
                               + "<indexed-entity>" + ENTITY_TYPE_NAME + "</indexed-entity>\n"
                               + "</indexed-entities>\n"
                               + "</indexing>\n"
                               + "</distributed-cache>";

        cacheContainer.administration().getOrCreateCache(CACHE_NAME, new StringConfiguration(configuration));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        // Fake that langchain4j-embeddings is on the classpath
        camelContext.addComponent("langchain4j-embeddings", camelContext.getComponent("stub"));
        return camelContext;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:put")
                        .setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR).body()
                        .transform(new DataType("infinispan:embeddings"))
                        .toF("infinispan://%s?embeddingStoreDimension=3", CACHE_NAME);

                from("direct:query")
                        .setHeader(CamelLangchain4jAttributes.CAMEL_LANGCHAIN4J_EMBEDDING_VECTOR).body()
                        .setHeader(InfinispanConstants.OPERATION).constant(InfinispanOperation.QUERY)
                        .transform(new DataType("infinispan:embeddings"))
                        .toF("infinispan://%s?embeddingStoreDimension=3&embeddingStoreDistance=2", CACHE_NAME);
            }
        };
    }

    private InfinispanRemoteConfiguration createInfinispanRemoteConfiguration() {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setHosts(service.getServiceAddress());
        configuration.setUsername(service.username());
        configuration.setPassword(service.password());
        configuration.setSaslMechanism("DIGEST-MD5");
        configuration.setSecurityRealm("default");
        configuration.setSecure(true);

        if (SystemUtils.IS_OS_MAC) {
            configuration.addConfigurationProperty("infinispan.client.hotrod.client_intelligence", "BASIC");
        }

        return configuration;
    }
}
