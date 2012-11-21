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

import java.util.concurrent.Future;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class HttpAsyncTest extends HttpAsyncTestSupport {
 
    @Test
    public void testAsyncAndSyncAtSameTimeWithHttp() throws Exception {
        // START SNIPPET: e2
        MockEndpoint mock = getMockEndpoint("mock:result");
        // We expect the name job to be faster than the async job even though the async job
        // was started first
        mock.expectedBodiesReceived("Claus", "Bye World");

        // Send a async request/reply message to the http endpoint
        Future<Object> future = template.asyncRequestBody("http://0.0.0.0:" + getPort() + "/myservice", "Hello World");

        // We got the future so in the meantime we can do other stuff, as this is Camel
        // so lets invoke another request/reply route but this time is synchronous
        String name = template.requestBody("direct:name", "Give me a name", String.class);
        assertEquals("Claus", name);

        // Okay we got a name and we have done some other work at the same time
        // the async route is running, but now its about time to wait and get
        // get the response from the async task

        // We use the extract future body to get the response from the future
        // (waiting if needed) and then return a string body response.
        // This allows us to do this in a single code line instead of using the
        // JDK Future API to get hold of it, but you can also use that if you want
        // Adding the (String) To make the CS happy
        String response = template.extractFutureBody(future, String.class);
        assertEquals("Bye World", response);

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

                // Some other service to return a name, this is invoked synchronously
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
