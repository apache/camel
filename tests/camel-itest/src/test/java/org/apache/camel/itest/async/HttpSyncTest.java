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
package org.apache.camel.itest.async;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class HttpSyncTest extends HttpAsyncTestSupport {

    @Test
    public void testSyncAndSyncAtSameTimeWithHttp() throws Exception {
        // START SNIPPET: e2
        MockEndpoint mock = getMockEndpoint("mock:result");
        // We expect the http job to complete before the name job
        mock.expectedBodiesReceived("Bye World", "Claus");

        // Send a sync request/reply message to the http endpoint
        String response = template.requestBody("http://0.0.0.0:" + getPort() + "/myservice", "Hello World", String.class);
        assertEquals("Bye World", response);

        // Send a sync request/reply message to the direct endpoint
        String name = template.requestBody("direct:name", "Give me a name", String.class);
        assertEquals("Claus", name);

        assertMockEndpointsSatisfied();
        // END SNIPPET: e2
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // The mocks are here for unit test

                // Some other service to return a name, this is invoked synhronously
                from("direct:name").transform(constant("Claus")).to("mock:result");

                // Simulate a slow http service (delaying 1 sec) we want to invoke async
                fromF("jetty:http://0.0.0.0:%s/myservice", getPort())
                    .delay(1000)
                    .transform(constant("Bye World"))
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}