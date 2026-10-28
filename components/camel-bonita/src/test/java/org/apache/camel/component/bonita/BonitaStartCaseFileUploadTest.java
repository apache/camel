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
package org.apache.camel.component.bonita;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.ws.rs.InternalServerErrorException;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bonita.api.model.FileInput;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts cases with a file input through two bonita endpoints that point to two different Bonita servers (two WireMock
 * servers) with two different accounts, and checks where each request of an exchange went.
 */
class BonitaStartCaseFileUploadTest {

    private static final String LOGIN = "/bonita/loginservice";
    private static final String PROCESS = "/bonita/API/bpm/process";
    private static final String UPLOAD = "/bonita/portal/resource/process/orders/1.0/API/formFileUpload";
    private static final String INSTANTIATION = "/bonita/API/bpm/process/7/instantiation";
    // one part: boundary, headers with the disposition that names the part like a temporary file, content, closing boundary
    private static final Pattern UPLOADED_PART = Pattern.compile(
            "(?:\r\n)?--(\\S+)\r\n(?:(?!Content-Disposition)[^\r\n]+\r\n)*"
                                                                 + "Content-Disposition: form-data;filename=(tempFile\\d+\\.tmp);name=file\r\n"
                                                                 + "(?:[^\r\n]+\r\n)*\r\n(.*)\r\n--(\\S+)--(?:\r\n)?",
            Pattern.DOTALL);

    private WireMockServer serverA;
    private WireMockServer serverB;
    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setup() throws Exception {
        serverA = startBonita("A");
        serverB = startBonita("B");

        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").to(bonita(serverA, "A"));
                from("direct:b").to(bonita(serverB, "B"));
            }
        });
        context.start();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (template != null) {
            template.stop();
        }
        if (context != null) {
            context.stop();
        }
        if (serverA != null) {
            serverA.stop();
        }
        if (serverB != null) {
            serverB.stop();
        }
    }

    @Test
    void fileOfSecondEndpointIsUploadedToItsOwnServer() {
        startCase("direct:a");
        assertOwnRequestsOnly(serverA, "A", 1);
        assertRequests(serverB, 0, 0, 0, 0);

        startCase("direct:b");
        assertOwnRequestsOnly(serverB, "B", 1);
        assertOwnRequestsOnly(serverA, "A", 1);

        startCase("direct:a");
        assertOwnRequestsOnly(serverA, "A", 2);
        assertOwnRequestsOnly(serverB, "B", 1);
    }

    @Test
    void fileOfFirstEndpointIsUploadedToItsOwnServerWhenTheOtherEndpointWasUsedFirst() {
        startCase("direct:b");
        assertOwnRequestsOnly(serverB, "B", 1);
        assertRequests(serverA, 0, 0, 0, 0);

        startCase("direct:a");
        assertOwnRequestsOnly(serverA, "A", 1);
        assertOwnRequestsOnly(serverB, "B", 1);
    }

    @Test
    void uploadLeavesNoTemporaryFile() throws Exception {
        startCase("direct:a");

        assertUploadedPartAndNoTemporaryFileLeft(serverA);
    }

    @Test
    void uploadLeavesNoTemporaryFileWhenTheUploadFails() throws Exception {
        serverA.stubFor(post(urlEqualTo(UPLOAD)).atPriority(1)
                .willReturn(aResponse().withStatus(500).withHeader("Content-Type", "text/plain")
                        .withBody("upload refused")));

        Exchange exchange = template.send("direct:a", e -> e.getIn().setBody(inputsWithFile()));

        assertInstanceOf(InternalServerErrorException.class, exchange.getException(),
                "the exchange must fail with the answer of Bonita to the upload");
        assertRequests(serverA, 2, 1, 1, 0);
        assertUploadedPartAndNoTemporaryFileLeft(serverA);
    }

    private void startCase(String uri) {
        Exchange exchange = template.send(uri, e -> e.getIn().setBody(inputsWithFile()));
        assertNull(exchange.getException(), "exception of the exchange sent to " + uri);
    }

    private static Map<String, Serializable> inputsWithFile() {
        Map<String, Serializable> inputs = new HashMap<>();
        inputs.put("amount", 1);
        inputs.put("document", new FileInput("report.txt", "content".getBytes(StandardCharsets.UTF_8)));
        return inputs;
    }

    private static String bonita(WireMockServer server, String id) {
        return "bonita:startCase?hostname=localhost&port=" + server.port() + "&processName=orders&username=user" + id
               + "&password=pass" + id;
    }

    /**
     * A Bonita server that knows the process "orders" and names its session, its API token and the path of an uploaded
     * file after itself.
     */
    private static WireMockServer startBonita(String id) {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        server.stubFor(post(urlEqualTo(LOGIN))
                .willReturn(aResponse().withHeader("Set-Cookie", "JSESSIONID=session-" + id,
                        "X-Bonita-API-Token=token-" + id)));
        server.stubFor(get(urlPathEqualTo(PROCESS)).withQueryParam("s", equalTo("orders"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"7\",\"name\":\"orders\",\"version\":\"1.0\"}]")));
        server.stubFor(post(urlEqualTo(UPLOAD))
                .willReturn(aResponse().withHeader("Content-Type", "text/plain;charset=UTF-8")
                        .withBody("{\"filename\":\"report.txt\",\"tempPath\":\"tmp_" + id
                                  + ".doc\",\"contentType\":\"text/plain\"}")));
        server.stubFor(post(urlEqualTo(INSTANTIATION))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"caseId\":\"1\"}")));
        return server;
    }

    /**
     * The server got exactly the requests of the given number of its own exchanges (per exchange: process lookup, file
     * upload and case creation, each after its own login), every one of them with the account and the session of this
     * server, and every case refers to a file that this server stored.
     */
    private static void assertOwnRequestsOnly(WireMockServer server, String id, int exchanges) {
        assertRequests(server, 3 * exchanges, exchanges, exchanges, exchanges);
        for (LoggedRequest request : server.findAll(anyRequestedFor(anyUrl()))) {
            String path = request.getUrl();
            if (LOGIN.equals(path)) {
                String form = request.getBodyAsString();
                assertTrue(form.contains("username=user" + id) && form.contains("password=pass" + id),
                        "login at Bonita server " + id + " with the account of another endpoint: " + form);
            } else {
                assertEquals("token-" + id, request.getHeader("X-Bonita-API-Token"),
                        "API token of " + path + " at Bonita server " + id);
                String cookies = String.join(",", request.header("Cookie").values());
                assertTrue(cookies.contains("JSESSIONID=session-" + id),
                        "session of " + path + " at Bonita server " + id + ": " + cookies);
            }
            if (INSTANTIATION.equals(path)) {
                assertTrue(request.getBodyAsString().contains("tmp_" + id + ".doc"),
                        "the case started at Bonita server " + id + " must refer to the file uploaded to this server: "
                                                                                     + request.getBodyAsString());
            }
        }
    }

    private static void assertRequests(WireMockServer server, int logins, int lookups, int uploads, int cases) {
        List<LoggedRequest> requests = server.findAll(anyRequestedFor(anyUrl()));
        String received = "requests received by the Bonita server on port " + server.port();
        assertEquals(uploads, count(requests, UPLOAD), "file uploads among the " + received);
        assertEquals(cases, count(requests, INSTANTIATION), "case creations among the " + received);
        assertEquals(lookups, count(requests, PROCESS + "?s=orders"), "process lookups among the " + received);
        assertEquals(logins, count(requests, LOGIN), "logins among the " + received);
        assertEquals(logins + lookups + uploads + cases, requests.size(), received);
    }

    private static long count(List<LoggedRequest> requests, String url) {
        return requests.stream().filter(r -> url.equals(r.getUrl())).count();
    }

    /**
     * The upload is one multipart part with the content of the file input, named like a temporary file
     * ({@code tempFile<digits>.tmp}, as on main). No file of that name may be left in the temporary directory.
     */
    private static void assertUploadedPartAndNoTemporaryFileLeft(WireMockServer server) throws Exception {
        List<LoggedRequest> uploads = server.findAll(anyRequestedFor(urlEqualTo(UPLOAD)));
        assertEquals(1, uploads.size(), "file uploads received");
        LoggedRequest upload = uploads.get(0);
        String contentType = upload.getHeader("Content-Type");
        assertTrue(contentType.startsWith("multipart/form-data"), "content type of the upload: " + contentType);
        String body = upload.getBodyAsString();
        Matcher matcher = UPLOADED_PART.matcher(body);
        assertTrue(matcher.matches(), "the upload must be one part named like a temporary file: " + body);
        assertEquals(matcher.group(1), matcher.group(4), "closing boundary of the upload: " + body);
        assertEquals("content", matcher.group(3), "content of the uploaded part");
        Path file = Path.of(System.getProperty("java.io.tmpdir"), matcher.group(2));

        // deleteIfExists also cleans up when the assertion fails
        assertFalse(Files.deleteIfExists(file), "the temporary file of the upload was left behind: " + file);
    }
}
