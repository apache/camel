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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer with protocol=https: the login, the process lookup and the case creation go to a server that only
 * accepts HTTPS, with the SSL context of the sslContextParameters option (or the global one of the component). The
 * certificate of the server (jsse/localhost.p12, for DNS:localhost) is not trusted by the JVM, so without the SSL
 * context of the endpoint the first request (the login) fails.
 */
class BonitaHttpsTest {

    private static final String KEYSTORE = "jsse/localhost.p12";
    private static final String LOGIN = "/bonita/loginservice";
    private static final String PROCESS = "/bonita/API/bpm/process";
    private static final String INSTANTIATION = "/bonita/API/bpm/process/7/instantiation";

    private WireMockServer server;
    private CamelContext context;

    @BeforeEach
    void setup() throws Exception {
        Path keystore = Path.of(BonitaHttpsTest.class.getClassLoader().getResource(KEYSTORE).toURI());
        server = new WireMockServer(
                WireMockConfiguration.options().httpDisabled(true).dynamicHttpsPort()
                        .keystorePath(keystore.toString()).keystoreType("PKCS12")
                        .keystorePassword("changeit").keyManagerPassword("changeit"));
        server.start();
        server.stubFor(post(urlEqualTo(LOGIN))
                .willReturn(aResponse().withHeader("Set-Cookie", "JSESSIONID=session", "X-Bonita-API-Token=token")));
        server.stubFor(get(urlPathEqualTo(PROCESS)).withQueryParam("s", equalTo("orders"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"7\",\"name\":\"orders\",\"version\":\"1.0\"}]")));
        server.stubFor(post(urlEqualTo(INSTANTIATION))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"caseId\":\"1\"}")));

        context = new DefaultCamelContext();
        context.getRegistry().bind("ssl", trustServer());
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void startCaseOverHttpsWithTheSslContextParametersOfTheEndpoint() throws Exception {
        startRoute("protocol=https&sslContextParameters=#ssl");

        Exchange exchange = startCase();

        assertNull(exchange.getException(), "exception of the exchange");
        assertEquals("1", exchange.getMessage().getHeader(BonitaConstants.CASE_ID));
        assertEquals(2, server.findAll(anyRequestedFor(urlEqualTo(LOGIN))).size(), "logins over https");
        assertEquals(1, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
    }

    @Test
    void protocolIsCaseInsensitive() throws Exception {
        startRoute("protocol=HTTPS&sslContextParameters=#ssl");

        Exchange exchange = startCase();

        assertNull(exchange.getException(), "exception of the exchange");
        assertEquals("1", exchange.getMessage().getHeader(BonitaConstants.CASE_ID));
    }

    @Test
    void startCaseOverHttpsWithTheGlobalSslContextParameters() throws Exception {
        context.setSSLContextParameters(trustServer());
        BonitaComponent component = new BonitaComponent();
        component.setUseGlobalSslContextParameters(true);
        context.addComponent("bonita", component);
        startRoute("protocol=https");

        Exchange exchange = startCase();

        assertNull(exchange.getException(), "exception of the exchange");
        assertEquals("1", exchange.getMessage().getHeader(BonitaConstants.CASE_ID));
    }

    @Test
    void loginFailsWithoutTheSslContextOfTheEndpoint() throws Exception {
        startRoute("protocol=https");

        Exchange exchange = startCase();

        assertNotNull(exchange.getException(), "the JVM does not trust the certificate of the server");
        assertTrue(hasCause(exchange.getException(), SSLException.class),
                "an SSLException in the causes of " + exchange.getException());
        assertEquals(0, server.findAll(anyRequestedFor(urlEqualTo(INSTANTIATION))).size(), "cases started");
    }

    @Test
    void otherProtocolFailsTheStart() throws Exception {
        context.addRoutes(route("protocol=ftp"));

        Exception e = assertThrows(FailedToStartRouteException.class, context::start);

        IllegalArgumentException cause = findCause(e, IllegalArgumentException.class);
        assertNotNull(cause, "an IllegalArgumentException in the causes of " + e);
        assertEquals("The protocol of the Bonita server must be http or https, but is: ftp", cause.getMessage());
    }

    private void startRoute(String options) throws Exception {
        context.addRoutes(route(options));
        context.start();
    }

    private RouteBuilder route(String options) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("bonita:startCase?hostname=localhost&port=" + server.httpsPort()
                            + "&processName=orders&username=user&password=pass&" + options);
            }
        };
    }

    private Exchange startCase() {
        ProducerTemplate template = context.createProducerTemplate();
        try {
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("amount", 1);
            return template.send("direct:start", e -> e.getIn().setBody(inputs));
        } finally {
            template.stop();
        }
    }

    private static SSLContextParameters trustServer() {
        KeyStoreParameters truststore = new KeyStoreParameters();
        truststore.setResource(KEYSTORE);
        truststore.setPassword("changeit");
        truststore.setType("PKCS12");
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(truststore);
        SSLContextParameters parameters = new SSLContextParameters();
        parameters.setTrustManagers(trustManagers);
        return parameters;
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        return findCause(e, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return assertInstanceOf(type, t);
            }
        }
        return null;
    }
}
