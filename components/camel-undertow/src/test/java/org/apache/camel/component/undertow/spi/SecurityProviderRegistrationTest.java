/**
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

import java.util.HashSet;
import java.util.Set;

import io.undertow.Undertow;
import io.undertow.util.StatusCodes;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of registration/unregistration of securityProvider.
 */
public class SecurityProviderRegistrationTest extends AbstractSecurityProviderTest {

    private int registrationCount;
    private int unregistrationCount;
    private CamelContext context;
    private Set<Undertow> registeredServers = new HashSet();


    @Override
    protected CamelContext createCamelContext() throws Exception {
        context =  super.createCamelContext();

        this.securityConfiguration.setRegistrationFunction((b, h) -> {
            registrationCount++;

            Undertow undertow = b.setHandler(null).build();
            registeredServers.add(undertow);
            return undertow;
        });
        this.securityConfiguration.setUnregistrationFunction(u -> {
            unregistrationCount++;
        });
        return context;
    }

    @Test
    public void testRegistration() throws Exception {
        securityConfiguration.setRoleToAssign("user");

        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        try {
            template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);

            fail("Should throw exception");

        } catch (CamelExecutionException e) {
            HttpOperationFailedException he = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(StatusCodes.INTERNAL_SERVER_ERROR, he.getStatusCode());
        }

        context.stop();

        Assert.assertEquals("registration should be executed", 1, registrationCount);
        Assert.assertEquals("unregistration should be executed", 1, unregistrationCount);
    }

}
