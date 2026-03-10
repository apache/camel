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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shared holder for Camel test infrastructure reference data used by both {@link TestScaffoldTools} (MCP Tool) and
 * {@link TestInfraResources} (MCP Resources).
 * <p>
 * Contains the registry of test-infra services (Testcontainers-based) that map component schemes to their corresponding
 * service classes, factory classes, and Maven artifact IDs.
 */
@ApplicationScoped
public class TestInfraData {

    /** Component schemes that are trivial and should not be replaced with mocks. */
    private static final Set<String> TRIVIAL_SCHEMES = Set.of("log", "direct", "seda", "mock", "controlbus", "stub");

    /** Component schemes that are internally triggered (user can send messages to them). */
    private static final Set<String> SENDABLE_SCHEMES = Set.of("direct", "seda");

    private static final Map<String, TestInfraInfo> TEST_INFRA_MAP;

    static {
        Map<String, TestInfraInfo> map = new LinkedHashMap<>();

        TestInfraInfo kafka = new TestInfraInfo(
                "KafkaService", "KafkaServiceFactory",
                "camel-test-infra-kafka", "org.apache.camel.test.infra.kafka.services");
        map.put("kafka", kafka);

        TestInfraInfo artemis = new TestInfraInfo(
                "ArtemisService", "ArtemisServiceFactory",
                "camel-test-infra-artemis", "org.apache.camel.test.infra.artemis.services");
        map.put("jms", artemis);
        map.put("activemq", artemis);
        map.put("sjms", artemis);
        map.put("sjms2", artemis);
        map.put("amqp", artemis);

        TestInfraInfo mongodb = new TestInfraInfo(
                "MongoDBService", "MongoDBServiceFactory",
                "camel-test-infra-mongodb", "org.apache.camel.test.infra.mongodb.services");
        map.put("mongodb", mongodb);

        TestInfraInfo postgres = new TestInfraInfo(
                "PostgresService", "PostgresServiceFactory",
                "camel-test-infra-postgres", "org.apache.camel.test.infra.postgres.services");
        map.put("sql", postgres);
        map.put("jdbc", postgres);

        TestInfraInfo cassandra = new TestInfraInfo(
                "CassandraService", "CassandraServiceFactory",
                "camel-test-infra-cassandra", "org.apache.camel.test.infra.cassandra.services");
        map.put("cql", cassandra);

        TestInfraInfo elasticsearch = new TestInfraInfo(
                "ElasticSearchService", "ElasticSearchServiceFactory",
                "camel-test-infra-elasticsearch", "org.apache.camel.test.infra.elasticsearch.services");
        map.put("elasticsearch", elasticsearch);
        map.put("elasticsearch-rest", elasticsearch);

        TestInfraInfo redis = new TestInfraInfo(
                "RedisService", "RedisServiceFactory",
                "camel-test-infra-redis", "org.apache.camel.test.infra.redis.services");
        map.put("spring-redis", redis);

        TestInfraInfo rabbitmq = new TestInfraInfo(
                "RabbitMQService", "RabbitMQServiceFactory",
                "camel-test-infra-rabbitmq", "org.apache.camel.test.infra.rabbitmq.services");
        map.put("rabbitmq", rabbitmq);

        TestInfraInfo ftp = new TestInfraInfo(
                "FtpService", "FtpServiceFactory",
                "camel-test-infra-ftp", "org.apache.camel.test.infra.ftp.services");
        map.put("ftp", ftp);
        map.put("sftp", ftp);
        map.put("ftps", ftp);

        TestInfraInfo consul = new TestInfraInfo(
                "ConsulService", "ConsulServiceFactory",
                "camel-test-infra-consul", "org.apache.camel.test.infra.consul.services");
        map.put("consul", consul);

        TestInfraInfo nats = new TestInfraInfo(
                "NatsService", "NatsServiceFactory",
                "camel-test-infra-nats", "org.apache.camel.test.infra.nats.services");
        map.put("nats", nats);

        TestInfraInfo pulsar = new TestInfraInfo(
                "PulsarService", "PulsarServiceFactory",
                "camel-test-infra-pulsar", "org.apache.camel.test.infra.pulsar.services");
        map.put("pulsar", pulsar);

        TestInfraInfo couchdb = new TestInfraInfo(
                "CouchDbService", "CouchDbServiceFactory",
                "camel-test-infra-couchdb", "org.apache.camel.test.infra.couchdb.services");
        map.put("couchdb", couchdb);

        TestInfraInfo infinispan = new TestInfraInfo(
                "InfinispanService", "InfinispanServiceFactory",
                "camel-test-infra-infinispan", "org.apache.camel.test.infra.infinispan.services");
        map.put("infinispan", infinispan);

        TestInfraInfo minio = new TestInfraInfo(
                "MinioService", "MinioServiceFactory",
                "camel-test-infra-minio", "org.apache.camel.test.infra.minio.services");
        map.put("minio", minio);

        TestInfraInfo solr = new TestInfraInfo(
                "SolrService", "SolrServiceFactory",
                "camel-test-infra-solr", "org.apache.camel.test.infra.solr.services");
        map.put("solr", solr);

        TEST_INFRA_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Get the full scheme-to-service mapping. Multiple schemes may map to the same service (e.g., jms, activemq, sjms
     * all map to ArtemisService).
     */
    public Map<String, TestInfraInfo> getTestInfraMap() {
        return TEST_INFRA_MAP;
    }

    /**
     * Get test infrastructure info for a specific component scheme.
     *
     * @return the infra info, or null if no test-infra service is available for the scheme
     */
    public TestInfraInfo getTestInfra(String scheme) {
        return TEST_INFRA_MAP.get(scheme);
    }

    /**
     * Get all unique test-infra services (deduplicated, since multiple schemes can map to the same service).
     */
    public List<TestInfraInfo> getUniqueServices() {
        return new ArrayList<>(new LinkedHashSet<>(TEST_INFRA_MAP.values()));
    }

    /**
     * Get all component schemes that have test-infra services available.
     */
    public List<String> getSupportedSchemes() {
        return List.copyOf(TEST_INFRA_MAP.keySet());
    }

    /**
     * Get the set of trivial component schemes that should not be replaced with mocks.
     */
    public Set<String> getTrivialSchemes() {
        return TRIVIAL_SCHEMES;
    }

    /**
     * Get the set of component schemes that are internally triggered (user can send messages to them).
     */
    public Set<String> getSendableSchemes() {
        return SENDABLE_SCHEMES;
    }

    /**
     * Holds test-infra service information for a Camel component.
     */
    public record TestInfraInfo(
            String serviceClass,
            String factoryClass,
            String artifactId,
            String packageName) {

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TestInfraInfo that)) {
                return false;
            }
            return artifactId.equals(that.artifactId);
        }

        @Override
        public int hashCode() {
            return artifactId.hashCode();
        }
    }
}
