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
package org.apache.camel.coap;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.junit.Test;

public class CoAPRestVerbTest extends CamelTestSupport {
    static int coapport = AvailablePortFinder.getNextAvailable();

    @Test
    public void testGetAll() throws Exception {
        NetworkConfig.createStandardWithoutFile();

        CoapClient client = new CoapClient("coap://localhost:" + coapport + "/users");
        client.setTimeout(1000000);
        CoapResponse rsp = client.get();

        assertEquals("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]", rsp.getResponseText());
    }

    @Test
    public void testGetOne() throws Exception {
        NetworkConfig.createStandardWithoutFile();

        CoapClient client = new CoapClient("coap://localhost:" + coapport + "/users/1");
        client.setTimeout(1000000);
        CoapResponse rsp = client.get();

        assertEquals("{ \"id\":\"1\", \"name\":\"Scott\" }", rsp.getResponseText());
    }

    @Test
    public void testPost() throws Exception {
        NetworkConfig.createStandardWithoutFile();

        final String body = "{ \"id\":\"1\", \"name\":\"Scott\" }";

        MockEndpoint mock = getMockEndpoint("mock:create");
        mock.expectedBodiesReceived(body);

        CoapClient client = new CoapClient("coap://localhost:" + coapport + "/users");
        client.setTimeout(1000000);
        CoapResponse rsp = client.post(body, MediaTypeRegistry.APPLICATION_JSON);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPut() throws Exception {
        NetworkConfig.createStandardWithoutFile();

        final String body = "{ \"id\":\"1\", \"name\":\"Scott\" }";

        MockEndpoint mock = getMockEndpoint("mock:update");
        mock.expectedBodiesReceived(body);
        mock.expectedHeaderReceived("id", "1");

        CoapClient client = new CoapClient("coap://localhost:" + coapport + "/users/1");
        client.setTimeout(1000000);
        CoapResponse rsp = client.put(body, MediaTypeRegistry.APPLICATION_JSON);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDelete() throws Exception {
        NetworkConfig.createStandardWithoutFile();

        MockEndpoint mock = getMockEndpoint("mock:delete");
        mock.expectedHeaderReceived("id", "1");

        CoapClient client = new CoapClient("coap://localhost:" + coapport + "/users/1");
        client.setTimeout(1000000);
        CoapResponse rsp = client.delete();

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("coap").host("localhost").port(coapport);

                rest()
                    .get("/users").route().transform().constant("[{ \"id\":\"1\", \"name\":\"Scott\" },{ \"id\":\"2\", \"name\":\"Claus\" }]").endRest()
                    .get("/users/{id}").route().transform().simple("{ \"id\":\"${header.id}\", \"name\":\"Scott\" }").endRest()
                    .post("/users").to("mock:create")
                    .put("/users/{id}").to("mock:update")
                    .delete("/users/{id}").to("mock:delete");
            }
        };
    }
}
