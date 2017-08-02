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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;

/**
 *
 */
public class RedeliveryErrorHandlerNoRedeliveryOnShutdownTest extends ContextTestSupport {

    public void testRedeliveryErrorHandlerNoRedeliveryOnShutdown() throws Exception {
        getMockEndpoint("mock:foo").expectedMessageCount(1);

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        // should not take long to stop the route
        StopWatch watch = new StopWatch();
        context.stopRoute("foo");
        watch.taken();

        assertTrue("Should stop route faster, was " + watch.taken(), watch.taken() < 4000);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1

                // this error handler will try up till 20 redelivery attempts with 1 second between.
                // however if we are stopping then do not allow any redeliver attempts.
                errorHandler(defaultErrorHandler()
                        .allowRedeliveryWhileStopping(false)
                        .maximumRedeliveries(20).redeliveryDelay(1000).retryAttemptedLogLevel(LoggingLevel.INFO));

                from("seda:foo").routeId("foo")
                    .to("mock:foo")
                    .throwException(new IllegalArgumentException("Forced"));
                // END SNIPPET: e1
            }
        };
    }
}
