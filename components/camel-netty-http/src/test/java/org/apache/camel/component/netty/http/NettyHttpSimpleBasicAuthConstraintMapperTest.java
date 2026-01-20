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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NettyHttpSimpleBasicAuthConstraintMapperTest extends BaseNettyTestSupport {

    @Override
    public void doPreSetup() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @Override
    public void doPostTearDown() {
        System.clearProperty("java.security.auth.login.config");
    }

    @BindToRegistry("myConstraint")
    public SecurityConstraintMapping loadSecurityConstraintMapping() {

        SecurityConstraintMapping matcher = new SecurityConstraintMapping();
        matcher.addInclusion("/*");
        matcher.addExclusion("/public/*");

        return matcher;
    }

    @DisplayName("Tests whether it can access the public page without auth")
    @Test
    @Order(1)
    void testAccessToPublicPage() {
        getMockEndpoint("mock:input").expectedBodiesReceived("Hello Public", "Hello World");

        // we do not need auth for the public page
        String out = template.requestBody("netty-http:http://localhost:{{port}}/foo/public/hello.txt", "Hello Public",
                String.class);

        assertEquals("Bye World", out);
    }

    @DisplayName("Tests whether it returns unauthorized (HTTP 401) for unauthorized access")
    @Test
    @Order(2)
    void testUnauthorized() {
        CamelExecutionException exception = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("netty-http:http://localhost:{{port}}/foo", "Hello World", String.class),
                "Should have thrown a CamelExecutionException");

        NettyHttpOperationFailedException cause
                = assertIsInstanceOf(NettyHttpOperationFailedException.class, exception.getCause());
        assertEquals(UNAUTHORIZED.code(), cause.getStatusCode(), "Should have sent back HTTP status 401");
    }

    @DisplayName("Tests whether it authorized access to non-public resource succeeds")
    @Test
    @Order(3)
    public void testBasicAuth() throws Exception {
        String auth = "Basic c2NvdHQ6c2VjcmV0";

        await().atMost(2, TimeUnit.SECONDS).untilAsserted(
                () -> {
                    String out = template.requestBodyAndHeader("netty-http:http://localhost:{{port}}/foo", "Hello World",
                            "Authorization", auth,
                            String.class);
                    assertEquals("Bye World", out);
                });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("netty-http:http://0.0.0.0:{{port}}/foo?matchOnUriPrefix=true"
                     + "&securityConfiguration.realm=karaf&securityConfiguration.securityConstraint=#myConstraint")
                        .to("mock:input")
                        .transform().constant("Bye World");
            }
        };
    }

}
