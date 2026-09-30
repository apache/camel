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
package org.apache.camel.oauth;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.oauth.OAuth.CAMEL_OAUTH_REDIRECT_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The post login url is emitted as the Location header of the post login redirect. Every origin the request offers -
 * the X-Forwarded-* headers, and the Host header behind CamelHttpUrl - is caller controlled, so the url is always built
 * from the origin of the configured redirect uri. Whatever the caller sends, the expected url below is therefore that
 * same origin plus the requested path: that is the property these tests pin.
 *
 * CAMEL-21899 is preserved by construction: the configured redirect uri is the address the identity provider sends the
 * browser back to, so behind an ingress or an OpenShift Route it is the externally reachable one.
 */
class OAuthCodeFlowPostLoginUrlTest {

    private static final String REDIRECT_URI = "https://app.example.com/auth";

    private DefaultCamelContext context;
    private Exchange exchange;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
        context.getPropertiesComponent().addInitialProperty(CAMEL_OAUTH_REDIRECT_URI, REDIRECT_URI);
        exchange = new DefaultExchange(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    private String postLoginUrl() {
        return new OAuthCodeFlowProcessor().getPostLoginUrl(exchange);
    }

    /**
     * CAMEL-21899: the browser is sent to the externally reachable url, not to the internally observed one.
     */
    @Test
    void theExternallyReachableUrlIsUsedBehindAProxy() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader(Exchange.HTTP_URI, "/hello");
        msg.setHeader(Exchange.HTTP_URL, "http://10.0.0.7:8080/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void aNonDefaultPortOfTheConfiguredRedirectUriIsKept() {
        context.getPropertiesComponent().addInitialProperty(CAMEL_OAUTH_REDIRECT_URI,
                "https://app.example.com:8443/auth");
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader("X-Forwarded-Port", 8443);
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com:8443/hello", postLoginUrl());
    }

    @Test
    void theDefaultPortIsNotAppendedToTheOrigin() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader("X-Forwarded-Port", 443);
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void aForwardedHostOnAnotherOriginIsConfinedToTheConfiguredOrigin() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "evil.example.net");
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    /**
     * A mismatched origin is only a diagnostic, so the warning fires at most once - a forged Host must not let anyone
     * flood the log. The later calls take the warn-once branch that drops to DEBUG, and every call still confines the
     * url to the configured origin, not just the first.
     */
    @Test
    void repeatedForeignOriginRequestsStayConfinedOnTheSameProcessor() {
        var processor = new OAuthCodeFlowProcessor();
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "evil.example.net");
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", processor.getPostLoginUrl(exchange));
        assertEquals("https://app.example.com/hello", processor.getPostLoginUrl(exchange));
        assertEquals("https://app.example.com/hello", processor.getPostLoginUrl(exchange));
    }

    @Test
    void aForwardedProtoOnAnotherSchemeIsConfinedToTheConfiguredOrigin() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "http");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void aForwardedPortOnAnotherPortIsConfinedToTheConfiguredOrigin() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader("X-Forwarded-Port", 9443);
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    /**
     * Chained proxies produce "host1, host2". Such a list must never be concatenated into the url.
     */
    @Test
    void aCommaSeparatedForwardedHostIsNotConcatenatedIntoTheUrl() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https, http");
        msg.setHeader("X-Forwarded-Host", "app.example.com, internal.example.net");
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void aCommaSeparatedForwardedHostStartingOnAnotherOriginIsConfined() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "evil.example.net, app.example.com");
        msg.setHeader(Exchange.HTTP_URI, "/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void withoutForwardedHeadersAMatchingRequestUrlIsKept() {
        var msg = exchange.getMessage();
        msg.setHeader(Exchange.HTTP_URL, "https://app.example.com/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void withoutForwardedHeadersARequestUrlOnAnotherOriginIsConfined() {
        var msg = exchange.getMessage();
        msg.setHeader(Exchange.HTTP_URL, "https://evil.example.net/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void withoutAnyRequestHeadersTheConfiguredOriginIsUsed() {
        assertEquals("https://app.example.com", postLoginUrl());
    }

    /**
     * A protocol relative request uri must not be able to move the redirect to another host.
     */
    @Test
    void aProtocolRelativeRequestUriCannotChangeTheOrigin() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader(Exchange.HTTP_URI, "//evil.example.net/hello");

        assertEquals("https://app.example.com/hello", postLoginUrl());
    }

    @Test
    void theQueryOfTheRequestUriIsKept() {
        var msg = exchange.getMessage();
        msg.setHeader("X-Forwarded-Proto", "https");
        msg.setHeader("X-Forwarded-Host", "app.example.com");
        msg.setHeader(Exchange.HTTP_URI, "/hello?greeting=hi");

        assertEquals("https://app.example.com/hello?greeting=hi", postLoginUrl());
    }

    @Test
    void anUnparsableRedirectUriIsRejected() {
        context.getPropertiesComponent().addInitialProperty(CAMEL_OAUTH_REDIRECT_URI, "not-a-url");

        assertThrows(IllegalStateException.class, this::postLoginUrl);
    }
}
