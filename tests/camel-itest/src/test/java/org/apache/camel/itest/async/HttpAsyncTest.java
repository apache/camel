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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class HttpAsyncTest extends ContextTestSupport {

    public void testAsyncAndSyncAtSameTimeWithHttp() throws Exception {
        // START SNIPPET: e2
        MockEndpoint mock = getMockEndpoint("mock:result");
        // we expect the name job to be faster than the async job even though the async job
        // was started first
        mock.expectedBodiesReceived("Claus", "Bye World");

        // send a async request/reply message to the http endpoint
        Future future = template.asyncRequestBody("http://0.0.0.0:9080/myservice", "Hello World");

        // we got the future so in the meantime we can do other stuff, as this is Camel
        // so lets invoke another request/reply route but this time is synchronous
        String name = template.requestBody("direct:name", "Give me a name", String.class);
        assertEquals("Claus", name);

        // okay we got a name and we have done some other work at the same time
        // the async route is running, but now its about time to wait and get
        // get the response from the async task

        // so we use the async extract body to return a string body response
        // this allows us to do this in a single code line instead of using the
        // JDK Future API to get hold of it, but you can also use that if you want
        String response = template.asyncExtractBody(future, String.class);
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
                // the mocks are here for unit test

                // some other service to return a name, this is invoked
                // synhronously
                from("direct:name").transform(constant("Claus")).to("mock:result");

                // simulate a slow http service we want to invoke async
                from("jetty:http://0.0.0.0:9080/myservice")
                    .delay(1000)
                    .transform(constant("Bye World"))
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}
