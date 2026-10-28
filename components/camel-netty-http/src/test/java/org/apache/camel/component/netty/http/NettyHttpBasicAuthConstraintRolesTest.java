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
 * The roles of a security constraint inclusion are matched by role name. The JAAS test login gives scott the roles
 * admin and guest, and gives guest the role guest.
 */
class NettyHttpBasicAuthConstraintRolesTest extends BaseNettyTestSupport {

    // username:password is scott:secret
    private static final String SCOTT = "Basic c2NvdHQ6c2VjcmV0";
    // username:password is guest:secret
    private static final String GUEST = "Basic Z3Vlc3Q6c2VjcmV0";

    @Override
    protected void doPreSetup() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @Override
    protected void doPostTearDown() {
        System.clearProperty("java.security.auth.login.config");
    }

    @BindToRegistry("mySecurityConfig")
    public NettyHttpSecurityConfiguration loadSecConf() {
        NettyHttpSecurityConfiguration security = new NettyHttpSecurityConfiguration();
        security.setRealm("karaf");
        SecurityAuthenticator auth = new JAASSecurityAuthenticator();
        auth.setName("karaf");
        security.setSecurityAuthenticator(auth);

        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/admin/*", "admin");
        matcher.addInclusion("/admins/*", "admins");
        matcher.addInclusion("/staff/*", "operator, guest");
        security.setSecurityConstraint(matcher);

        return security;
    }

    @Test
    void userWithTheRoleIsAccepted() {
        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo/admin/x", "Hello",
                "Authorization", SCOTT, String.class);
        assertEquals("Bye World", out);
    }

    @Test
    void anyRoleOfTheListIsAccepted() {
        String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo/staff/x", "Hello",
                "Authorization", GUEST, String.class);
        assertEquals("Bye World", out);
    }

    @Test
    void roleIsMatchedByTheWholeName() {
        // scott has the role admin, which is not the role admins
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo/admins/x", "Hello",
                        "Authorization", SCOTT, String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());
    }

    @Test
    void userWithoutTheRoleIsRejected() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo/admin/x", "Hello",
                        "Authorization", GUEST, String.class));
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
