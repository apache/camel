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

import io.undertow.util.StatusCodes;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class SpringSecurityBearerTokenTest extends AbstractSpringSecurityBearerTokenTest {

    @Test
    public void testBearerTokenAccess() {
        //configure token in mockFilter
        getMockFilter().setJwt(createToken("Alice", "user"));

        String response = template.requestBody("undertow:http://localhost:{{port}}/myapp",
                "empty body",
                String.class);
        assertNotNull(response);
        assertEquals("Hello Alice!", response);
    }

    @Test
    public void testBearerTokenForbidden() {
        //configure token in mockFilter
        getMockFilter().setJwt(createToken("Tom", "wrongUser"));

        try {
            template.requestBody("undertow:http://localhost:{{port}}/myapp",
                    "empty body",
                    String.class);
            fail("Access is denied");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException he = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(StatusCodes.FORBIDDEN, he.getStatusCode());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("undertow:http://localhost:{{port}}/myapp?allowedRoles=user")
                        .transform(simple("Hello ${in.header." + SpringSecurityProvider.PRINCIPAL_NAME_HEADER + "}!"));
            }
        };
    }
}
