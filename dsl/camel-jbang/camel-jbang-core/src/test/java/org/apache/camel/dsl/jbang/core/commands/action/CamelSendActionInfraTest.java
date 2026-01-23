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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTestSupport;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

// CamelSendAction's --infra option.
public class CamelSendActionInfraTest extends CamelCommandBaseTestSupport {

    private CamelSendAction sendAction;
    private CamelCatalog catalog;

    private static final Set<String> SKIP_CATALOG_VALIDATION = Set.of(
            "openldap",
            "docling",
            "torch-serve",
            "microprofile",
            "artemis",
            "ibmmq",
            "rabbitmq",
            "postgres",
            "ollama");

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        sendAction = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
        catalog = new DefaultCamelCatalog();
    }

    private String buildAndDecode(String endpoint, String service, JsonObject json) {
        String result = sendAction.updateEndpointWithConnectionDetails(endpoint, service, json);
        return URLDecoder.decode(result, StandardCharsets.UTF_8);
    }

    private void validateEndpointWithCatalog(String serviceName, String decodedUri) {
        if (SKIP_CATALOG_VALIDATION.contains(serviceName)) {
            return;
        }

        EndpointValidationResult result = catalog.validateEndpointProperties(decodedUri);

        assertThat(result.getUnknown())
                .describedAs("URI '%s' for service '%s' contains unknown properties: %s",
                        decodedUri, serviceName, result.getUnknown())
                .isNullOrEmpty();
    }

    static Stream<Arguments> infraServiceTestCases() {
        List<Arguments> testCases = new ArrayList<>();

        testCases.add(Arguments.of(
                "nats",
                createJson("servers", "localhost:4222"),
                "nats:myTopic",
                "servers", "localhost:4222"));

        testCases.add(Arguments.of(
                "kafka",
                createJson("brokers", "localhost:9092"),
                "kafka:myTopic",
                "brokers", "localhost:9092"));

        testCases.add(Arguments.of(
                "mosquitto",
                createJson("brokerUrl", "tcp://localhost:1883"),
                "paho-mqtt5:myTopic",
                "brokerUrl", "tcp://localhost:1883"));

        testCases.add(Arguments.of(
                "hive-mq",
                createJson("brokerUrl", "tcp://localhost:1883"),
                "paho-mqtt5:myTopic",
                "brokerUrl", "tcp://localhost:1883"));

        testCases.add(Arguments.of(
                "artemis",
                createJson("serviceAddress", "tcp://localhost:61616", "userName", "admin", "password", "secret"),
                "jms:myQueue",
                "password", "RAW(secret)"));

        testCases.add(Arguments.of(
                "rabbitmq",
                createJson("uri", "amqp://localhost:5672", "username", "guest", "password", "guest"),
                "spring-rabbitmq:myExchange",
                "spring-rabbitmq", "myExchange"));

        testCases.add(Arguments.of(
                "mongodb",
                createJson("hosts", "localhost:27017"),
                "mongodb:myDatabase",
                "hosts", "localhost:27017"));

        testCases.add(Arguments.of(
                "minio",
                createJson("accessKey", "minioadmin", "secretKey", "minioadmin", "endpoint", "http://localhost:9000"),
                "minio:myBucket",
                "endpoint", "http://localhost:9000"));

        testCases.add(Arguments.of(
                "infinispan",
                createJson("hosts", "localhost:11222", "username", "admin", "password", "secret"),
                "infinispan:myCache",
                "hosts", "localhost:11222"));

        testCases.add(Arguments.of(
                "zookeeper",
                createJson("serverUrls", "localhost:2181",
                        "endpointUri", "zookeeper:localhost:2181/camel",
                        "connectionBase", "zookeeper:localhost:2181"),
                "zookeeper:/myPath",
                "localhost:2181", "/myPath"));

        testCases.add(Arguments.of(
                "couchdb",
                createJson("host", "localhost", "port", 5984, "database", "mydb",
                        "endpointUri", "couchdb:http:localhost:5984/mydb",
                        "connectionBase", "couchdb:http:localhost:5984"),
                "couchdb:myDatabase",
                "localhost", "myDatabase"));

        testCases.add(Arguments.of(
                "arangodb",
                createJson("host", "localhost", "port", 8529),
                "arangodb:myDatabase",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "hashicorp",
                createJson("host", "localhost", "port", 8200, "token", "mytoken"),
                "hashicorp-vault:mySecret",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "keycloak",
                createJson("serverUrl", "http://localhost:8080", "realm", "myrealm"),
                "http:localhost:8080/auth",
                "http", "localhost"));

        testCases.add(Arguments.of(
                "smb",
                createJson("address", "localhost:445", "shareName", "myShare",
                        "userName", "user", "password", "pass",
                        "endpointUri", "smb:localhost:445/myShare?username=user&password=RAW(pass)",
                        "connectionBase", "smb:localhost:445"),
                "smb:myShare",
                "localhost", "myShare"));

        testCases.add(Arguments.of(
                "openldap",
                createJson("host", "localhost", "port", 389,
                        "ldapUrl", "ldap://localhost:389",
                        "ldapContextFactory", "com.sun.jndi.ldap.LdapCtxFactory",
                        "endpointUri", "ldap:ldapEnv"),
                "ldap:ou=users",
                "ldap", "ldapEnv"));

        testCases.add(Arguments.of(
                "ollama",
                createJson("baseUrl", "http://localhost:11434", "modelName", "llama2"),
                "langchain4j-chat:myModel",
                "langchain4j-chat", "myModel"));

        testCases.add(Arguments.of(
                "couchbase",
                createJson("hostname", "localhost", "port", 8091, "bucket", "myBucket",
                        "protocol", "http",
                        "endpointUri", "couchbase:http://localhost:8091/myBucket",
                        "connectionBase", "couchbase:http://localhost:8091"),
                "couchbase:myBucket",
                "localhost", "myBucket"));

        testCases.add(Arguments.of(
                "postgres",
                createJson("host", "localhost", "port", 5432, "userName", "postgres", "password", "secret",
                        "jdbcUrl", "jdbc:postgresql://localhost:5432/postgres",
                        "endpointUri", "sql:SELECT 1?dataSource=#postgresDS"),
                "sql:SELECT 1",
                "sql", "SELECT"));

        testCases.add(Arguments.of(
                "ibmmq",
                createJson("channel", "DEV.APP.SVRCONN", "queueManager", "QM1", "listenerPort", 1414),
                "jms:myQueue",
                "jms", "myQueue"));

        testCases.add(Arguments.of(
                "rocketmq",
                createJson("namesrvAddr", "localhost:9876"),
                "rocketmq:myTopic",
                "namesrvAddr", "localhost:9876"));

        testCases.add(Arguments.of(
                "milvus",
                createJson("host", "localhost", "port", 19530),
                "milvus:myCollection",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "qdrant",
                createJson("host", "localhost", "port", 6333),
                "qdrant:myCollection",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "ftp",
                createJson("host", "localhost", "port", 21,
                        "username", "admin", "password", "admin",
                        "endpointUri", "ftp:localhost:21/defaultdir?username=admin&password=RAW(admin)",
                        "connectionBase", "ftp:localhost:21"),
                "ftp:mydir",
                "username", "admin"));

        testCases.add(Arguments.of(
                "ftps",
                createJson("host", "localhost", "port", 21,
                        "username", "admin", "password", "admin",
                        "endpointUri", "ftps:localhost:21/defaultdir?username=admin&password=RAW(admin)",
                        "connectionBase", "ftps:localhost:21"),
                "ftps:mydir",
                "username", "admin"));

        testCases.add(Arguments.of(
                "sftp",
                createJson("host", "localhost", "port", 22,
                        "username", "admin", "password", "admin",
                        "endpointUri", "sftp:localhost:22/defaultdir?username=admin&password=RAW(admin)",
                        "connectionBase", "sftp:localhost:22"),
                "sftp:mydir",
                "username", "admin"));

        testCases.add(Arguments.of(
                "docling",
                createJson("doclingServerUrl", "http://localhost:5000"),
                "rest:post:/convert",
                "rest", "post"));

        testCases.add(Arguments.of(
                "torch-serve",
                createJson("inferencePort", 8080, "managementPort", 8081, "metricsPort", 8082),
                "rest:post:/predictions/model",
                "rest", "post"));

        testCases.add(Arguments.of(
                "fhir",
                createJson("serverUrl", "http://localhost:8080/fhir"),
                "fhir:Patient/search",
                "serverUrl", "http://localhost:8080/fhir"));

        testCases.add(Arguments.of(
                "pulsar",
                createJson("serviceUrl", "pulsar://localhost:6650"),
                "pulsar:persistent://public/default/myTopic",
                "serviceUrl", "pulsar://localhost:6650"));

        testCases.add(Arguments.of(
                "aws",
                createJson("amazonAWSHost", "localhost:4566", "region", "us-east-1",
                        "accessKey", "test", "secretKey", "test"),
                "aws2-s3:myBucket",
                "region", "us-east-1"));

        testCases.add(Arguments.of(
                "azure",
                createJson("host", "localhost", "port", 10000,
                        "accountName", "devstoreaccount1", "accessKey", "testkey",
                        "containerName", "testcontainer",
                        "endpointUri", "azure-storage-blob:devstoreaccount1/testcontainer",
                        "connectionBase", "azure-storage-blob:devstoreaccount1"),
                "azure-storage-blob:myContainer",
                "devstoreaccount1", "myContainer"));

        testCases.add(Arguments.of(
                "google",
                createJson("endpoint", "localhost:8085"),
                "google-pubsub:myTopic",
                "google-pubsub", "myTopic"));

        testCases.add(Arguments.of(
                "microprofile",
                createJson("host", "localhost", "port", 8080),
                "http:localhost:8080/lra-coordinator",
                "host", "localhost"));

        return testCases.stream();
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("infraServiceTestCases")
    @DisplayName("Test endpoint building for infra service")
    void testEndpointBuilding(
            String serviceName, JsonObject mockJson, String baseEndpoint,
            String expectedKey, String expectedValue) {

        String decoded = buildAndDecode(baseEndpoint, serviceName, mockJson);

        boolean hasQueryParam = decoded.contains(expectedKey + "=" + expectedValue);
        boolean hasPathParts = decoded.contains(expectedKey) && decoded.contains(expectedValue);

        assertThat(hasQueryParam || hasPathParts)
                .describedAs("Endpoint for %s should contain '%s' and '%s' (got: %s)",
                        serviceName, expectedKey, expectedValue, decoded)
                .isTrue();

        String expectedScheme = baseEndpoint.split(":")[0];
        assertThat(decoded).startsWith(expectedScheme + ":");

        validateEndpointWithCatalog(serviceName, decoded);
    }

    @Test
    void testKafkaWithBrokersProperty() {
        JsonObject json = new JsonObject();
        json.put("brokers", "localhost:9092");
        json.put("getBootstrapServers", "localhost:9092");

        String decoded = buildAndDecode("kafka:myTopic", "kafka", json);

        assertThat(decoded).contains("brokers=localhost:9092");
        assertThat(decoded).doesNotContain("getBootstrapServers");
    }

    @Test
    void testNatsWithCorrectProperty() {
        JsonObject json = new JsonObject();
        json.put("servers", "localhost:4222");

        String decoded = buildAndDecode("nats:mySubject", "nats", json);

        assertThat(decoded).contains("servers=localhost:4222");
    }

    @Test
    void testMosquittoBrokerUrl() {
        JsonObject json = new JsonObject();
        json.put("brokerUrl", "tcp://localhost:1883");

        String decoded = buildAndDecode("paho-mqtt5:myTopic", "mosquitto", json);

        assertThat(decoded).contains("brokerUrl=tcp://localhost:1883");
    }

    @Test
    void testHiveMqBrokerUrl() {
        JsonObject json = new JsonObject();
        json.put("brokerUrl", "tcp://localhost:1883");

        String decoded = buildAndDecode("paho-mqtt5:myTopic", "hive-mq", json);

        assertThat(decoded).contains("brokerUrl=tcp://localhost:1883");
    }

    @Test
    void testArtemisWithCredentials() {
        JsonObject json = new JsonObject();
        json.put("serviceAddress", "tcp://localhost:61616");
        json.put("userName", "admin");
        json.put("password", "secret");

        String decoded = buildAndDecode("jms:myQueue", "artemis", json);

        assertThat(decoded).contains("password=RAW(secret)");
    }

    @Test
    void testEndpointWithExistingQueryParams() {
        JsonObject json = new JsonObject();
        json.put("brokers", "localhost:9092");

        String result = sendAction.updateEndpointWithConnectionDetails(
                "kafka:myTopic?groupId=myGroup", "kafka", json);

        assertThat(result).isEqualTo("kafka:myTopic?groupId=myGroup");
    }

    @Test
    void testHazelcastEmptyJson() {
        JsonObject json = new JsonObject();

        String result = sendAction.updateEndpointWithConnectionDetails(
                "hazelcast-map:myMap", "hazelcast", json);

        assertThat(result).isEqualTo("hazelcast-map:myMap");
    }

    @Test
    void testNullEndpointCreatesDefault() {
        JsonObject json = new JsonObject();
        json.put("brokers", "localhost:9092");

        String decoded = buildAndDecode(null, "kafka", json);

        assertThat(decoded).startsWith("kafka:default");
        assertThat(decoded).contains("brokers=localhost:9092");
    }

    @Test
    void testEmptyJsonHandledGracefully() {
        JsonObject json = new JsonObject();

        String result = sendAction.updateEndpointWithConnectionDetails(
                "kafka:myTopic", "kafka", json);

        assertThat(result).isEqualTo("kafka:myTopic");
    }

    @Test
    void testRedisPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 6379);
        json.put("endpointUri", "spring-redis:localhost:6379");

        String result = sendAction.updateEndpointWithConnectionDetails(
                "spring-redis:COMMAND", "redis", json);

        assertThat(result).contains("localhost");
        assertThat(result).contains("6379");
        assertThat(result).startsWith("spring-redis:");
    }

    @Test
    void testXmppPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 5222);
        json.put("endpointUri", "xmpp:localhost:5222");

        String result = sendAction.updateEndpointWithConnectionDetails(
                "xmpp:room", "xmpp", json);

        assertThat(result).contains("localhost");
        assertThat(result).contains("5222");
        assertThat(result).startsWith("xmpp:");
    }

    @Test
    void testChatScriptPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("serviceAddress", "localhost:1024");
        json.put("botName", "Harry");
        json.put("endpointUri", "chatscript:localhost:1024/Harry");

        String result = sendAction.updateEndpointWithConnectionDetails(
                "chatscript:Harry", "chat-script", json);

        assertThat(result).isEqualTo("chatscript:localhost:1024/Harry");
    }

    @Test
    void testPulsarWithServiceUrl() {
        JsonObject json = new JsonObject();
        json.put("serviceUrl", "pulsar://localhost:6650");

        String decoded = buildAndDecode("pulsar:persistent://public/default/myTopic", "pulsar", json);

        assertThat(decoded).contains("serviceUrl=pulsar://localhost:6650");
    }

    @Test
    void testCassandraPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("hosts", "localhost:9042");
        json.put("port", 9042);

        String result = sendAction.updateEndpointWithConnectionDetails(
                "cql:cassandra-session:localhost:9042/mykeyspace", "cassandra", json);

        assertThat(result).contains("localhost");
        assertThat(result).startsWith("cql:");
    }

    @Test
    void testFtpPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 21);
        json.put("username", "admin");
        json.put("password", "secret");
        json.put("endpointUri", "ftp:localhost:21/mydir?username=admin&password=RAW(secret)");

        String result = sendAction.updateEndpointWithConnectionDetails(
                "ftp:mydir", "ftp", json);

        assertThat(result).contains("localhost");
        assertThat(result).contains("21");
        assertThat(result).contains("username=admin");
        assertThat(result).startsWith("ftp:");
    }

    @Test
    void testElasticsearchWithHostAddresses() {
        JsonObject json = new JsonObject();
        json.put("hostAddresses", "localhost:9200");

        String decoded = buildAndDecode("elasticsearch:myCluster", "elasticsearch", json);

        assertThat(decoded).contains("hostAddresses=localhost:9200");
    }

    @Test
    void testSolrPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 8983);

        String result = sendAction.updateEndpointWithConnectionDetails(
                "solr:localhost:8983/solr", "solr", json);

        assertThat(result).contains("localhost");
        assertThat(result).startsWith("solr:");
    }

    @Test
    void testOpenLdapWithBeanProperties() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 389);
        json.put("ldapUrl", "ldap://localhost:389");
        json.put("ldapContextFactory", "com.sun.jndi.ldap.LdapCtxFactory");
        json.put("endpointUri", "ldap:ldapEnv");

        JsonObject beanProps = new JsonObject();
        beanProps.put("camel.beans.ldapEnv", "#class:java.util.Hashtable");
        beanProps.put("camel.beans.ldapEnv[java.naming.factory.initial]", "com.sun.jndi.ldap.LdapCtxFactory");
        beanProps.put("camel.beans.ldapEnv[java.naming.provider.url]", "ldap://localhost:389");
        json.put("beanProperties", beanProps);

        String result = sendAction.updateEndpointWithConnectionDetails(
                "ldap:ou=users", "openldap", json);

        assertThat(result).isEqualTo("ldap:ldapEnv");
    }

    @Test
    void testPostgresWithBeanProperties() {
        JsonObject json = new JsonObject();
        json.put("host", "localhost");
        json.put("port", 5432);
        json.put("jdbcUrl", "jdbc:postgresql://localhost:5432/postgres");
        json.put("endpointUri", "sql:SELECT 1?dataSource=#postgresDS");

        JsonObject beanProps = new JsonObject();
        beanProps.put("camel.beans.postgresDS", "#class:org.postgresql.ds.PGSimpleDataSource");
        beanProps.put("camel.beans.postgresDS.url", "jdbc:postgresql://localhost:5432/postgres");
        beanProps.put("camel.beans.postgresDS.user", "postgres");
        beanProps.put("camel.beans.postgresDS.password", "secret");
        json.put("beanProperties", beanProps);

        String result = sendAction.updateEndpointWithConnectionDetails(
                "sql:SELECT 1", "postgres", json);

        assertThat(result).isEqualTo("sql:SELECT 1?dataSource=#postgresDS");
    }

    @Test
    void testCatalogValidationCatchesInvalidProperties() {
        String invalidUri = "kafka:myTopic?brokers=localhost:9092&invalidFakeProperty=badvalue";

        EndpointValidationResult result = catalog.validateEndpointProperties(invalidUri);

        assertThat(result.getUnknown())
                .describedAs("Catalog should detect 'invalidFakeProperty' as unknown")
                .isNotNull()
                .contains("invalidFakeProperty");
    }

    @Test
    void testCatalogValidationPassesForValidProperties() {
        String validUri = "kafka:myTopic?brokers=localhost:9092";

        EndpointValidationResult result = catalog.validateEndpointProperties(validUri);

        assertThat(result.getUnknown())
                .describedAs("Valid URI should have no unknown properties")
                .isNullOrEmpty();
    }

    private static JsonObject createJson(Object... keyValues) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            json.put((String) keyValues[i], keyValues[i + 1]);
        }
        return json;
    }
}
