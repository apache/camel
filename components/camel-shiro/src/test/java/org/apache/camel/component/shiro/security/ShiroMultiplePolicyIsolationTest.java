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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.config.Ini;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ShiroMultiplePolicyIsolationTest extends CamelTestSupport {

    private static final byte[] KEY_A = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY_B = "fedcba9876543210".getBytes(StandardCharsets.US_ASCII);

    @EndpointInject("mock:successA")
    protected MockEndpoint successEndpointA;

    @EndpointInject("mock:failureA")
    protected MockEndpoint failureEndpointA;

    @EndpointInject("mock:successB")
    protected MockEndpoint successEndpointB;

    @EndpointInject("mock:failureB")
    protected MockEndpoint failureEndpointB;

    private ShiroSecurityPolicy policyA;
    private ShiroSecurityPolicy policyB;
    private ShiroSecurityPolicy policyNoReauthA;
    private ShiroSecurityPolicy policyNoReauthB;

    @EndpointInject("mock:successNoReauthA")
    protected MockEndpoint successEndpointNoReauthA;

    @EndpointInject("mock:failureNoReauthA")
    protected MockEndpoint failureEndpointNoReauthA;

    @EndpointInject("mock:successNoReauthB")
    protected MockEndpoint successEndpointNoReauthB;

    @EndpointInject("mock:failureNoReauthB")
    protected MockEndpoint failureEndpointNoReauthB;

    @EndpointInject("mock:successAuthB")
    protected MockEndpoint successEndpointAuthB;

    @EndpointInject("mock:failureAuthB")
    protected MockEndpoint failureEndpointAuthB;

    @EndpointInject("mock:nestedSuccess")
    protected MockEndpoint nestedSuccessEndpoint;

    @EndpointInject("mock:innerSuccess")
    protected MockEndpoint innerSuccessEndpoint;

    private ShiroSecurityPolicy policyAuthB;

    @AfterEach
    public void tearDownThreadContext() {
        ThreadContext.remove();
    }

    @Test
    public void testPolicyIsolationBetweenDifferentRealms() throws Exception {
        successEndpointA.expectedMessageCount(1);
        failureEndpointA.expectedMessageCount(1);
        successEndpointB.expectedMessageCount(1);
        failureEndpointB.expectedMessageCount(1);

        // 1. Valid user for policy A should succeed on route A
        Map<String, Object> headersAlice = new HashMap<>();
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordA");
        template.sendBodyAndHeaders("direct:routeA", "payloadA", headersAlice);

        // 2. Valid user for policy B should fail on route A
        Map<String, Object> headersBobOnA = new HashMap<>();
        headersBobOnA.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
        headersBobOnA.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeA", "payloadA", headersBobOnA);

        // 3. Valid user for policy B should succeed on route B
        Map<String, Object> headersBobOnB = new HashMap<>();
        headersBobOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
        headersBobOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeB", "payloadB", headersBobOnB);

        // 4. User for policy A should fail on route B
        Map<String, Object> headersAliceOnB = new HashMap<>();
        headersAliceOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        headersAliceOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordA");
        template.sendBodyAndHeaders("direct:routeB", "payloadB", headersAliceOnB);

        successEndpointA.assertIsSatisfied();
        failureEndpointA.assertIsSatisfied();
        successEndpointB.assertIsSatisfied();
        failureEndpointB.assertIsSatisfied();
    }

    @Test
    public void testPolicyIsolationWithReauthenticateFalse() throws Exception {
        successEndpointNoReauthA.expectedMessageCount(1);
        failureEndpointNoReauthA.expectedMessageCount(1);
        successEndpointNoReauthB.expectedMessageCount(1);
        failureEndpointNoReauthB.expectedMessageCount(1);

        // 1. Valid user for policy A on route A
        Map<String, Object> headersAlice = new HashMap<>();
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordA");
        template.sendBodyAndHeaders("direct:routeNoReauthA", "payloadA", headersAlice);

        // 2. Valid user for policy B on route B
        Map<String, Object> headersBob = new HashMap<>();
        headersBob.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
        headersBob.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeNoReauthB", "payloadB", headersBob);

        // 3. User for policy B should fail on route A even after B succeeded
        Map<String, Object> headersBobOnA = new HashMap<>();
        headersBobOnA.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
        headersBobOnA.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeNoReauthA", "payloadA", headersBobOnA);

        // 4. User for policy A should fail on route B
        Map<String, Object> headersAliceOnB = new HashMap<>();
        headersAliceOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        headersAliceOnB.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordA");
        template.sendBodyAndHeaders("direct:routeNoReauthB", "payloadB", headersAliceOnB);

        successEndpointNoReauthA.assertIsSatisfied();
        failureEndpointNoReauthA.assertIsSatisfied();
        successEndpointNoReauthB.assertIsSatisfied();
        failureEndpointNoReauthB.assertIsSatisfied();
    }

    @Test
    public void testThreadContextCleanupOnFailureWithReauthenticateFalse() throws Exception {
        failureEndpointNoReauthA.expectedMessageCount(1);

        Map<String, Object> invalidHeaders = new HashMap<>();
        invalidHeaders.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        invalidHeaders.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "wrongpassword");
        template.sendBodyAndHeaders("direct:routeNoReauthA", "payloadA", invalidHeaders);

        failureEndpointNoReauthA.assertIsSatisfied();
        assertNull(ThreadContext.getSubject(), "ThreadContext subject should be cleaned up on failure");
        assertNull(ThreadContext.getSecurityManager(), "ThreadContext security manager should be cleaned up on failure");
    }

    @Test
    public void testRoleAuthorizationIsolation() throws Exception {
        successEndpointAuthB.expectedMessageCount(1);
        failureEndpointAuthB.expectedMessageCount(1);

        // 1. Bob has roleB in realm B, passes authorization
        Map<String, Object> headersBob = new HashMap<>();
        headersBob.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
        headersBob.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeAuthB", "payload", headersBob);

        // 2. Charlie is in realm B with roleA (from realm A's permission model), fails authorization on policy B
        Map<String, Object> headersCharlie = new HashMap<>();
        headersCharlie.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "charlie");
        headersCharlie.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
        template.sendBodyAndHeaders("direct:routeAuthB", "payload", headersCharlie);

        successEndpointAuthB.assertIsSatisfied();
        failureEndpointAuthB.assertIsSatisfied();
    }

    @Test
    public void testNestedRouteSubjectThreadStateRestoration() throws Exception {
        nestedSuccessEndpoint.expectedMessageCount(1);
        innerSuccessEndpoint.expectedMessageCount(1);

        Map<String, Object> headersAlice = new HashMap<>();
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "alice");
        headersAlice.put(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordA");
        template.sendBodyAndHeaders("direct:outerRoute", "payload", headersAlice);

        nestedSuccessEndpoint.assertIsSatisfied();
        innerSuccessEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        Ini iniA = new Ini();
        iniA.setSectionProperty("users", "alice", "passwordA, roleA");
        policyA = new ShiroSecurityPolicy(iniA, KEY_A);
        policyNoReauthA = new ShiroSecurityPolicy(iniA, KEY_A, false);

        Ini iniB = new Ini();
        iniB.setSectionProperty("users", "bob", "passwordB, roleB");
        iniB.setSectionProperty("users", "charlie", "passwordB, roleA");
        policyB = new ShiroSecurityPolicy(iniB, KEY_B);
        policyNoReauthB = new ShiroSecurityPolicy(iniB, KEY_B, false);

        policyAuthB = new ShiroSecurityPolicy(iniB, KEY_B);
        policyAuthB.setRolesList(List.of("roleB"));

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:routeA")
                        .onException(AuthenticationException.class)
                        .handled(true)
                        .to("mock:failureA")
                        .end()
                        .policy(policyA)
                        .to("mock:successA");

                from("direct:routeB")
                        .onException(AuthenticationException.class)
                        .handled(true)
                        .to("mock:failureB")
                        .end()
                        .policy(policyB)
                        .to("mock:successB");

                from("direct:routeNoReauthA")
                        .onException(AuthenticationException.class)
                        .handled(true)
                        .to("mock:failureNoReauthA")
                        .end()
                        .policy(policyNoReauthA)
                        .to("mock:successNoReauthA");

                from("direct:routeNoReauthB")
                        .onException(AuthenticationException.class)
                        .handled(true)
                        .to("mock:failureNoReauthB")
                        .end()
                        .policy(policyNoReauthB)
                        .to("mock:successNoReauthB");

                from("direct:routeAuthB")
                        .onException(CamelAuthorizationException.class)
                        .handled(true)
                        .to("mock:failureAuthB")
                        .end()
                        .policy(policyAuthB)
                        .to("mock:successAuthB");

                from("direct:outerRoute")
                        .policy(policyNoReauthA)
                        .process(exchange -> {
                            Subject subject = SecurityUtils.getSubject();
                            assertEquals("alice", subject.getPrincipal());
                            assertTrue(subject.isAuthenticated());
                        })
                        .to("direct:innerRoute")
                        .process(exchange -> {
                            Subject restored = SecurityUtils.getSubject();
                            assertEquals("alice", restored.getPrincipal(),
                                    "Outer subject should be restored after calling inner route");
                            assertTrue(restored.isAuthenticated(),
                                    "Restored subject must still be authenticated");
                        })
                        .to("mock:nestedSuccess");

                from("direct:innerRoute")
                        .process(exchange -> {
                            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_USERNAME, "bob");
                            exchange.getIn().setHeader(ShiroSecurityConstants.SHIRO_SECURITY_PASSWORD, "passwordB");
                        })
                        .policy(policyB)
                        .to("mock:innerSuccess");
            }
        };
    }
}
