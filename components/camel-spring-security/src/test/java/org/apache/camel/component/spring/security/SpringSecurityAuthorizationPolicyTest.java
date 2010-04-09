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
package org.apache.camel.component.spring.security;

import javax.security.auth.Subject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.security.Authentication;
import org.springframework.security.AuthenticationManager;
import org.springframework.security.GrantedAuthority;
import org.springframework.security.GrantedAuthorityImpl;
import org.springframework.security.providers.UsernamePasswordAuthenticationToken;

public class SpringSecurityAuthorizationPolicyTest extends CamelSpringTestSupport {

    @Test
    public void testAuthorizationPassed() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("hello world");
        sendMessageWithAuthentication("jim", "jimspassword", "ROLE_USER", "ROLE_ADMIN");
        end.assertIsSatisfied();
    }

    @Test
    public void testAuthorizationFailed() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(0);
        try {
            sendMessageWithAuthentication("bob", "bobspassword", "ROLE_USER");
            fail("we should get the access deny exception here");
        } catch (Exception exception) {
            // the exception should be caused by CamelAuthorizationException
            assertTrue("Expect CamelAuthorizationException here", exception.getCause() instanceof CamelAuthorizationException);
        }
        end.assertIsSatisfied();
    }

    private void sendMessageWithAuthentication(String username, String password, String... roles) {

        Authentication authToken;
        if (roles != null && roles.length > 0) {
            GrantedAuthority[] authorities = new GrantedAuthority[roles.length];
            for (int i = 0; i < roles.length; i++) {
                authorities[i] = new GrantedAuthorityImpl(roles[i]);
            }
            authToken = new UsernamePasswordAuthenticationToken(username, password, authorities);
        } else {
            authToken = new UsernamePasswordAuthenticationToken(username, password);
        }

        Subject subject = new Subject();
        subject.getPrincipals().add(authToken);

        template.sendBodyAndProperty("direct:start", "hello world", Exchange.AUTHENTICATION, subject);

    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                                                  "/org/apache/camel/component/spring/security/SpringSecurityCamelContext.xml");
    }

}
