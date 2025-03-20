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
package org.apache.camel.component.langchain4j.embeddings;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanOperation;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteComponent;
import org.apache.camel.component.infinispan.remote.InfinispanRemoteConfiguration;
import org.apache.camel.component.infinispan.remote.embeddingstore.EmbeddingStoreUtil;
import org.apache.camel.component.infinispan.remote.embeddingstore.InfinispanRemoteEmbedding;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.support.task.budget.IterationBoundedBudget;
import org.apache.camel.test.infra.infinispan.services.InfinispanService;
import org.apache.camel.test.infra.infinispan.services.InfinispanServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.configuration.StringConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.SystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LangChain4jEmbeddingsComponentInfinispanTargetIT extends CamelTestSupport {
    @RegisterExtension
    private static final InfinispanService service = InfinispanServiceFactory.createSingletonInfinispanService();
    private static final String CACHE_NAME = "camel-infinispan-embeddings";
    private static final String INFINISPAN_URI = "infinispan:" + CACHE_NAME;
    private static final String TEXT_EMBEDDING_1 = "The quick brown fox jumps over the lazy dog";
    private static final String TEXT_EMBEDDING_2 = "Quantum mechanics explains the behavior of particles at atomic scales";
    private static final String TEXT_EMBEDDING_3 = "Some test content";
    private static final String TEXT_EMBEDDING_4 = TEXT_EMBEDDING_3 + " modified";

    @BindToRegistry
    private final AllMiniLmL6V2EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
    private RemoteCacheManager cacheContainer;

    @Test
    @Order(1)
    public void put() {
        Stream.of(TEXT_EMBEDDING_1, TEXT_EMBEDDING_2)
                .forEach(string -> {
                    Exchange result = fluentTemplate.to("direct:start")
                            .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                            .withBody(string)
                            .request(Exchange.class);

                    assertThat(result).isNotNull();
                    assertThat(result.getException()).isNull();
                });
    }

    @Test
    @Order(2)
    public void replace() {
        Exchange putResult = fluentTemplate.to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.PUT)
                .withBody(TEXT_EMBEDDING_3)
                .request(Exchange.class);

        assertThat(putResult).isNotNull();
        assertThat(putResult.getException()).isNull();

        Optional<InfinispanRemoteEmbedding> original = cacheContainer.getCache(CACHE_NAME)
                .values()
                .stream()
                .map(obj -> (InfinispanRemoteEmbedding) obj)
                .filter(embedding -> embedding.getText().equals(TEXT_EMBEDDING_3))
                .findFirst();

        assertThat(original).isPresent();

        Exchange replaceResult = fluentTemplate.to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REPLACE)
                .withHeader(InfinispanConstants.OLD_VALUE, original.get())
                .withBody(TEXT_EMBEDDING_4)
                .request(Exchange.class);

        assertThat(replaceResult).isNotNull();
        assertThat(replaceResult.getException()).isNull();
        assertThat(replaceResult.getMessage().getBody()).isEqualTo(true);
    }

    @Test
    @Order(3)
    public void remove() {
        Optional<Map.Entry<Object, Object>> entry = cacheContainer.getCache(CACHE_NAME)
                .entrySet()
                .stream()
                .filter(e -> {
                    InfinispanRemoteEmbedding embedding = (InfinispanRemoteEmbedding) e.getValue();
                    return embedding.getText().equals(TEXT_EMBEDDING_4);
                })
                .findFirst();

        assertThat(entry).isPresent();

        Map.Entry<Object, Object> itemToRemove = entry.get();
        Exchange result = fluentTemplate.to(INFINISPAN_URI)
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.REMOVE)
                .withHeader(InfinispanConstants.KEY, itemToRemove.getKey())
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody()).isEqualTo(itemToRemove.getValue());
    }

    @Test
    @Order(4)
    public void query() {
        Exchange result = fluentTemplate.to("direct:start")
                .withHeader(InfinispanConstants.OPERATION, InfinispanOperation.QUERY)
                .withBody("The study of particles and their properties at atomic levels")
                .request(Exchange.class);

        assertThat(result).isNotNull();
        assertThat(result.getException()).isNull();
        List<Object[]> results = result.getMessage().getBody(List.class);
        assertThat(results).isNotNull();
        assertThat(results.size()).isEqualTo(2);

        // Sort the results by their score
        results.sort((o1, o2) -> {
            Float value1 = (Float) o1[1];
            Float value2 = (Float) o2[1];
            return value2.compareTo(value1);
        });

        // The best hit should be similar to the search text
        Object[] hit1 = results.get(0);
        InfinispanRemoteEmbedding match1 = (InfinispanRemoteEmbedding) hit1[0];
        assertThat(match1.getText()).isEqualTo(TEXT_EMBEDDING_2);

        // The least relevant hit
        Object[] hit2 = results.get(1);
        InfinispanRemoteEmbedding match2 = (InfinispanRemoteEmbedding) hit2[0];
        assertThat(match2.getText()).isEqualTo(TEXT_EMBEDDING_1);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("langchain4j-embeddings:test")
                        .transform(new DataType("infinispan:embeddings"))
                        .to(INFINISPAN_URI);
            }
        };
    }

    @BindToRegistry
    public ComponentCustomizer infinispanComponentCustomizer() {
        return ComponentCustomizer.forType(
                InfinispanRemoteComponent.class,
                component -> {
                    InfinispanRemoteConfiguration configuration = component.getConfiguration();
                    configuration.setCacheContainer(cacheContainer);
                    configuration.setEmbeddingStoreDimension(model.dimension());
                });
    }

    @Override
    protected void setupResources() throws Exception {
        super.setupResources();

        if (cacheContainer == null) {
            cacheContainer = getCacheContainer();
            final IterationBoundedBudget budget
                    = Budgets.iterationBudget().withInterval(Duration.ofSeconds(1)).withMaxIterations(10).build();
            final ForegroundTask task = Tasks.foregroundTask()
                    .withBudget(budget).build();

            final boolean cacheCreated = task.run(this::createCache);
            Assumptions.assumeTrue(cacheCreated, "The container cache is not running healthily");
        }
    }

    private RemoteCacheManager getCacheContainer() {
        InfinispanRemoteConfiguration configuration = new InfinispanRemoteConfiguration();
        configuration.setEmbeddingStoreDimension(model.dimension());
        ConfigurationBuilder builder = getConfiguration();
        EmbeddingStoreUtil.configureMarshaller(configuration, builder);
        return new RemoteCacheManager(builder.build());
    }

    private boolean createCache() {
        try {
            getOrCreateCache();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void getOrCreateCache() {
        String configuration = "<distributed-cache name=\"" + CACHE_NAME + "\">\n"
                               + "<indexing storage=\"local-heap\">\n"
                               + "<indexed-entities>\n"
                               + "<indexed-entity>" + EmbeddingStoreUtil.DEFAULT_TYPE_NAME_PREFIX + model.dimension()
                               + "</indexed-entity>\n"
                               + "</indexed-entities>\n"
                               + "</indexing>\n"
                               + "</distributed-cache>";

        cacheContainer.administration().getOrCreateCache(CACHE_NAME, new StringConfiguration(configuration));
    }

    private ConfigurationBuilder getConfiguration() {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
        clientBuilder.forceReturnValues(true);
        clientBuilder.addServer()
                .host(service.host())
                .port(service.port());

        clientBuilder.security()
                .authentication()
                .username(service.username())
                .password(service.password())
                .serverName("infinispan")
                .saslMechanism("DIGEST-MD5")
                .realm("default");

        if (SystemUtils.IS_OS_MAC) {
            Properties properties = new Properties();
            properties.put("infinispan.client.hotrod.client_intelligence", "BASIC");
            clientBuilder.withProperties(properties);
        }
        return clientBuilder;
    }
}
