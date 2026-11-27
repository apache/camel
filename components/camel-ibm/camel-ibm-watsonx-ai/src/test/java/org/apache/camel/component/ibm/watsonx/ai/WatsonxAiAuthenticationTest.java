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
package org.apache.camel.component.ibm.watsonx.ai;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.watsonx.ai.core.auth.ibmcloud.IBMCloudAuthenticator;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.service.OAuthProfileAuthenticator;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the endpoint authenticates: the access token of an OAuth profile is sent as a Bearer token and obtained again for
 * every request, so a renewed token is used; a custom SDK authenticator is used as it is; an API key is exchanged at
 * IBM Cloud IAM.
 */
class WatsonxAiAuthenticationTest extends CamelTestSupport {

    private static final String CHAT_RESPONSE = """
            {"id":"chat-1","object":"chat.completion","model_id":"model","model":"model","created":1,
             "created_at":"2026-01-01T00:00:00.000Z",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Paris"},"finish_reason":"stop"}],
             "usage":{"completion_tokens":1,"prompt_tokens":2,"total_tokens":3}}""";

    private static FakeWatsonxServer server;
    private static final AtomicInteger TOKEN = new AtomicInteger();

    @BeforeAll
    static void startServer() throws IOException {
        server = FakeWatsonxServer.start();
        // the route starts before each test, and asks the token of its profile at startup
        TestOAuthClientAuthenticationFactory.tokens = WatsonxAiAuthenticationTest::token;
    }

    private static String token(String profile) {
        return "access-token-" + profile + "-" + TOKEN.get();
    }

    @AfterAll
    static void stopServer() {
        server.close();
        TestOAuthClientAuthenticationFactory.reset();
    }

    @BeforeEach
    void resetServer() {
        server.reset();
        server.respond("/ml/v1/text/chat", CHAT_RESPONSE);
        TestOAuthClientAuthenticationFactory.PROFILES.clear();
        TOKEN.set(1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:oauth")
                        .to("ibm-watsonx-ai:oauth?oauthProfile=myidp&baseUrl=" + server.baseUrl()
                            + "&projectId=my-project&modelId=model&operation=chat");
            }
        };
    }

    @Test
    void oauthProfileTokenIsSentAsBearerTokenAndRenewed() {
        Exchange first = template.send("direct:oauth", e -> e.getIn().setBody("What is the capital of France?"));
        assertNull(first.getException());
        // the identity provider renews the token: the next request must use the new one
        TOKEN.set(2);
        Exchange second = template.send("direct:oauth", e -> e.getIn().setBody("And of Italy?"));
        assertNull(second.getException());

        List<FakeWatsonxServer.Request> requests = server.requests();
        // only watsonx.ai is called: the token is never exchanged at IBM Cloud IAM as an API key
        assertEquals(List.of("/ml/v1/text/chat", "/ml/v1/text/chat"),
                requests.stream().map(FakeWatsonxServer.Request::path).toList());
        assertEquals("Bearer access-token-myidp-1", requests.get(0).authorization());
        assertEquals("Bearer access-token-myidp-2", requests.get(1).authorization());
        assertEquals("myidp", TestOAuthClientAuthenticationFactory.PROFILES.get(0));
    }

    @Test
    void oauthProfileGivesABearerAuthenticatorThatAsksATokenAtStartup() throws Exception {
        WatsonxAiEndpoint endpoint = context.getEndpoint(
                "ibm-watsonx-ai:start?oauthProfile=other&baseUrl=" + server.baseUrl() + "&operation=chat",
                WatsonxAiEndpoint.class);
        endpoint.start();
        try {
            OAuthProfileAuthenticator authenticator
                    = assertInstanceOf(OAuthProfileAuthenticator.class, endpoint.getAuthenticator());
            assertEquals("Bearer", authenticator.scheme());
            assertEquals("access-token-other-1", authenticator.tokenAsync().get());
            // the token is asked once at startup, so a misconfigured profile fails the route at startup
            assertEquals(List.of("other", "other"), TestOAuthClientAuthenticationFactory.PROFILES);
        } finally {
            endpoint.stop();
        }
    }

    @Test
    void oauthProfileWithoutTokenFailsAtStartup() {
        TestOAuthClientAuthenticationFactory.tokens = profile -> {
            throw new IllegalStateException("unknown profile " + profile);
        };
        try {
            // the endpoint starts when it is resolved
            Exception e = assertThrows(Exception.class, () -> context.getEndpoint(
                    "ibm-watsonx-ai:fail?oauthProfile=missing&baseUrl=" + server.baseUrl() + "&operation=chat"));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("unknown profile missing", e.getCause().getMessage());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens = WatsonxAiAuthenticationTest::token;
        }
    }

    @Test
    void oauthProfileWithEmptyTokenFailsAtStartup() {
        TestOAuthClientAuthenticationFactory.tokens = profile -> "";
        try {
            Exception e = assertThrows(Exception.class, () -> context.getEndpoint(
                    "ibm-watsonx-ai:empty?oauthProfile=empty&baseUrl=" + server.baseUrl() + "&operation=chat"));
            assertInstanceOf(IllegalStateException.class, e.getCause());
            assertEquals("The OAuth profile empty gave no access token", e.getCause().getMessage());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens = WatsonxAiAuthenticationTest::token;
        }
    }

    @Test
    void processToolCallsAsksNoTokenAtStartup() throws Exception {
        // processToolCalls runs the tools locally: an oauthProfile it inherits, from the component for example, must not
        // contact the identity provider
        TestOAuthClientAuthenticationFactory.tokens = profile -> {
            throw new IllegalStateException("the identity provider was contacted for " + profile);
        };
        try {
            WatsonxAiEndpoint endpoint = context.getEndpoint(
                    "ibm-watsonx-ai:tools?oauthProfile=other&operation=processToolCalls", WatsonxAiEndpoint.class);
            assertTrue(endpoint.isStarted());
            assertNull(endpoint.getAuthenticator());
            assertTrue(TestOAuthClientAuthenticationFactory.PROFILES.isEmpty());
        } finally {
            TestOAuthClientAuthenticationFactory.tokens = WatsonxAiAuthenticationTest::token;
        }
    }

    @Test
    void processToolCallsEndpointAuthenticatesTheOperationOfAHeader() {
        context.getRegistry().bind("testAuthenticator", FakeWatsonxServer.TEST_AUTHENTICATOR);
        Exchange result = template.send(
                "ibm-watsonx-ai:switch?authenticator=#testAuthenticator&baseUrl=" + server.baseUrl()
                                        + "&projectId=my-project&modelId=model&operation=processToolCalls",
                e -> {
                    e.getIn().setBody("What is the capital of France?");
                    e.getIn().setHeader(WatsonxAiConstants.OPERATION, "chat");
                });

        assertNull(result.getException());
        assertEquals("Paris", result.getMessage().getBody());
        assertEquals("Bearer test-token", server.lastRequest("/ml/v1/text/chat").authorization());
    }

    @Test
    void customAuthenticatorIsUsedAsItIs() throws Exception {
        context.getRegistry().bind("testAuthenticator", FakeWatsonxServer.TEST_AUTHENTICATOR);
        Exchange result = template.send(
                "ibm-watsonx-ai:custom?authenticator=#testAuthenticator&baseUrl=" + server.baseUrl()
                                        + "&projectId=my-project&modelId=model&operation=chat",
                e -> e.getIn().setBody("What is the capital of France?"));
        assertNull(result.getException());
        assertEquals("Paris", result.getMessage().getBody());
        assertEquals("Bearer test-token", server.lastRequest("/ml/v1/text/chat").authorization());

        WatsonxAiEndpoint endpoint = context.getEndpoint(
                "ibm-watsonx-ai:custom?authenticator=#testAuthenticator&baseUrl=" + server.baseUrl()
                                                         + "&projectId=my-project&modelId=model&operation=chat",
                WatsonxAiEndpoint.class);
        assertSame(FakeWatsonxServer.TEST_AUTHENTICATOR, endpoint.getAuthenticator());
    }

    @Test
    void apiKeyUsesTheIbmCloudIamAuthenticator() throws Exception {
        WatsonxAiEndpoint endpoint = context.getEndpoint(
                "ibm-watsonx-ai:iam?apiKey=my-key&baseUrl=" + server.baseUrl() + "&operation=chat",
                WatsonxAiEndpoint.class);
        endpoint.start();
        try {
            IBMCloudAuthenticator authenticator
                    = assertInstanceOf(IBMCloudAuthenticator.class, endpoint.getAuthenticator());
            assertEquals("Bearer", authenticator.scheme());
        } finally {
            endpoint.stop();
        }
    }
}
