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
package org.apache.camel.component.netty.http;

import java.util.concurrent.TimeUnit;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class NettyHttpBasicAuthCustomSecurityAuthenticatorTest extends BaseNettyTest {

    @BindToRegistry("myAuthenticator")
    private MyAuthenticator auth = new MyAuthenticator();

    @Test
    public void testBasicAuth() throws Exception {
        try {
            template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class);
            fail("Should send back 401");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(401, cause.getStatusCode());
        }

        // wait a little bit before next as the connection was closed when
        // denied
        String auth = "Basic c2NvdHQ6c2VjcmV0";
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", "Hello World",
                            "Authorization",
                            auth, String.class);
                    assertEquals("Bye World", out);
                });
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?securityConfiguration.realm=foo&securityConfiguration.securityAuthenticator=#myAuthenticator")
                        .to("mock:input")
                        .transform().constant("Bye World");
            }
        };
    }

    private static final class MyAuthenticator implements SecurityAuthenticator {

        @Override
        public void setName(String name) {
            // noop
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public void setRoleClassNames(String names) {
            // noop
        }

        @Override
        public Subject login(HttpPrincipal principal) throws LoginException {
            if (!principal.getPassword().equalsIgnoreCase("secret")) {
                throw new LoginException("Login denied");
            }
            // login success so return a subject
            return new Subject();
        }

        @Override
        public void logout(Subject subject) {
            // noop
        }

        @Override
        public String getUserRoles(Subject subject) {
            return null;
        }
    }

}
