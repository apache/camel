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

import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NettyHttpBasicAuthConstraintMapperTest extends BaseNettyTestSupport {

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

        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/*");
        matcher.addExclusion("/public/*");
        security.setSecurityConstraint(matcher);

        return security;
    }

    @Test
    public void testBasicAuth() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello Public", "Hello World");

        // we dont need auth for the public page
        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo/public/hello.txt", "Hello Public",
                String.class);
        assertEquals("Bye World", out);

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class));
        NettyHttpOperationFailedException cause = assertIsInstanceOf(NettyHttpOperationFailedException.class, e.getCause());
        assertEquals(401, cause.getStatusCode());

        // username:password is scott:secret
        final String auth = "Basic c2NvdHQ6c2VjcmV0";

        // wait a little bit before next as the connection was closed when denied
        await().atMost(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    String nextOut = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", "Hello World",
                            "Authorization", auth,
                            String.class);
                    assertEquals("Bye World", nextOut);
                });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?matchOnUriPrefix=true&securityConfiguration=#mySecurityConfig")
                        .to("mock:input")
                        .transform().constant("Bye World");
            }
        };
    }

}
