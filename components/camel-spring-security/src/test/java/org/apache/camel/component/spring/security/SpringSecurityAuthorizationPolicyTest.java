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
package org.apache.camel.component.spring.security;

import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SpringSecurityAuthorizationPolicyTest extends CamelSpringTestSupport {

    @Test
    public void testAuthorizationPassed() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("hello world");
        sendMessageWithAuthentication("jim", "jimspassword");
        end.assertIsSatisfied();
    }

    @Test
    public void testAuthenticationFailed() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(0);
        try {
            sendMessageWithAuthentication("jim", "jimspassword2");
            fail("we should get the access deny exception here");
        } catch (Exception exception) {
            // the exception should be caused by CamelAuthorizationException
            assertTrue("Expect CamelAuthorizationException here", exception.getCause() instanceof CamelAuthorizationException);
        }
        end.assertIsSatisfied();
    }

    @Test
    public void testAuthorizationFailed() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(0);
        try {
            sendMessageWithAuthentication("bob", "bobspassword");
            fail("we should get the access deny exception here");
        } catch (Exception exception) {
            // the exception should be caused by CamelAuthorizationException
            assertTrue("Expect CamelAuthorizationException here", exception.getCause() instanceof CamelAuthorizationException);
        }
        end.assertIsSatisfied();
    }
    
    @Test
    public void testGetAuthorizationTokenFromSecurityContextHolder() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedBodiesReceived("hello world");
        Authentication authToken = new UsernamePasswordAuthenticationToken("jim", "jimspassword");
        SecurityContextHolder.getContext().setAuthentication(authToken);
        template.sendBody("direct:start", "hello world");
        end.assertIsSatisfied();
        SecurityContextHolder.getContext().setAuthentication(null);
        
    }
    
    @Test
    public void testAuthorizationFailedWithWrongExplicitRole() throws Exception {
        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(0);
        try {
            List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_BAD"));

            Authentication authToken = new UsernamePasswordAuthenticationToken("jim", "jimspassword", authorities);
            
            Subject subject = new Subject();
            subject.getPrincipals().add(authToken);

            template.sendBodyAndHeader("direct:start", "hello world", Exchange.AUTHENTICATION, subject);
            fail("we should get the access deny exception here");
        } catch (Exception exception) {
            // the exception should be caused by CamelAuthorizationException
            assertTrue("Expect CamelAuthorizationException here", exception.getCause() instanceof CamelAuthorizationException);
        }
        end.assertIsSatisfied();
    }

    private void sendMessageWithAuthentication(String username, String password) {

        Authentication authToken = new UsernamePasswordAuthenticationToken(username, password);
        
        Subject subject = new Subject();
        subject.getPrincipals().add(authToken);

        template.sendBodyAndHeader("direct:start", "hello world", Exchange.AUTHENTICATION, subject);

    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/org/apache/camel/component/spring/security/SpringSecurityCamelContext.xml");
    }

}
