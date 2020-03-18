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

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test of basic securityProvider scenario, when provider does not accepts security configuration.
 */
public class SecurityProviderNoAcceptanceTest extends AbstractSecurityProviderTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext =  super.createCamelContext();
        this.securityConfiguration.setAccept(false);
        return camelContext;
    }

    @Test
    public void testSecuredNotAllowedButNotApplied() throws Exception {
        securityConfiguration.setRoleToAssign("admin");

        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        String out = template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class);

        Assert.assertEquals("", out);

        assertMockEndpointsSatisfied();
    }
}
