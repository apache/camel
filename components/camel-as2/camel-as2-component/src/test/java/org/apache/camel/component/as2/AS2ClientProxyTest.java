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

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.spi.Registry;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AS2 client sends through an HTTP proxy: plain http in absolute form, https through a CONNECT tunnel.
 */
public class AS2ClientProxyTest extends AbstractAS2ITSupport {

    private static final String PROXY_USER = "proxyUser";
    private static final String PROXY_PASSWORD = "proxyPassword";
    private static final String PROXY_AUTHORIZATION = "Basic " + Base64.getEncoder()
            .encodeToString((PROXY_USER + ":" + PROXY_PASSWORD).getBytes(StandardCharsets.UTF_8));
    // a name the client cannot resolve (RFC 2606): only the proxy knows where it is
    private static final String PARTNER_BEHIND_PROXY = "as2-partner.invalid";
    private static final String EDI_MESSAGE = """
            UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'
            UNH+00000000000117+INVOIC:D:97B:UN'
            BGM+380+342459+9'
            DTM+3:20060515:102'
            UNT+23+00000000000117'
            UNZ+1+00000000000778'
            """;

    private SSLContext sslContext;
    private TestHttpProxy proxy;
    private AS2ServerConnection httpServer;
    private AS2ServerConnection httpsServer;
    private CountingRequestHandler httpRequestHandler;
    private CountingRequestHandler httpsRequestHandler;

    @Override
    public void setupResources() throws Exception {
        sslContext = createSslContext();
        proxy = new TestHttpProxy(PROXY_AUTHORIZATION);
        proxy.addHostAlias(PARTNER_BEHIND_PROXY, "localhost");

        httpServer = new AS2ServerConnection(
                "1.1", "AS2ClientProxyTest Server", "server.example.com", 0, AS2SignatureAlgorithm.SHA256WITHRSA,
                null, null, null, "TBD", null, null, null, null, null);
        httpRequestHandler = new CountingRequestHandler();
        httpServer.listen("/", httpRequestHandler);

        httpsServer = new AS2ServerConnection(
                "1.1", "AS2ClientProxyTest Server", "server.example.com", 0, AS2SignatureAlgorithm.SHA256WITHRSA,
                null, null, null, "TBD", null, sslContext, null, null, null);
        httpsRequestHandler = new CountingRequestHandler();
        httpsServer.listen("/", httpsRequestHandler);
    }

    @Override
    public void cleanupResources() {
        if (httpServer != null) {
            httpServer.close();
        }
        if (httpsServer != null) {
            httpsServer.close();
        }
        if (proxy != null) {
            proxy.close();
        }
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("tlsContext", sslContext);
    }

    @Test
    public void sendsHttpThroughProxy() {
        HttpEntity mdn = requestBodyAndHeaders("direct://HTTP", EDI_MESSAGE, as2Headers());

        assertInstanceOf(DispositionNotificationMultipartReportEntity.class, mdn);
        assertEquals(List.of("POST http://localhost:" + httpServer.getLocalPort() + "/ HTTP/1.1"),
                proxy.getRequestLines());
        assertEquals(List.of(PROXY_AUTHORIZATION), proxy.getProxyAuthorizations());
        assertReceived(httpRequestHandler);
    }

    @Test
    public void reachesPartnerOnlyResolvableByTheProxy() {
        HttpEntity mdn = requestBodyAndHeaders("direct://HTTP_PARTNER_BEHIND_PROXY", EDI_MESSAGE, as2Headers());

        assertInstanceOf(DispositionNotificationMultipartReportEntity.class, mdn);
        assertEquals(List.of("POST http://" + PARTNER_BEHIND_PROXY + ":" + httpServer.getLocalPort() + "/ HTTP/1.1"),
                proxy.getRequestLines());
        assertReceived(httpRequestHandler);
    }

    @Test
    public void refusedHttpRequestFailsTheSend() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> requestBodyAndHeaders("direct://HTTP_WRONG_PASSWORD", EDI_MESSAGE, as2Headers()));

        assertTrue(rootMessage(e).contains("407"), rootMessage(e));
        assertEquals(0, httpRequestHandler.getCount());
    }

    @Test
    public void refusedHttpConnectionIsNotReused() {
        assertEachSendIsRefused("direct://HTTP_WRONG_PASSWORD", httpRequestLine());
        assertEquals(0, httpRequestHandler.getCount());
    }

    @Test
    public void refusedHttpConnectionIsNotReusedWhenTheProxyKeepsItOpen() {
        // the 407 then has a body and the proxy waits for a retry on the same connection: it must not be reused
        proxy.setKeepAliveAfterRefusal(true);

        assertEachSendIsRefused("direct://HTTP_WRONG_PASSWORD", httpRequestLine());
        assertEquals(0, httpRequestHandler.getCount());
    }

    @Test
    public void sendsHttpsThroughProxyTunnel() {
        HttpEntity mdn = requestBodyAndHeaders("direct://HTTPS", EDI_MESSAGE, as2Headers());

        assertInstanceOf(DispositionNotificationMultipartReportEntity.class, mdn);
        assertEquals(List.of(httpsConnectLine()), proxy.getRequestLines());
        assertEquals(List.of(PROXY_AUTHORIZATION), proxy.getProxyAuthorizations());
        HttpRequest request = assertReceived(httpsRequestHandler);
        // the proxy credentials are for the proxy only: they must not travel through the tunnel to the partner
        assertNull(request.getFirstHeader("Proxy-Authorization"));
    }

    @Test
    public void sendsSeveralMessagesThroughTheTunnel() {
        for (int i = 0; i < 3; i++) {
            HttpEntity mdn = requestBodyAndHeaders("direct://HTTPS", EDI_MESSAGE, as2Headers());
            assertInstanceOf(DispositionNotificationMultipartReportEntity.class, mdn);
        }

        assertEquals(3, httpsRequestHandler.getCount());
        // a connection kept alive by the pool is already tunnelled: it must not be asked to CONNECT again
        assertTrue(proxy.getRequestLines().stream().allMatch(httpsConnectLine()::equals), proxy.getRequestLines()::toString);
        assertTrue(proxy.getRequestLines().size() <= 3, proxy.getRequestLines()::toString);
    }

    @Test
    public void sendsConcurrentlyThroughTheTunnel() throws Exception {
        int messages = 16;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> mdns = new ArrayList<>();
            for (int i = 0; i < messages; i++) {
                mdns.add(executor.submit(() -> requestBodyAndHeaders("direct://HTTPS", EDI_MESSAGE, as2Headers())));
            }
            for (Future<Object> mdn : mdns) {
                assertInstanceOf(DispositionNotificationMultipartReportEntity.class, mdn.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(messages, httpsRequestHandler.getCount());
        assertTrue(proxy.getRequestLines().stream().allMatch(httpsConnectLine()::equals), proxy.getRequestLines()::toString);
    }

    @Test
    public void refusedTunnelFailsTheSend() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> requestBodyAndHeaders("direct://HTTPS_WRONG_PASSWORD", EDI_MESSAGE, as2Headers()));

        assertTrue(rootMessage(e).contains("407"), rootMessage(e));
        assertEquals(0, httpsRequestHandler.getCount());
    }

    @Test
    public void refusedTunnelIsNotReused() {
        // a connection without its tunnel must not go back to the pool, or the next send skips CONNECT and TLS
        assertEachSendIsRefused("direct://HTTPS_WRONG_PASSWORD", httpsConnectLine());
        assertEquals(0, httpsRequestHandler.getCount());
    }

    @Test
    public void refusedTunnelIsNotReusedWhenTheProxyKeepsItOpen() {
        proxy.setKeepAliveAfterRefusal(true);

        assertEachSendIsRefused("direct://HTTPS_WRONG_PASSWORD", httpsConnectLine());
        assertEquals(0, httpsRequestHandler.getCount());
    }

    @Test
    public void verifiesTheTargetHostNameThroughTheTunnel() {
        // the certificate names localhost, which is also the proxy's name: the handshake must check the target's
        for (int i = 0; i < 2; i++) {
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> requestBodyAndHeaders("direct://HTTPS_IP_TARGET", EDI_MESSAGE, as2Headers()));
            assertTrue(hasCause(e, SSLException.class), rootMessage(e));
        }

        // a failed handshake leaves no connection in the pool: the second send opens a new tunnel
        String connectLine = "CONNECT 127.0.0.1:" + httpsServer.getLocalPort() + " HTTP/1.1";
        assertEquals(List.of(connectLine, connectLine), proxy.getRequestLines());
        assertEquals(0, httpsRequestHandler.getCount());
    }

    @Test
    public void unreachableProxyFailsAtStartup() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertThrows(ConnectException.class, () -> createClientConnection("localhost", closedPort));
    }

    @Test
    public void proxyPortIsRequired() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> createClientConnection("localhost", null));

        assertTrue(e.getMessage().contains("proxyPort"), e.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://HTTP")
                        .toF("as2://client/send?inBody=ediMessage&targetPortNumber=%d&proxyHost=localhost&proxyPort=%d"
                             + "&proxyAuthUsername=%s&proxyAuthPassword=%s",
                                httpServer.getLocalPort(), proxy.getPort(), PROXY_USER, PROXY_PASSWORD);

                from("direct://HTTP_PARTNER_BEHIND_PROXY")
                        .toF("as2://client/send?inBody=ediMessage&targetHostname=%s&targetPortNumber=%d"
                             + "&proxyHost=localhost&proxyPort=%d&proxyAuthUsername=%s&proxyAuthPassword=%s",
                                PARTNER_BEHIND_PROXY, httpServer.getLocalPort(), proxy.getPort(), PROXY_USER,
                                PROXY_PASSWORD);

                from("direct://HTTP_WRONG_PASSWORD")
                        .toF("as2://client/send?inBody=ediMessage&targetPortNumber=%d&proxyHost=localhost&proxyPort=%d"
                             + "&proxyAuthUsername=%s&proxyAuthPassword=wrong",
                                httpServer.getLocalPort(), proxy.getPort(), PROXY_USER);

                from("direct://HTTPS")
                        .toF("as2://client/send?inBody=ediMessage&targetPortNumber=%d&sslContext=#tlsContext"
                             + "&proxyHost=localhost&proxyPort=%d&proxyAuthUsername=%s&proxyAuthPassword=%s",
                                httpsServer.getLocalPort(), proxy.getPort(), PROXY_USER, PROXY_PASSWORD);

                from("direct://HTTPS_WRONG_PASSWORD")
                        .toF("as2://client/send?inBody=ediMessage&targetPortNumber=%d&sslContext=#tlsContext"
                             + "&proxyHost=localhost&proxyPort=%d&proxyAuthUsername=%s&proxyAuthPassword=wrong",
                                httpsServer.getLocalPort(), proxy.getPort(), PROXY_USER);

                from("direct://HTTPS_IP_TARGET")
                        .toF("as2://client/send?inBody=ediMessage&targetHostname=127.0.0.1&targetPortNumber=%d"
                             + "&sslContext=#tlsContext&proxyHost=localhost&proxyPort=%d"
                             + "&proxyAuthUsername=%s&proxyAuthPassword=%s",
                                httpsServer.getLocalPort(), proxy.getPort(), PROXY_USER, PROXY_PASSWORD);
            }
        };
    }

    /**
     * Sends twice through a route the proxy refuses: each send must get the 407 on a new connection to the proxy.
     */
    private void assertEachSendIsRefused(String route, String requestLine) {
        for (int i = 0; i < 2; i++) {
            CamelExecutionException e = assertThrows(CamelExecutionException.class,
                    () -> requestBodyAndHeaders(route, EDI_MESSAGE, as2Headers()));
            assertTrue(rootMessage(e).contains("407"), rootMessage(e));
        }

        assertEquals(List.of(requestLine, requestLine), proxy.getRequestLines());
        assertEquals(2, proxy.getConnectionCount());
    }

    private String httpRequestLine() {
        return "POST http://localhost:" + httpServer.getLocalPort() + "/ HTTP/1.1";
    }

    private String httpsConnectLine() {
        return "CONNECT localhost:" + httpsServer.getLocalPort() + " HTTP/1.1";
    }

    private AS2ClientConnection createClientConnection(String proxyHost, Integer proxyPort) throws IOException {
        return new AS2ClientConnection(
                "1.1", "Camel AS2 Endpoint", "example.org", "localhost", httpServer.getLocalPort(), Duration.ofSeconds(5),
                Duration.ofSeconds(5), 5, Duration.ofMinutes(15), null, null, false, proxyHost, proxyPort, null, null);
    }

    private static HttpRequest assertReceived(CountingRequestHandler requestHandler) {
        assertEquals(1, requestHandler.getCount());
        HttpRequest request = requestHandler.getRequest();
        assertNotNull(request);
        HttpEntity entity = ((ClassicHttpRequest) request).getEntity();
        assertInstanceOf(ApplicationEntity.class, entity);
        assertEquals(EDI_MESSAGE, ((String) ((ApplicationEntity) entity).getEdiMessage()).replaceAll("\r", ""));
        return request;
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    private static Map<String, Object> as2Headers() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelAs2.requestUri", "/");
        headers.put("CamelAs2.subject", "Test Case");
        headers.put("CamelAs2.from", "mrAS@example.org");
        headers.put("CamelAs2.as2From", "878051556");
        headers.put("CamelAs2.as2To", "878051556");
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.PLAIN);
        headers.put("CamelAs2.ediMessageContentType", AS2MediaType.APPLICATION_EDIFACT);
        headers.put("CamelAs2.ediMessageTransferEncoding", "7bit");
        headers.put("CamelAs2.dispositionNotificationTo", "mrAS2@example.com");
        headers.put("CamelAs2.attachedFileName", "");
        return headers;
    }

    private static SSLContext createSslContext() throws Exception {
        char[] password = "changeit".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = AS2ClientProxyTest.class.getResourceAsStream("/jsse/localhost.p12")) {
            keyStore.load(is, password);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }

    private static final class CountingRequestHandler extends AS2ClientManagerIT.RequestHandler {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void handle(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
                throws HttpException, IOException {
            count.incrementAndGet();
            super.handle(request, response, context);
        }

        int getCount() {
            return count.get();
        }
    }
}
