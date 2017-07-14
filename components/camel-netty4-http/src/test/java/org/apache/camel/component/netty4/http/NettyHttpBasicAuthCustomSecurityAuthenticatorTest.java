/**
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
package org.apache.camel.component.netty4.http;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

public class NettyHttpBasicAuthCustomSecurityAuthenticatorTest extends BaseNettyTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("myAuthenticator", new MyAuthenticator());
        return jndi;
    }

    @Test
    public void testBasicAuth() throws Exception {
        try {
            template.requestBody("netty4-http:http://localhost:{{port}}/foo", "Hello World", String.class);
            fail("Should send back 401");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
            assertEquals(401, cause.getStatusCode());
        }

        // wait a little bit before next as the connection was closed when denied
        Thread.sleep(500);

        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        // username:password is scott:secret
        String auth = "Basic c2NvdHQ6c2VjcmV0";
        String out = template.requestBodyAndHeader("netty4-http:http://localhost:{{port}}/foo", "Hello World", "Authorization", auth, String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty4-http:http://0.0.0.0:{{port}}/foo?securityConfiguration.realm=foo&securityConfiguration.securityAuthenticator=#myAuthenticator")
                    .to("mock:input")
                    .transform().constant("Bye World");
            }
        };
    }

    private final class MyAuthenticator implements SecurityAuthenticator {

        public void setName(String name) {
            // noop
        }

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
        public void logout(Subject subject) throws LoginException {
            // noop
        }

        @Override
        public String getUserRoles(Subject subject) {
            return null;
        }
    }

}
