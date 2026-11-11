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
package org.apache.camel.component.azure.key.vault;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.DeletedSecret;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer with the real SecretClient against a fake of the REST API of Key Vault. deleteSecret gives the
 * DeletedSecret, and createSecret stores a Map, a List or a POJO as JSON, and text, bytes, a stream or a file as text.
 */
class KeyVaultProducerTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final long DELETED_ON = 1700000000L;
    private static final long PURGE_ON = 1707776000L;
    // as Key Vault sends them: the SDK needs the recovery level of a deleted secret
    private static final Map<String, Object> ATTRIBUTES = Map.of(
            "enabled", true, "created", DELETED_ON, "updated", DELETED_ON, "recoveryLevel", "Recoverable+Purgeable");

    private final FakeKeyVault vault = new FakeKeyVault();

    @BindToRegistry("secretClient")
    private final SecretClient secretClient = new SecretClientBuilder()
            .vaultUrl(vault.url())
            .pipeline(new HttpPipelineBuilder().httpClient(HttpClient.createDefault()).build())
            .buildClient();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSecret")
                        .setHeader(KeyVaultConstants.SECRET_NAME, constant("mySecret"))
                        .to("azure-key-vault:myVault?secretClient=#secretClient&operation=createSecret");
                from("direct:deleteSecret")
                        .setHeader(KeyVaultConstants.SECRET_NAME, constant("mySecret"))
                        .to("azure-key-vault:myVault?secretClient=#secretClient&operation=deleteSecret");
            }
        };
    }

    @AfterEach
    void stopVault() {
        vault.stop();
    }

    @Test
    void deleteSecretGivesTheDeletedSecret() {
        Exchange exchange = template.send("direct:deleteSecret", e -> e.getIn().setBody("ignored"));

        assertNull(exchange.getException());
        DeletedSecret deleted = assertInstanceOf(DeletedSecret.class, exchange.getMessage().getBody());
        assertEquals("mySecret", deleted.getName());
        assertEquals(vault.url() + "/deletedsecrets/mySecret", deleted.getRecoveryId());
        assertEquals(OffsetDateTime.ofInstant(Instant.ofEpochSecond(DELETED_ON), ZoneOffset.UTC), deleted.getDeletedOn());
        assertEquals(OffsetDateTime.ofInstant(Instant.ofEpochSecond(PURGE_ON), ZoneOffset.UTC),
                deleted.getScheduledPurgeDate());
        assertEquals(List.of("DELETE /secrets/mySecret", "GET /deletedsecrets/mySecret"), vault.requests);
    }

    @Test
    void mapIsStoredAsJson() throws Exception {
        Map<String, Object> credentials = new LinkedHashMap<>();
        credentials.put("username", "admin");
        credentials.put("password", "s3cr3t");
        credentials.put("port", 5432);

        Exchange exchange = template.send("direct:createSecret", e -> e.getIn().setBody(credentials));

        assertNull(exchange.getException());
        assertEquals("{\"username\":\"admin\",\"password\":\"s3cr3t\",\"port\":5432}", vault.storedValue());
        // the JSON reads back as the Map
        assertEquals(credentials, MAPPER.readValue(vault.storedValue(), Map.class));
        KeyVaultSecret secret = assertInstanceOf(KeyVaultSecret.class, exchange.getMessage().getBody());
        assertEquals("mySecret", secret.getName());
        assertEquals(vault.storedValue(), secret.getValue());
    }

    @Test
    void pojoIsStoredAsJson() {
        Exchange exchange = template.send("direct:createSecret",
                e -> e.getIn().setBody(new Credentials("admin", "s3cr3t")));

        assertNull(exchange.getException());
        assertEquals("{\"username\":\"admin\",\"password\":\"s3cr3t\"}", vault.storedValue());
    }

    @Test
    void listIsStoredAsJson() {
        Exchange exchange = template.send("direct:createSecret", e -> e.getIn().setBody(List.of("10.0.0.1", "10.0.0.2")));

        assertNull(exchange.getException());
        assertEquals("[\"10.0.0.1\",\"10.0.0.2\"]", vault.storedValue());
    }

    @Test
    void mapThatIsNotJsonFails() {
        Map<String, Object> body = Map.of("value", new Object());

        Exchange exchange = template.send("direct:createSecret", e -> e.getIn().setBody(body));

        IllegalArgumentException error = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(error.getMessage().contains("body of createSecret cannot be stored as JSON"), error.getMessage());
        assertTrue(vault.requests.isEmpty());
    }

    @Test
    void dateAndTimeInAMapOrPojoAreStoredAsIsoText() {
        OffsetDateTime expires = OffsetDateTime.of(2026, 1, 1, 12, 30, 0, 0, ZoneOffset.UTC);
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("token", "abc");
        token.put("expires", expires);

        Exchange exchange = template.send("direct:createSecret", e -> e.getIn().setBody(token));
        assertNull(exchange.getException());
        assertEquals("{\"token\":\"abc\",\"expires\":\"2026-01-01T12:30:00Z\"}", vault.storedValue());

        exchange = template.send("direct:createSecret", e -> e.getIn().setBody(new Lease("abc", LocalDate.of(2026, 1, 31))));
        assertNull(exchange.getException());
        assertEquals("{\"token\":\"abc\",\"until\":\"2026-01-31\"}", vault.storedValue());
    }

    @Test
    void textBytesStreamAndFileAreStoredAsText(@TempDir Path dir) throws Exception {
        String value = "pässwörd-€";

        template.sendBody("direct:createSecret", value);
        assertEquals(value, vault.storedValue());

        template.sendBody("direct:createSecret", value.getBytes(StandardCharsets.UTF_8));
        assertEquals(value, vault.storedValue());

        template.sendBody("direct:createSecret", new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
        assertEquals(value, vault.storedValue());

        Path file = Files.writeString(dir.resolve("secret.txt"), value);
        template.sendBody("direct:createSecret", file.toFile());
        assertEquals(value, vault.storedValue());
    }

    @Test
    void xmlIsStoredAsItsText() throws Exception {
        String xml = "<credentials><username>admin</username></credentials>";
        Document document = context.getTypeConverter().mandatoryConvertTo(Document.class, xml);

        // the type converter gives the XML text of a Source, which Jackson would write as a bean
        template.sendBody("direct:createSecret", new DOMSource(document));
        assertEquals(xml, vault.storedValue());

        // and of a DOM node, which Jackson writes as a JSON string
        template.sendBody("direct:createSecret", document);
        assertEquals(xml, vault.storedValue());
    }

    @Test
    void valueIsStoredAsItsText() {
        UUID uuid = UUID.randomUUID();
        template.sendBody("direct:createSecret", uuid);
        assertEquals(uuid.toString(), vault.storedValue());

        template.sendBody("direct:createSecret", 42);
        assertEquals("42", vault.storedValue());

        OffsetDateTime dateTime = OffsetDateTime.of(2026, 1, 1, 12, 30, 0, 0, ZoneOffset.ofHours(1));
        template.sendBody("direct:createSecret", dateTime);
        assertEquals(dateTime.toString(), vault.storedValue());
    }

    @Test
    void noBodyFails() {
        Exchange exchange = template.send("direct:createSecret", e -> e.getIn().setBody(null));

        assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertTrue(vault.requests.isEmpty());
    }

    record Credentials(String username, String password) {
    }

    record Lease(String token, LocalDate until) {
    }

    /**
     * The REST API of Key Vault for setSecret, deleteSecret and the poll of the deleted secret.
     */
    static final class FakeKeyVault {

        final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<String> storedValues = new CopyOnWriteArrayList<>();
        private final HttpServer server;

        FakeKeyVault() {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/", this::handle);
            server.start();
        }

        String url() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        String storedValue() {
            assertFalse(storedValues.isEmpty(), "no secret was stored");
            return storedValues.get(storedValues.size() - 1);
        }

        void stop() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            requests.add(method + " " + path);
            String name = path.substring(path.lastIndexOf('/') + 1);
            String response;
            if ("PUT".equals(method) && path.startsWith("/secrets/")) {
                String value = MAPPER.readTree(exchange.getRequestBody()).get("value").asString();
                storedValues.add(value);
                Map<String, Object> bundle = new LinkedHashMap<>();
                bundle.put("value", value);
                bundle.put("id", url() + "/secrets/" + name + "/1");
                bundle.put("attributes", ATTRIBUTES);
                response = MAPPER.writeValueAsString(bundle);
            } else if ("DELETE".equals(method) && path.startsWith("/secrets/")
                    || "GET".equals(method) && path.startsWith("/deletedsecrets/")) {
                Map<String, Object> bundle = new LinkedHashMap<>();
                bundle.put("recoveryId", url() + "/deletedsecrets/" + name);
                bundle.put("deletedDate", DELETED_ON);
                bundle.put("scheduledPurgeDate", PURGE_ON);
                bundle.put("id", url() + "/secrets/" + name + "/1");
                bundle.put("attributes", ATTRIBUTES);
                response = MAPPER.writeValueAsString(bundle);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
