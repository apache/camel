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

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.junit.jupiter.api.Test;

/**
 * With {@code alwaysReauthenticate=false} the Shiro subject stays bound to the thread between exchanges, which is the
 * point of the option. The password carried by each exchange still has to be verified: a username that happens to match
 * the bound subject's principal is not evidence about the credentials presented this time.
 */
class ShiroAuthenticationCredentialAlwaysCheckedTest extends CamelTestSupport {

    @EndpointInject("mock:success")
    protected MockEndpoint successEndpoint;

    @EndpointInject("mock:authenticationException")
    protected MockEndpoint failureEndpoint;

    private final byte[] passPhrase = {
            (byte) 0x08, (byte) 0x09, (byte) 0x0A, (byte) 0x0B,
            (byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F,
            (byte) 0x10, (byte) 0x11, (byte) 0x12, (byte) 0x13,
            (byte) 0x14, (byte) 0x15, (byte) 0x16, (byte) 0x17 };

    @Test
    void aWrongPasswordIsRejectedEvenAfterTheSameUserAuthenticated() throws Exception {
        successEndpoint.expectedMessageCount(1);
        failureEndpoint.expectedMessageCount(1);

        // Authenticates and leaves the subject bound to this thread
        template.send("direct:secureEndpoint", injector("ringo", "starr"));
        // Same username, wrong password - must not ride the bound subject through
        template.send("direct:secureEndpoint", injector("ringo", "not-starr"));

        successEndpoint.assertIsSatisfied();
        failureEndpoint.assertIsSatisfied();
    }

    private TestShiroSecurityTokenInjector injector(String user, String password) {
        return new TestShiroSecurityTokenInjector(new ShiroSecurityToken(user, password), passPhrase);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final ShiroSecurityPolicy securityPolicy
                = new ShiroSecurityPolicy("./src/test/resources/securityconfig.ini", passPhrase, false);

        return new RouteBuilder() {
            @Override
            public void configure() {
                onException(UnknownAccountException.class, IncorrectCredentialsException.class,
                        LockedAccountException.class, AuthenticationException.class).to("mock:authenticationException");

                from("direct:secureEndpoint").policy(securityPolicy).to("mock:success");
            }
        };
    }

    private static class TestShiroSecurityTokenInjector extends ShiroSecurityTokenInjector {

        TestShiroSecurityTokenInjector(ShiroSecurityToken shiroSecurityToken, byte[] bytes) {
            super(shiroSecurityToken, bytes);
        }

        @Override
        public void process(Exchange exchange) {
            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_TOKEN, encrypt());
            exchange.getIn().setBody("Beatle Mania");
        }
    }
}
