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
package org.apache.camel.component.digitalocean;

import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.myjeeva.digitalocean.pojo.Account;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.component.digitalocean.constants.DigitalOceanOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DigitalOceanComponentTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:getAccountInfo")
                    .setHeader(DigitalOceanHeaders.OPERATION, constant(DigitalOceanOperations.get))
                    .to("digitalocean:account?digitalOceanClient=#digitalOceanClient")
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testGetAccountInfo() throws Exception {

        mockResultEndpoint.expectedMinimumMessageCount(1);
        Exchange exchange = template.request("direct:getAccountInfo", null);
        assertMockEndpointsSatisfied();
        assertIsInstanceOf(Account.class, exchange.getOut().getBody());
        assertEquals(exchange.getIn().getBody(Account.class).getEmail(), "camel@apache.org");
    }


    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        DigitalOceanClient digitalOceanClient = new DigitalOceanClientMock();
        registry.bind("digitalOceanClient", digitalOceanClient);
        return registry;
    }
}
