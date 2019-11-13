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
package org.apache.camel.component.shiro.security;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.junit.Test;

public class ShiroAuthenticationTest extends CamelTestSupport {

    @EndpointInject("mock:success")
    protected MockEndpoint successEndpoint;

    @EndpointInject("mock:authenticationException")
    protected MockEndpoint failureEndpoint;

    private byte[] passPhrase = {
        (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
        (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
        (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
        (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17};

    @Test
    public void testShiroAuthenticationFailure() throws Exception {
        //Incorrect password
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("ringo", "stirr");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);

        successEndpoint.expectedMessageCount(0);
        failureEndpoint.expectedMessageCount(1);

        template.send("direct:secureEndpoint", shiroSecurityTokenInjector);

        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSuccessfulShiroAuthenticationWithNoAuthorization() throws Exception {
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("ringo", "starr");
        TestShiroSecurityTokenInjector shiroSecurityTokenInjector = new TestShiroSecurityTokenInjector(shiroSecurityToken, passPhrase);

        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);

        template.send("direct:secureEndpoint", shiroSecurityTokenInjector);

        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSuccessfulTokenHeader() throws Exception {
        ShiroSecurityToken shiroSecurityToken = new ShiroSecurityToken("ringo", "starr");

        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:secureEndpoint", "Beatle Mania", ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, shiroSecurityToken);

        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    @Test
    public void testSuccessfulUsernameHeader() throws Exception {
        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<>();
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "ringo");
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "starr");
        template.sendBodyAndHeaders("direct:secureEndpoint", "Beatle Mania", headers);

        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    @Test
    public void testMissingEncryptionKey() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "ringo");
        headers.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "starr");
        try {
            template.sendBodyAndHeaders("direct:secureEndpointMissingKey", "Beatle Mania", headers);
            fail("Failure expected on a missing key");
        } catch (CamelExecutionException ex) {
            // expected
            assertTrue(ex.getCause() instanceof IllegalArgumentException);
        }
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        final ShiroSecurityPolicy securityPolicy = new ShiroSecurityPolicy("src/test/resources/securityconfig.ini", passPhrase);
        final ShiroSecurityPolicy securityPolicy2 = new ShiroSecurityPolicy("src/test/resources/securityconfig.ini");

        return new RouteBuilder[] {new RouteBuilder() {
            @SuppressWarnings("unchecked")
            public void configure() {
                onException(UnknownAccountException.class, IncorrectCredentialsException.class,
                        LockedAccountException.class, AuthenticationException.class).
                    to("mock:authenticationException");

                from("direct:secureEndpoint").
                    policy(securityPolicy).
                    to("log:incoming payload").
                    to("mock:success");
            }
        }, new RouteBuilder() {
            @SuppressWarnings("unchecked")
            public void configure() {
                onException(UnknownAccountException.class, IncorrectCredentialsException.class,
                        LockedAccountException.class, AuthenticationException.class).
                    to("mock:authenticationException");

                from("direct:secureEndpointMissingKey").
                    policy(securityPolicy2).
                    to("log:incoming payload").
                    to("mock:success");
            }
        }};
    }


    private static class TestShiroSecurityTokenInjector extends ShiroSecurityTokenInjector {

        TestShiroSecurityTokenInjector(ShiroSecurityToken shiroSecurityToken, byte[] bytes) {
            super(shiroSecurityToken, bytes);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, encrypt());
            exchange.getIn().setBody("Beatle Mania");
        }
    }

}
