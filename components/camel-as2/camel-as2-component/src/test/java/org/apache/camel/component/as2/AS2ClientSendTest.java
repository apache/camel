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
package org.apache.camel.component.as2;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body types client/send accepts, the default of requestUri, the deprecated ediMessageType option, the
 * quoted-printable transfer encoding and a partner that answers with an HTTP error (CAMEL-26052).
 */
public class AS2ClientSendTest extends AbstractAS2ITSupport {

    private static final String EDI_MESSAGE
            = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'UNZ+1+00000000000778'";

    @TempDir
    static Path inbox;

    private final List<String> received = new CopyOnWriteArrayList<>();
    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();
    private AS2ServerConnection serverConnection;
    private int targetPort;

    @Override
    public void setupResources() throws Exception {
        serverConnection = new AS2ServerConnection(
                "1.1", "AS2ClientSendTest Server", "server.example.com", 0,
                AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, "TBD", null, null,
                null, null, null, "localhost");
        targetPort = serverConnection.getLocalPort();
        serverConnection.listen("/fail", (request, response, context) -> response.setCode(HttpStatus.SC_SERVER_ERROR));
        serverConnection.listen("*", (request, response, context) -> {
            context.setAttribute(AS2ServerManager.FROM, "mrAS@example.org");
            context.setAttribute(AS2ServerManager.SUBJECT, "MDN Response");
            ApplicationEntity entity = HttpMessageUtils.extractEdiPayload(request,
                    new HttpMessageUtils.DecrpytingAndSigningInfo(null, null));
            receivedPaths.add(request.getPath());
            received.add(asText(entity.getEdiMessage()));
        });
    }

    private String asText(Object ediMessage) {
        return context.getTypeConverter().convertTo(String.class, ediMessage);
    }

    @Override
    public void cleanupResources() {
        if (serverConnection != null) {
            serverConnection.close();
        }
    }

    @BeforeEach
    public void clearReceived() {
        received.clear();
        receivedPaths.clear();
    }

    @Test
    public void sendText() {
        Object body = requestBodyAndHeaders("direct://SEND", EDI_MESSAGE, as2Headers());

        assertNull(body);
        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void sendBytes() {
        requestBodyAndHeaders("direct://SEND", EDI_MESSAGE.getBytes(StandardCharsets.US_ASCII), as2Headers());

        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void sendFile() throws Exception {
        File file = Files.writeString(inbox.resolve("message.edi"), EDI_MESSAGE).toFile();

        requestBodyAndHeaders("direct://SEND", file, as2Headers());

        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void sendTheFilesOfAFolder() throws Exception {
        MockEndpoint sent = getMockEndpoint("mock:sent");
        sent.expectedMessageCount(1);

        Files.writeString(inbox.resolve("order.edi"), EDI_MESSAGE);
        context.getRouteController().startRoute("files");

        sent.assertIsSatisfied();
        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void sendABodyThatDoesNotConvert() {
        Map<String, Object> notAnEdiMessage = Map.of("order", "1");

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> requestBodyAndHeaders("direct://SEND", notAnEdiMessage, as2Headers()));

        assertTrue(e.getCause().getMessage().contains(notAnEdiMessage.getClass().getName()), e.getCause().getMessage());
        assertTrue(received.isEmpty());
    }

    @Test
    public void requestUriDefaultsToSlash() {
        Map<String, Object> headers = as2Headers();
        headers.remove("CamelAs2.requestUri");

        // an as2 component without a component configuration, as with camel run
        requestBodyAndHeaders("direct://SEND_WITHOUT_COMPONENT_CONFIGURATION", EDI_MESSAGE, headers);

        assertEquals(List.of("/"), receivedPaths);
        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void ediMessageTypeIsUsedAsContentType() {
        Map<String, Object> headers = as2Headers();
        headers.remove("CamelAs2.ediMessageContentType");

        requestBodyAndHeaders("direct://SEND_EDI_MESSAGE_TYPE", EDI_MESSAGE, headers);

        assertEquals(List.of(EDI_MESSAGE), received);
    }

    @Test
    public void sendQuotedPrintable() {
        // a long line, an equal sign and characters outside US-ASCII all need encoding
        String message = EDI_MESSAGE + EDI_MESSAGE + "FTX+AAI+++Prix = 12 €, Grüße'";
        Map<String, Object> headers = as2Headers();
        headers.put("CamelAs2.ediMessageTransferEncoding", "quoted-printable");
        headers.put("CamelAs2.ediMessageCharset", "utf-8");

        requestBodyAndHeaders("direct://SEND", message, headers);

        assertEquals(List.of(message), received);
    }

    @Test
    public void partnerErrorFailsTheExchange() {
        Map<String, Object> headers = as2Headers();
        headers.put("CamelAs2.requestUri", "/fail");

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> requestBodyAndHeaders("direct://SEND", EDI_MESSAGE, headers));

        assertTrue(e.getCause().getMessage().contains("500"), e.getCause().getMessage());
        // the interchange is still there to look at
        assertNotNull(e.getExchange().getProperty(AS2Constants.AS2_INTERCHANGE));
    }

    private Map<String, Object> as2Headers() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelAs2.requestUri", "/");
        headers.put("CamelAs2.subject", "Test Case");
        headers.put("CamelAs2.from", "mrAS@example.org");
        headers.put("CamelAs2.as2From", "878051556");
        headers.put("CamelAs2.as2To", "878051556");
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.PLAIN);
        headers.put("CamelAs2.ediMessageContentType", AS2MediaType.APPLICATION_EDIFACT);
        headers.put("CamelAs2.ediMessageTransferEncoding", "7bit");
        return headers;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("as2-plain", new AS2Component(camelContext));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://SEND")
                        .to("as2://client/send?inBody=ediMessage");

                from("direct://SEND_WITHOUT_COMPONENT_CONFIGURATION")
                        .to("as2-plain://client/send?inBody=ediMessage&targetHostname=localhost&targetPortNumber="
                            + targetPort);

                from("direct://SEND_EDI_MESSAGE_TYPE")
                        .to("as2://client/send?inBody=ediMessage&ediMessageType=application/edifact");

                from("file:" + inbox + "?include=.*\\.edi&noop=true&initialDelay=0&delay=10").routeId("files")
                        .autoStartup(false)
                        .setHeader("CamelAs2.requestUri", constant("/"))
                        .setHeader("CamelAs2.subject", constant("Test Case"))
                        .setHeader("CamelAs2.from", constant("mrAS@example.org"))
                        .setHeader("CamelAs2.as2From", constant("878051556"))
                        .setHeader("CamelAs2.as2To", constant("878051556"))
                        .setHeader("CamelAs2.as2MessageStructure", constant(AS2MessageStructure.PLAIN))
                        .setHeader("CamelAs2.ediMessageContentType", constant(AS2MediaType.APPLICATION_EDIFACT))
                        .to("as2://client/send?inBody=ediMessage")
                        .to("mock:sent");
            }
        };
    }

    @Override
    protected void customizeConfiguration(AS2Configuration configuration) {
        configuration.setTargetPortNumber(targetPort);
    }
}
