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
package org.apache.camel.mdc;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MDCAsyncTest extends ExchangeTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        mdcSvc.setCustomHeaders("*");
        mdcSvc.setCustomProperties("*");
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        return context;
    }

    @Test
    public void testAsyncEndpoint() throws Exception {
        MockEndpoint before = getMockEndpoint("mock:before");
        MockEndpoint after = getMockEndpoint("mock:after");
        MockEndpoint result = getMockEndpoint("mock:result");
        before.expectedBodiesReceived("Hello Camel");
        after.expectedBodiesReceived("Bye Camel");
        result.expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        before.assertIsSatisfied();
        after.assertIsSatisfied();
        result.assertIsSatisfied();

        // NOTE: more assertions directly in process as it was simpler to verify the condition while executing the async process.
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start").to("mock:before").to("log:before")
                        .setProperty("prop1", simple("Property1"))
                        .setHeader("head", simple("Header1"))
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                assertEquals("Header1", MDC.get("head"));
                                assertEquals("Property1", MDC.get("prop1"));
                                assertNull(MDC.get("prop2"));
                                // We store the threadId of this execution in a property
                                // as we will use it to assert the thread is different in the direct:b execution
                                exchange.setProperty("thread-a", MDC.get(MDCService.MDC_CAMEL_THREAD_ID));
                            }
                        }).recipientList(constant("direct:foo"));

                from("direct:foo").to("async:bye:camel")
                        .setProperty("prop2", simple("Property2"))
                        .setHeader("head", simple("Header2"))
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                // Make sure this execution is spanned in a different thread
                                // but still the context (in this case the properties) is propagated
                                assertNotEquals(exchange.getProperty("thread-a"), MDC.get(MDCService.MDC_CAMEL_THREAD_ID));
                                assertEquals("Header2", MDC.get("head"));
                                assertEquals("Property1", MDC.get("prop1"));
                                assertEquals("Property2", MDC.get("prop2"));
                            }
                        }).to("log:after").to("mock:after").to("mock:result");
            }
        };
    }

}
