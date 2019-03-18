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
package org.apache.camel.itest.async;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Test;

public class HttpAsyncCallbackTest extends HttpAsyncTestSupport {

    private static final CountDownLatch LATCH = new CountDownLatch(3);

    @Test
    public void testAsyncAndSyncAtSameTimeWithHttp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Hello Claus", "Hello Hadrian", "Hello Willem");

        // START SNIPPET: e3
        MyCallback callback = new MyCallback();

        // Send 3 async request/reply message to the http endpoint
        // where we let the callback handle gathering the responses
        String url = "http://localhost:" + getPort() + "/myservice";
        template.asyncCallbackRequestBody(url, "Claus", callback);
        template.asyncCallbackRequestBody(url, "Hadrian", callback);
        template.asyncCallbackRequestBody(url, "Willem", callback);

        // END SNIPPET: e3
        assertMockEndpointsSatisfied();

        assertTrue("Should get 3 callbacks", LATCH.await(10, TimeUnit.SECONDS));

        // assert that we got all the correct data in our callback
        assertTrue("Claus is missing", callback.getData().contains("Hello Claus"));
        assertTrue("Hadrian is missing", callback.getData().contains("Hello Hadrian"));
        assertTrue("Willem is missing", callback.getData().contains("Hello Willem"));
    }

    // START SNIPPET: e2
    /**
     * Our own callback that will gather all the responses.
     * We extend the SynchronizationAdapter class as we then only need to override the onComplete method.
     */
    private static class MyCallback extends SynchronizationAdapter {

        // below the String elements are added in the context of different threads so that we should make
        // sure that this's done in a thread-safe manner, that's no two threads should call the data.add()
        // method below concurrently, so why we use Vector here and not e.g. ArrayList
        private final List<String> data = new Vector<>();

        @Override
        public void onComplete(Exchange exchange) {
            // this method is invoked when the exchange was a success and we can get the response
            String body = exchange.getOut().getBody(String.class);
            data.add(body);

            // the latch is used for testing purposes
            LATCH.countDown();
        }

        public List<String> getData() {
            return data;
        }
    }
    // END SNIPPET: e2

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                // The mocks are here for unit test
                // Simulate a slow http service (delaying a bit) we want to invoke async
                from("jetty:http://0.0.0.0:" + getPort() + "/myservice")
                    .delay(300)
                    .transform(body().prepend("Hello "))
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }
}
