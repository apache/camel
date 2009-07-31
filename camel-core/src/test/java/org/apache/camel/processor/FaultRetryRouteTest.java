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

import org.apache.camel.CamelException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.interceptor.HandleFault;

public class FaultRetryRouteTest extends ContextTestSupport {
    protected MockEndpoint a;
    protected MockEndpoint b;
    protected MockEndpoint error;

    protected final Processor successOnRetryProcessor = new Processor() {
        int count;
        public void process(Exchange exchange) throws CamelException {
            if (count++ == 0) {
                exchange.getOut().setFault(true);
                exchange.getOut().setBody(new CamelException("Failed the first time"));
            }
        }
    };

    public void testSuccessfulRetry() throws Exception {
        a.expectedBodiesReceived("in");
        b.expectedBodiesReceived("in");
        error.expectedMessageCount(0);

        template.sendBody("direct:start", "in");

        MockEndpoint.assertIsSatisfied(a, b, error);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        a = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        b = resolveMandatoryEndpoint("mock:b", MockEndpoint.class);
        error = resolveMandatoryEndpoint("mock:error", MockEndpoint.class);
    }
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().addInterceptStrategy(new HandleFault());

                errorHandler(
                    deadLetterChannel("mock:error")
                        .maximumRedeliveries(4)
                        .loggingLevel(LoggingLevel.DEBUG));

                from("direct:start")
                    .to("mock:a")
                    .process(successOnRetryProcessor)
                    .to("mock:b");
            }
        };
    }
}
