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
package org.apache.camel.component.jetty.async;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

public class JettyAsyncContinuationTimeoutTest extends BaseJettyTest {

    @Test
    public void testJettyAsyncTimeout() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        StopWatch watch = new StopWatch();
        try {
            template.requestBody("http://localhost:{{port}}/myservice", null, String.class);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            log.info("Timeout hit and client got reply with failure status code");

            long taken = watch.taken();

            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(504, cause.getStatusCode());

            // should be approx 3-4 sec.
            assertTrue("Timeout should occur faster than " + taken, taken < 4500);
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("jetty:http://localhost:{{port}}/myservice?continuationTimeout=3000").to("async:bye:world?delay=6000").to("mock:result");
            }
        };
    }
}
