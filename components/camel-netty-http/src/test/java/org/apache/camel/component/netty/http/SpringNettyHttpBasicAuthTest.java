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

import jakarta.annotation.Resource;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@CamelSpringTest
@ContextConfiguration(locations = { "/org/apache/camel/component/netty/http/SpringNettyHttpBasicAuthTest.xml" })
public class SpringNettyHttpBasicAuthTest {

    @Produce
    private ProducerTemplate template;

    @EndpointInject("mock:input")
    private MockEndpoint mockEndpoint;

    private Integer port;

    public Integer getPort() {
        return port;
    }

    @Resource(name = "dynaPort")
    public void setPort(Integer port) {
        this.port = port;
    }

    @BeforeAll
    public static void setUpJaas() {
        System.setProperty("java.security.auth.login.config", "src/test/resources/myjaas.config");
    }

    @AfterAll
    public static void tearDownJaas() {
        System.clearProperty("java.security.auth.login.config");
    }

    @Test
    public void testAdminAuth() throws Exception {
        mockEndpoint.reset();

        mockEndpoint.expectedBodiesReceived("Hello Public", "Hello Foo", "Hello Admin");

        // public do not need authentication
        String out = template.requestBody("netty-http:http://localhost:" + port + "/foo/public/welcome", "Hello Public",
                String.class);
        assertEquals("Bye /foo/public/welcome", out);

        // username:password is scott:secret
        String auth = "Basic c2NvdHQ6c2VjcmV0";
        out = template.requestBodyAndHeader("netty-http:http://localhost:" + port + "/foo", "Hello Foo", "Authorization", auth,
                String.class);
        assertEquals("Bye /foo", out);

        out = template.requestBodyAndHeader("netty-http:http://localhost:" + port + "/foo/admin/users", "Hello Admin",
                "Authorization", auth, String.class);
        assertEquals("Bye /foo/admin/users", out);

        mockEndpoint.assertIsSatisfied();

        try {
            template.requestBody("netty-http:http://localhost:" + port + "/foo", "Hello Foo", String.class);
            fail("Should send back 401");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = (NettyHttpOperationFailedException) e.getCause();
            assertEquals(401, cause.getStatusCode());
        }

    }

    @Test
    public void testGuestAuth() {
        // username:password is guest:secret
        String auth = "Basic Z3Vlc3Q6c2VjcmV0";
        String out = template.requestBodyAndHeader("netty-http:http://localhost:" + port + "/foo/guest/hello", "Hello Guest",
                "Authorization", auth, String.class);
        assertEquals("Bye /foo/guest/hello", out);

        // but we can access foo as that is any roles
        out = template.requestBodyAndHeader("netty-http:http://localhost:" + port + "/foo", "Hello Foo", "Authorization", auth,
                String.class);
        assertEquals("Bye /foo", out);

        // accessing admin is restricted for guest user
        try {
            template.requestBodyAndHeader("netty-http:http://localhost:" + port + "/foo/admin/users", "Hello Admin",
                    "Authorization", auth, String.class);
            fail("Should send back 401");
        } catch (CamelExecutionException e) {
            NettyHttpOperationFailedException cause = (NettyHttpOperationFailedException) e.getCause();
            assertEquals(401, cause.getStatusCode());
        }
    }

}
