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
 * A request path containing {@code .} or {@code ..} segments may be interpreted differently by the security constraint,
 * the consumer dispatch and the route, so with a {@code securityConstraint} configured such a request is rejected with
 * HTTP 400. This closes both directions of the mismatch: a dot-segment path can neither reach a restricted inclusion
 * (for example {@code /admin/*}) unchallenged, nor be treated as an excluded (public) path when its raw form targets a
 * restricted one.
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

        // a specific inclusion and a public exclusion, both below the endpoint path
        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/admin/*");
        matcher.addExclusion("/public/*");
        security.setSecurityConstraint(matcher);

        return security;
    }

    @Test
    void restrictedPathIsChallenged() {
        assertStatus("netty-http:http://localhost:{{port}}/foo/admin/x", 401);
    }

    @Test
    void excludedPathIsPublic() {
        assertEquals("Bye World",
                template.requestBody("netty-http:http://localhost:{{port}}/foo/public/x", "Hello", String.class));
    }

    @Test
    void literalDotSegmentIsRejected() {
        // a leading .. reaches /admin/x as the raw target evades /admin/*
        assertStatus("netty-http:http://localhost:{{port}}/foo/../admin/x", 400);
    }

    @Test
    void encodedDotSegmentIsRejected() {
        assertStatus("netty-http:http://localhost:{{port}}/foo/%2e%2e/admin/x", 400);
    }

    @Test
    void inContextDotSegmentIsRejected() {
        // resolves to /foo/admin/y, which stays below the endpoint context
        assertStatus("netty-http:http://localhost:{{port}}/foo/x/../admin/y", 400);
    }

    @Test
    void reverseDotSegmentIsRejected() {
        // raw /admin/../public/x matches the /admin/* inclusion, but resolves to the excluded /public/x; rejecting it
        // prevents the restricted raw target from being treated as public
        assertStatus("netty-http:http://localhost:{{port}}/foo/admin/../public/x", 400);
    }

    @Test
    void pathParameterIsRejected() {
        // raw /admin;x/y evades /admin/*, but a backend that strips path parameters sees /admin/y
        assertStatus("netty-http:http://localhost:{{port}}/foo/admin;x/y", 400);
        assertStatus("netty-http:http://localhost:{{port}}/foo/admin%3bx/y", 400);
    }

    @Test
    void dotSegmentWithPathParameterIsRejected() {
        // raw /public/..;/admin/x matches the /public/* exclusion, but a backend that treats ..; as .. sees /admin/x
        assertStatus("netty-http:http://localhost:{{port}}/foo/public/..;/admin/x", 400);
    }

    @Test
    void backslashIsRejected() {
        // raw /public/\..\..\admin matches the /public/* exclusion, but a backend that treats \ as / sees /admin
        assertStatus("netty-http:http://localhost:{{port}}/foo/public/%5c..%5c..%5cadmin", 400);
    }

    @Test
    void emptySegmentIsRejected() {
        // raw //admin/x evades /admin/*, but a backend that collapses empty segments sees /admin/x
        assertStatus("netty-http:http://localhost:{{port}}/foo//admin/x", 400);
    }

    private void assertStatus(String uri, int expected) {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody(uri, "Hello", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(expected, cause.getStatusCode());
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
