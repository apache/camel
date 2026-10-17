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

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A request containing . or .. segments is routed to the consumer without resolving those segments, so the security
 * constraint has to be evaluated against the effective resolved path. Otherwise a request such as /foo/../admin/x (or
 * its percent-encoded /foo/%2e%2e/admin/x form) reaches the route while skipping the constraint that guards /admin/*.
 */
class NettyHttpBasicAuthConstraintDotSegmentTest extends BaseNettyTestSupport {

    @Override
    public void doPreSetup() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @Override
    public void doPostTearDown() {
        System.clearProperty("java.security.auth.login.config");
    }

    @BindToRegistry("mySecurityConfig")
    public NettyHttpSecurityConfiguration loadSecConf() {
        NettyHttpSecurityConfiguration security = new NettyHttpSecurityConfiguration();
        security.setRealm("karaf");
        SecurityAuthenticator auth = new JAASSecurityAuthenticator();
        auth.setName("karaf");
        security.setSecurityAuthenticator(auth);

        // a specific inclusion, not a catch-all: only /admin/* below the endpoint path is restricted
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/admin/*");
        security.setSecurityConstraint(matcher);

        return security;
    }

    @Test
    void plainPathIsChallenged() {
        assertChallenged("netty-http:http://localhost:{{port}}/foo/admin/x");
    }

    @Test
    void literalDotSegmentIsChallenged() {
        // without resolving the .. the constraint target is /../admin/x, which matches no inclusion
        assertChallenged("netty-http:http://localhost:{{port}}/foo/../admin/x");
    }

    @Test
    void encodedDotSegmentIsChallenged() {
        // the percent-encoded form decodes to the same /../admin/x target
        assertChallenged("netty-http:http://localhost:{{port}}/foo/%2e%2e/admin/x");
    }

    private void assertChallenged(String uri) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(uri, "Hello", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?matchOnUriPrefix=true&securityConfiguration=#mySecurityConfig")
                        .transform().constant("Bye World");
            }
        };
    }
}
