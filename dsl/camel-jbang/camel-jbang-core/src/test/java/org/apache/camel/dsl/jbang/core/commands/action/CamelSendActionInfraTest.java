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
import java.util.stream.Stream;

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

    @BeforeEach
    public void setup() throws Exception {
        super.setup();
        sendAction = new CamelSendAction(new CamelJBangMain().withPrinter(printer));
    }

    private String buildAndDecode(String endpoint, String service, JsonObject json) {
        String result = sendAction.updateEndpointWithConnectionDetails(endpoint, service, json);
        return URLDecoder.decode(result, StandardCharsets.UTF_8);
    }

    static Stream<Arguments> infraServiceTestCases() {
        List<Arguments> testCases = new ArrayList<>();

        testCases.add(Arguments.of(
                "nats",
                createJson("getServiceAddress", "localhost:4222"),
                "nats:myTopic",
                "servers", "localhost:4222"));

        testCases.add(Arguments.of(
                "kafka",
                createJson("brokers", "localhost:9092"),
                "kafka:myTopic",
                "brokers", "localhost:9092"));

        testCases.add(Arguments.of(
                "mosquitto",
                createJson("getPort", 1883),
                "paho-mqtt5:myTopic",
                "brokerUrl", "tcp://localhost:1883"));

        testCases.add(Arguments.of(
                "hive-mq",
                createJson("getMqttHostAddress", "tcp://localhost:1883"),
                "paho-mqtt5:myTopic",
                "brokerUrl", "tcp://localhost:1883"));

        testCases.add(Arguments.of(
                "artemis",
                createJson("serviceAddress", "tcp://localhost:61616"),
                "jms:myQueue",
                "brokerURL", "tcp://localhost:61616"));

        testCases.add(Arguments.of(
                "rabbitmq",
                createJson("uri", "amqp://localhost:5672"),
                "spring-rabbitmq:myExchange",
                "addresses", "amqp://localhost:5672"));

        testCases.add(Arguments.of(
                "mongodb",
                createJson("getReplicaSetUrl", "mongodb://localhost:27017"),
                "mongodb:myDatabase",
                "hosts", "mongodb://localhost:27017"));

        testCases.add(Arguments.of(
                "minio",
                createJson("accessKey", "minioadmin", "secretKey", "minioadmin", "endpoint", "http://localhost:9000"),
                "minio:myBucket",
                "endpoint", "http://localhost:9000"));

        testCases.add(Arguments.of(
                "infinispan",
                createJson("host", "localhost", "port", 11222, "username", "admin", "password", "secret"),
                "infinispan:myCache",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "zookeeper",
                createJson("getConnectionString", "localhost:2181"),
                "zookeeper:/myPath",
                "servers", "localhost:2181"));

        testCases.add(Arguments.of(
                "couchdb",
                createJson("host", "localhost", "port", 5984),
                "couchdb:myDatabase",
                "host", "localhost"));

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
                createJson("getKeycloakServerUrl", "http://localhost:8080",
                        "getKeycloakRealm", "myrealm"),
                "http:localhost:8080/auth",
                "serverUrl", "http://localhost:8080"));

        testCases.add(Arguments.of(
                "smb",
                createJson("address", "localhost", "shareName", "myShare",
                        "userName", "user", "password", "pass"),
                "smb:myShare",
                "address", "localhost"));

        testCases.add(Arguments.of(
                "openldap",
                createJson("getHost", "localhost"),
                "ldap:ou=users",
                "ldapServerUrl", "localhost"));

        testCases.add(Arguments.of(
                "ollama",
                createJson("baseUrl", "http://localhost:11434", "modelName", "llama2"),
                "langchain4j-chat:myModel",
                "baseUrl", "http://localhost:11434"));

        testCases.add(Arguments.of(
                "couchbase",
                createJson("getConnectionString", "localhost:8091", "hostname", "localhost", "port", 8091),
                "couchbase:myBucket",
                "connectionString", "localhost:8091"));

        testCases.add(Arguments.of(
                "postgres",
                createJson("host", "localhost", "port", 5432, "userName", "postgres", "password", "secret"),
                "sql:SELECT 1",
                "host", "localhost"));

        testCases.add(Arguments.of(
                "ibmmq",
                createJson("channel", "DEV.APP.SVRCONN", "queueManager", "QM1", "listenerPort", 1414),
                "jms:myQueue",
                "channel", "DEV.APP.SVRCONN"));

        testCases.add(Arguments.of(
                "rocketmq",
                createJson("nameserverAddress", "localhost:9876"),
                "rocketmq:myTopic",
                "nameserverAddress", "localhost:9876"));

        testCases.add(Arguments.of(
                "milvus",
                createJson("getMilvusEndpointUrl", "http://localhost:19530", "host", "localhost", "port", 19530),
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
                        "username", "admin", "password", "admin"),
                "ftp:localhost/mydir",
                "username", "RAW(admin)"));

        testCases.add(Arguments.of(
                "ftps",
                createJson("host", "localhost", "port", 21,
                        "username", "admin", "password", "admin"),
                "ftps:localhost/mydir",
                "username", "RAW(admin)"));

        testCases.add(Arguments.of(
                "sftp",
                createJson("host", "localhost", "port", 22,
                        "username", "admin", "password", "admin"),
                "sftp:localhost/mydir",
                "username", "RAW(admin)"));

        testCases.add(Arguments.of(
                "docling",
                createJson("doclingServerUrl", "http://localhost:5000"),
                "rest:post:/convert",
                "doclingServerUrl", "http://localhost:5000"));

        testCases.add(Arguments.of(
                "torch-serve",
                createJson("inferencePort", 8080, "managementPort", 8081, "metricsPort", 8082),
                "rest:post:/predictions/model",
                "inferencePort", "8080"));

        testCases.add(Arguments.of(
                "fhir",
                createJson("getServiceBaseURL", "http://localhost:8080/fhir"),
                "fhir:Patient/search",
                "serverUrl", "http://localhost:8080/fhir"));

        testCases.add(Arguments.of(
                "pulsar",
                createJson("getPulsarBrokerUrl", "pulsar://localhost:6650"),
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
                        "accountName", "devstoreaccount1", "accessKey", "testkey"),
                "azure-storage-blob:myContainer",
                "serviceEndpoint", "http://localhost:10000"));

        testCases.add(Arguments.of(
                "google",
                createJson("getServiceAddress", "localhost:8085"),
                "google-pubsub:myTopic",
                "endpoint", "localhost:8085"));

        testCases.add(Arguments.of(
                "microprofile",
                createJson("host", "localhost", "port", 8080,
                        "getServiceAddress", "http://localhost:8080"),
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

        assertThat(decoded)
                .describedAs("Endpoint for %s should contain %s=%s", serviceName, expectedKey, expectedValue)
                .contains(expectedKey + "=" + expectedValue);

        String expectedScheme = baseEndpoint.split(":")[0];
        assertThat(decoded).startsWith(expectedScheme + ":");
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
    void testNatsWithDeprecatedProperty() {
        JsonObject json = new JsonObject();
        json.put("getServiceAddress", "localhost:4222");

        String decoded = buildAndDecode("nats:mySubject", "nats", json);

        assertThat(decoded).contains("servers=localhost:4222");
    }

    @Test
    void testMosquittoPortConstruction() {
        JsonObject json = new JsonObject();
        json.put("getPort", 1883);

        String decoded = buildAndDecode("paho-mqtt5:myTopic", "mosquitto", json);

        assertThat(decoded).contains("brokerUrl=tcp://localhost:1883");
    }

    @Test
    void testHiveMqMqttAddress() {
        JsonObject json = new JsonObject();
        json.put("getMqttHostAddress", "tcp://localhost:1883");

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

        assertThat(decoded).contains("brokerURL=tcp://localhost:61616");
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

        String result = sendAction.updateEndpointWithConnectionDetails(
                "chatscript:Harry", "chat-script", json);

        assertThat(result).isEqualTo("chatscript:localhost:1024/Harry");
    }

    @Test
    void testPulsarWithServiceUrl() {
        JsonObject json = new JsonObject();
        json.put("getPulsarBrokerUrl", "pulsar://localhost:6650");

        String decoded = buildAndDecode("pulsar:persistent://public/default/myTopic", "pulsar", json);

        assertThat(decoded).contains("serviceUrl=pulsar://localhost:6650");
    }

    @Test
    void testCassandraPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("getCassandraHost", "localhost");
        json.put("getCassandraPort", 9042);

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

        String result = sendAction.updateEndpointWithConnectionDetails(
                "ftp:localhost/mydir", "ftp", json);

        assertThat(result).contains("localhost");
        assertThat(result).startsWith("ftp:");
    }

    @Test
    void testElasticsearchWithHostAddresses() {
        JsonObject json = new JsonObject();
        json.put("getHttpHostAddress", "localhost:9200");

        String decoded = buildAndDecode("elasticsearch:myCluster", "elasticsearch", json);

        assertThat(decoded).contains("localhost:9200");
    }

    @Test
    void testSolrPathBasedEndpoint() {
        JsonObject json = new JsonObject();
        json.put("getSolrHost", "localhost");
        json.put("getSolrPort", 8983);

        String result = sendAction.updateEndpointWithConnectionDetails(
                "solr:localhost:8983/solr", "solr", json);

        assertThat(result).contains("localhost");
        assertThat(result).startsWith("solr:");
    }

    private static JsonObject createJson(Object... keyValues) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < keyValues.length; i += 2) {
            json.put((String) keyValues[i], keyValues[i + 1]);
        }
        return json;
    }
}
