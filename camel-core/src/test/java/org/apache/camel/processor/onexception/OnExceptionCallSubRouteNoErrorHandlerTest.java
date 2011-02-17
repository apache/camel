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
package org.apache.camel.processor.onexception;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class OnExceptionCallSubRouteNoErrorHandlerTest extends ContextTestSupport {

    public void testOnExceptionCallSubRouteNoErrorHandler() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:start").expectedMessageCount(1);
        getMockEndpoint("mock:afterbar").expectedMessageCount(0);
        getMockEndpoint("mock:catch").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).to("mock:error").end();

                from("direct:start")
                    .to("mock:start")
                    .doTry()
                        .to("direct:bar")
                        .to("mock:afterbar")
                    .doCatch(Exception.class)
                        .to("mock:catch")
                    .end()
                    .to("mock:result");

                from("direct:bar")
                    // disable error handling so we can handle the exceptions in the doTry .. doCatch
                    .errorHandler(noErrorHandler())
                    .to("mock:bar")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
