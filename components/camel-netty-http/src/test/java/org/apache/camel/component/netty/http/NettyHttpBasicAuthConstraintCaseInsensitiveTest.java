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
 * Consumer dispatch matches the context-path case-insensitively, so the security constraint has to be evaluated against
 * the same normalized target. Otherwise a request that differs from the configured context-path only by case reaches
 * the route while skipping the constraint that guards it.
 */
public class NettyHttpBasicAuthConstraintCaseInsensitiveTest extends BaseNettyTestSupport {

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
    public void exactCaseContextPathIsChallenged() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/foo/admin/x", "Hello", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());
    }

    @Test
    public void differentlyCasedContextPathIsChallengedToo() {
        // dispatch reaches the route either way, so the constraint must apply either way
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/Foo/admin/x", "Hello", String.class));
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
