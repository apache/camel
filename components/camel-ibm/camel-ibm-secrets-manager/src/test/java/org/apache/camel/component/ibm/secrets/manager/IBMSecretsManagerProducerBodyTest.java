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
package org.apache.camel.component.ibm.secrets.manager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.cloud.sdk.core.security.NoAuthAuthenticator;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.SecretsManager;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.Secret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bodies the producer takes and gives, with the IBM Secrets Manager SDK against a JDK HttpServer that plays the
 * service and records the requests.
 */
class IBMSecretsManagerProducerBodyTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SECRETS_PATH = "/api/v2/secrets";

    // the secrets the server knows, by id, which is also the secret type
    private static final Map<String, String> SECRETS = Map.of(
            "arbitrary", "{\"payload\":\"s3cr3t\"}",
            "kv", "{\"data\":{\"username\":\"admin\",\"password\":\"s3cr3t\"}}",
            "username_password", "{\"username\":\"admin\",\"password\":\"s3cr3t\"}",
            "iam_credentials", "{\"api_key\":\"my-api-key\",\"service_id\":\"my-service-id\"}",
            "imported_cert", "{\"certificate\":\"-----BEGIN CERTIFICATE-----\",\"common_name\":\"example.com\"}",
            "public_cert", "{\"certificate\":\"-----BEGIN CERTIFICATE-----\",\"common_name\":\"example.com\"}",
            "private_cert", "{\"certificate\":\"-----BEGIN CERTIFICATE-----\",\"common_name\":\"example.com\"}",
            "service_credentials", "{\"credentials\":{\"apikey\":\"my-api-key\"}}",
            "custom_credentials", "{\"credentials_content\":{\"token\":\"my-token\"}}");

    private static final Map<String, String> REQUESTS = new ConcurrentHashMap<>();
    private static HttpServer server;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", IBMSecretsManagerProducerBodyTest::handle);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.put(method + " " + path, request);

        String response = null;
        int status = 200;
        if ("GET".equals(method) && path.startsWith(SECRETS_PATH + "/")) {
            // get a secret: the id is the type
            String id = path.substring(SECRETS_PATH.length() + 1);
            String fields = SECRETS.get(id);
            if (fields != null) {
                response = secret(id, id, fields);
            }
        } else if ("POST".equals(method) && path.equals(SECRETS_PATH)) {
            // create a secret
            String type = MAPPER.readTree(request).path("secret_type").asText();
            response = secret("new-secret-id", type, "{}");
            status = 201;
        } else if ("POST".equals(method) && path.startsWith(SECRETS_PATH + "/") && path.endsWith("/versions")) {
            // create a version of a secret
            String id = path.substring(SECRETS_PATH.length() + 1, path.length() - "/versions".length());
            ObjectNode version = MAPPER.createObjectNode();
            version.put("id", "new-version-id");
            version.put("secret_id", id);
            version.put("secret_type", id);
            response = version.toString();
            status = 201;
        }
        if (response == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String secret(String id, String type, String fields) throws IOException {
        ObjectNode secret = (ObjectNode) MAPPER.readTree(fields);
        secret.put("id", id);
        secret.put("name", "my-secret");
        secret.put("secret_type", type);
        return secret.toString();
    }

    @BeforeEach
    void useTestServer() {
        REQUESTS.clear();
        // the endpoint is started with an IAM client: use a client without authentication that calls the test server
        SecretsManager client = new SecretsManager("test", new NoAuthAuthenticator());
        client.setServiceUrl("http://localhost:" + server.getAddress().getPort());
        context.getEndpoint(endpointUri(), IBMSecretsManagerEndpoint.class).setSecretManager(client);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a body that becomes a Map only through a type converter
        context.getTypeConverterRegistry().addTypeConverter(Map.class, KeyValue.class, new TypeConverterSupport() {
            @Override
            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                KeyValue keyValue = (KeyValue) value;
                return type.cast(Map.of(keyValue.key(), keyValue.value()));
            }
        });
        return context;
    }

    private static String endpointUri() {
        return "ibm-secrets-manager:test?token=test-token&serviceUrl=http://localhost:" + server.getAddress().getPort();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:secrets").to(endpointUri());
            }
        };
    }

    // 1. getSecret and updateSecret for the other secret types

    @ParameterizedTest
    @ValueSource(strings = {
            "username_password", "iam_credentials", "imported_cert", "public_cert", "private_cert", "service_credentials",
            "custom_credentials" })
    void getSecretGivesTheSecretOfTheOtherTypes(String type) {
        Exchange result = getSecret(type);

        Secret secret = assertInstanceOf(Secret.class, result.getMessage().getBody());
        assertEquals(type, secret.getSecretType());
        assertEquals(type, secret.getId());
    }

    @Test
    void getSecretGivesTheUsernameAndPasswordOfAUsernamePasswordSecret() {
        Secret secret = assertInstanceOf(Secret.class, getSecret("username_password").getMessage().getBody());

        assertEquals("admin", secret.getUsername());
        assertEquals("s3cr3t", secret.getPassword());
    }

    @Test
    void getSecretGivesTheApiKeyOfAnIamCredentialsSecret() {
        Secret secret = assertInstanceOf(Secret.class, getSecret("iam_credentials").getMessage().getBody());

        assertEquals("my-api-key", secret.getApiKey());
    }

    @Test
    void getSecretGivesTheCertificateOfAnImportedCertificate() {
        Secret secret = assertInstanceOf(Secret.class, getSecret("imported_cert").getMessage().getBody());

        assertEquals("-----BEGIN CERTIFICATE-----", secret.getCertificate());
    }

    @Test
    void getSecretGivesThePayloadOfAnArbitrarySecretAndTheMapOfAKvSecret() {
        assertEquals("s3cr3t", getSecret("arbitrary").getMessage().getBody());
        assertEquals(Map.of("username", "admin", "password", "s3cr3t"), getSecret("kv").getMessage().getBody());
    }

    @Test
    void updateSecretFailsClearlyForTheOtherTypes() {
        Exchange result = updateSecret("username_password", "new-password");

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().contains("username_password"), e.getMessage());
        assertTrue(e.getMessage().contains("supports arbitrary and kv secrets"), e.getMessage());
        assertNull(REQUESTS.get("POST " + SECRETS_PATH + "/username_password/versions"), "no version is created");
    }

    // 2. createKVSecret and a JSON object

    @Test
    void createKVSecretTakesAJsonObjectText() throws Exception {
        Exchange result = createKVSecret("{\"username\": \"admin\", \"password\": \"s3cr3t\", \"port\": 5432}");

        assertEquals("new-secret-id", result.getMessage().getBody());
        JsonNode request = request("POST " + SECRETS_PATH);
        assertEquals("kv", request.get("secret_type").asText());
        assertEquals(MAPPER.readTree("{\"username\": \"admin\", \"password\": \"s3cr3t\", \"port\": 5432}"),
                request.get("data"));
    }

    @Test
    void createKVSecretTakesAJsonObjectAsBytes() throws Exception {
        createKVSecret("{\"username\": \"admin\"}".getBytes(StandardCharsets.UTF_8));

        assertEquals(MAPPER.readTree("{\"username\": \"admin\"}"), request("POST " + SECRETS_PATH).get("data"));
    }

    @Test
    void createKVSecretTakesAJsonObjectAsAStream() throws Exception {
        createKVSecret(new ByteArrayInputStream("{\"username\": \"admin\"}".getBytes(StandardCharsets.UTF_8)));

        assertEquals(MAPPER.readTree("{\"username\": \"admin\"}"), request("POST " + SECRETS_PATH).get("data"));
    }

    @Test
    void createKVSecretTakesAMap() throws Exception {
        createKVSecret(Map.of("username", "admin"));

        assertEquals(MAPPER.readTree("{\"username\": \"admin\"}"), request("POST " + SECRETS_PATH).get("data"));
    }

    @Test
    void createKVSecretTakesABodyThatConvertsToAMap() throws Exception {
        createKVSecret(new KeyValue("username", "admin"));

        assertEquals(MAPPER.readTree("{\"username\": \"admin\"}"), request("POST " + SECRETS_PATH).get("data"));
    }

    @Test
    void createKVSecretFailsForJsonThatIsNotAnObject() {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.createKVSecret);
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "my-secret");
            exchange.getIn().setBody("[\"admin\", \"s3cr3t\"]");
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().contains("is not a JSON object"), e.getMessage());
        assertNull(REQUESTS.get("POST " + SECRETS_PATH), "no secret is created");
    }

    @Test
    void createKVSecretDoesNotQuoteATextThatIsNotJson() {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.createKVSecret);
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "my-secret");
            exchange.getIn().setBody("s3cr3t-password");
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().contains("is not a JSON object"), e.getMessage());
        // the error is logged: it must not quote the secret, as the message of the JSON parser does
        StringWriter trace = new StringWriter();
        e.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains("s3cr3t"), trace.toString());
    }

    @Test
    void updateSecretOfAKvSecretTakesAJsonObjectText() throws Exception {
        Exchange result = updateSecret("kv", "{\"password\": \"new-password\"}");

        assertNoException(result);
        assertEquals(MAPPER.readTree("{\"password\": \"new-password\"}"),
                request("POST " + SECRETS_PATH + "/kv/versions").get("data"));
    }

    // 3. createArbitrarySecret and updateSecret store a Map or a POJO as JSON

    @Test
    void createArbitrarySecretStoresAMapAsJson() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", "admin");
        body.put("port", 5432);

        createArbitrarySecret(body);

        assertEquals(MAPPER.readTree("{\"username\": \"admin\", \"port\": 5432}"), payloadOf("POST " + SECRETS_PATH));
    }

    @Test
    void createArbitrarySecretStoresAPojoAsJson() throws Exception {
        createArbitrarySecret(new DatabaseCredentials("admin", "s3cr3t"));

        assertEquals(MAPPER.readTree("{\"username\": \"admin\", \"password\": \"s3cr3t\"}"),
                payloadOf("POST " + SECRETS_PATH));
    }

    @Test
    void createArbitrarySecretStoresAListAsJson() throws Exception {
        createArbitrarySecret(List.of("admin", "s3cr3t"));

        assertEquals(MAPPER.readTree("[\"admin\", \"s3cr3t\"]"), payloadOf("POST " + SECRETS_PATH));
    }

    @Test
    void createArbitrarySecretReadsTextBytesAndNumbersAsText() throws Exception {
        createArbitrarySecret("s3cr3t");
        assertEquals("s3cr3t", request("POST " + SECRETS_PATH).get("payload").asText());

        createArbitrarySecret("s3cr3t-bytes".getBytes(StandardCharsets.UTF_8));
        assertEquals("s3cr3t-bytes", request("POST " + SECRETS_PATH).get("payload").asText());

        createArbitrarySecret(new ByteArrayInputStream("s3cr3t".getBytes(StandardCharsets.UTF_8)));
        assertEquals("s3cr3t", request("POST " + SECRETS_PATH).get("payload").asText());

        createArbitrarySecret(1234);
        assertEquals("1234", request("POST " + SECRETS_PATH).get("payload").asText());
    }

    @Test
    void createArbitrarySecretReadsADomDocumentAndASourceAsXml() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        document.appendChild(document.createElement("user")).setTextContent("admin");
        String xml = context.getTypeConverter().convertTo(String.class, document);
        assertTrue(xml.contains("<user>admin</user>"), xml);

        createArbitrarySecret(document);
        assertEquals(xml, request("POST " + SECRETS_PATH).get("payload").asText(), "the XML, not a JSON string");

        createArbitrarySecret(new DOMSource(document));
        assertEquals(xml, request("POST " + SECRETS_PATH).get("payload").asText(), "the XML, not a JSON string");
    }

    @Test
    void createArbitrarySecretReadsAByteBufferAsText() throws Exception {
        createArbitrarySecret(ByteBuffer.wrap("s3cr3t".getBytes(StandardCharsets.UTF_8)));

        assertEquals("s3cr3t", request("POST " + SECRETS_PATH).get("payload").asText(), "the text, not Base64");
    }

    @Test
    void createArbitrarySecretReadsAUuidAsText() throws Exception {
        UUID uuid = UUID.randomUUID();

        createArbitrarySecret(uuid);

        assertEquals(uuid.toString(), request("POST " + SECRETS_PATH).get("payload").asText(), "the text, not quoted");
    }

    @Test
    void createArbitrarySecretFailsWithoutABody() {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "my-secret");
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, result.getException());
        assertTrue(e.getMessage().startsWith("Secret payload must be provided"), e.getMessage());
    }

    @Test
    void updateSecretOfAnArbitrarySecretStoresAMapAsJson() throws Exception {
        Exchange result = updateSecret("arbitrary", Map.of("username", "admin"));

        assertNoException(result);
        assertEquals(MAPPER.readTree("{\"username\": \"admin\"}"), payloadOf("POST " + SECRETS_PATH + "/arbitrary/versions"));
    }

    private Exchange getSecret(String id) {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.getSecret);
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_ID, id);
        });
        assertNoException(result);
        return result;
    }

    private Exchange updateSecret(String id, Object body) {
        return template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.updateSecret);
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_ID, id);
            exchange.getIn().setBody(body);
        });
    }

    private Exchange createKVSecret(Object body) {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.OPERATION, IBMSecretsManagerOperation.createKVSecret);
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "my-secret");
            exchange.getIn().setBody(body);
        });
        assertNoException(result);
        return result;
    }

    private Exchange createArbitrarySecret(Object body) {
        Exchange result = template.send("direct:secrets", exchange -> {
            exchange.getIn().setHeader(IBMSecretsManagerConstants.SECRET_NAME, "my-secret");
            exchange.getIn().setBody(body);
        });
        assertNoException(result);
        assertEquals("new-secret-id", result.getMessage().getBody());
        return result;
    }

    private static JsonNode request(String key) throws IOException {
        String body = REQUESTS.get(key);
        if (body == null) {
            throw new AssertionError("no request " + key + " in " + REQUESTS.keySet());
        }
        return MAPPER.readTree(body);
    }

    /**
     * The payload of the secret or version the request creates, read as JSON.
     */
    private static JsonNode payloadOf(String key) throws IOException {
        String payload = request(key).get("payload").asText();
        try {
            return MAPPER.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new AssertionError("the payload is not JSON: " + payload, e);
        }
    }

    private static void assertNoException(Exchange result) {
        if (result.getException() != null) {
            throw new AssertionError("the exchange failed: " + result.getException(), result.getException());
        }
    }

    record KeyValue(String key, String value) {
    }

    static final class DatabaseCredentials {

        private final String username;
        private final String password;

        DatabaseCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        @Override
        public String toString() {
            return "DatabaseCredentials[username=" + username + "]";
        }
    }
}
