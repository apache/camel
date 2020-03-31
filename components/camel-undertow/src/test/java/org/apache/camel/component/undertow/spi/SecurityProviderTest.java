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
package org.apache.camel.component.undertow.spi;

import io.undertow.util.StatusCodes;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Basic tests with securityProvider, tests whether securityProvider allows or denies access.
 */
public class SecurityProviderTest extends AbstractSecurityProviderTest {


    @Test
    public void testSecuredAllowed() throws Exception {
        securityConfiguration.setRoleToAssign("user");

        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        String out = template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);

        Assert.assertEquals("user", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSecuredNotAllowed() throws Exception {
        securityConfiguration.setRoleToAssign("admin");

        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        try {
            template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);

            fail("Should throw exception");

        } catch (CamelExecutionException e) {
            HttpOperationFailedException he = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(StatusCodes.FORBIDDEN, he.getStatusCode());
        }
    }

}
