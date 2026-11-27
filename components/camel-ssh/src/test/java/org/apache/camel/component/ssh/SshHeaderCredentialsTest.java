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
package org.apache.camel.component.ssh;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user name and password that the producer logs in with, with and without the allowUsernameAndPasswordFromHeaders
 * option.
 */
public class SshHeaderCredentialsTest extends SshComponentTestSupport {

    /**
     * The passwords that the test server received, by user.
     */
    private final Map<String, String> passwords = new ConcurrentHashMap<>();

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        sshd.setPasswordAuthenticator((username, password, session) -> {
            authenticatedUsers.add(username);
            passwords.put(username, password);
            return true;
        });
    }

    @Test
    public void testPasswordHeadersIgnoredByDefault() throws Exception {
        sendWithHeaders("direct:password", "headerUser", "headerPassword");

        assertTrue(authenticatedUsers.contains("smx"), "the producer should log in as the configured user");
        assertEquals("smx", passwords.get("smx"), "the producer should use the configured password");
        assertFalse(authenticatedUsers.contains("headerUser"), "the CamelSshUsername header should be ignored");
    }

    @Test
    public void testPasswordHeadersAllowed() throws Exception {
        sendWithHeaders("direct:passwordAllowed", "headerUser", "headerPassword");

        assertTrue(authenticatedUsers.contains("headerUser"), "the producer should log in as the header user");
        assertEquals("headerPassword", passwords.get("headerUser"), "the producer should use the header password");
        assertFalse(authenticatedUsers.contains("smx"), "the configured user should not be used");
    }

    @Test
    public void testKeyUsernameHeaderIgnoredByDefault() throws Exception {
        sendWithHeaders("direct:key", "headerUser", null);

        assertTrue(authenticatedUsers.contains("smx"), "the key should log in as the configured user");
        assertFalse(authenticatedUsers.contains("headerUser"), "the CamelSshUsername header should be ignored");
    }

    @Test
    public void testKeyUsernameHeaderAllowed() throws Exception {
        sendWithHeaders("direct:keyAllowed", "headerUser", "headerPassword");

        assertTrue(authenticatedUsers.contains("headerUser"), "the key should log in as the header user");
        assertFalse(authenticatedUsers.contains("smx"), "the configured user should not be used");
        assertNull(passwords.get("headerUser"), "the CamelSshPassword header should not be used with a key");
    }

    @Test
    public void testComponentOption() throws Exception {
        sendWithHeaders("direct:component", "headerUser", "headerPassword");

        assertTrue(authenticatedUsers.contains("headerUser"), "the component option should allow the header user");
        assertEquals("headerPassword", passwords.get("headerUser"), "the component option should allow the header password");
    }

    private void sendWithHeaders(String uri, String username, String password) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("test");
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        Map<String, Object> headers = new ConcurrentHashMap<>();
        headers.put(SshConstants.USERNAME_HEADER, username);
        if (password != null) {
            headers.put(SshConstants.PASSWORD_HEADER, password);
        }
        template.sendBodyAndHeaders(uri, "test", headers);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class)
                        .handled(true)
                        .to("mock:error");

                SshComponent allowing = new SshComponent();
                allowing.getConfiguration().setAllowUsernameAndPasswordFromHeaders(true);
                getContext().addComponent("ssh-allowing", allowing);

                from("direct:password")
                        .to("ssh://smx:smx@localhost:" + port + "?timeout=3000")
                        .to("mock:result");

                from("direct:passwordAllowed")
                        .to("ssh://smx:smx@localhost:" + port + "?timeout=3000&allowUsernameAndPasswordFromHeaders=true")
                        .to("mock:result");

                from("direct:key")
                        .to("ssh://smx@localhost:" + port + "?timeout=3000&certResource=file:src/test/resources/hostkey.pem")
                        .to("mock:result");

                from("direct:keyAllowed")
                        .to("ssh://smx@localhost:" + port + "?timeout=3000&certResource=file:src/test/resources/hostkey.pem"
                            + "&allowUsernameAndPasswordFromHeaders=true")
                        .to("mock:result");

                from("direct:component")
                        .to("ssh-allowing://smx:smx@localhost:" + port + "?timeout=3000")
                        .to("mock:result");
            }
        };
    }
}
