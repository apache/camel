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
package org.apache.camel.processor.async;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ThreadPoolProfile;
import org.junit.Test;

/**
 * Unit test to verify that error handling using async() also works as expected.
 *
 * @version 
 */
public class AsyncDeadLetterChannelExecutorServiceRefTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAsyncErrorHandlerWait() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                ThreadPoolProfile profile = new ThreadPoolProfile("myAsyncPool");
                profile.setPoolSize(5);
                context.getExecutorServiceManager().registerThreadPoolProfile(profile);

                errorHandler(deadLetterChannel("mock:dead")
                        .maximumRedeliveries(2)
                        .redeliveryDelay(0)
                        .logStackTrace(false)
                        .executorServiceRef("myAsyncPool"));

                from("direct:in")
                    .threads(2)
                    .to("mock:foo")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            throw new Exception("Forced exception by unit test");
                        }
                    });
            }
        });
        context.start();

        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.requestBody("direct:in", "Hello World");

        assertMockEndpointsSatisfied();
    }

}
