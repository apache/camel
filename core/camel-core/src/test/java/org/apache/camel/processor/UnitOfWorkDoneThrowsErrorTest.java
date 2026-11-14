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
package org.apache.camel.processor;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An Error that is not a VirtualMachineError, thrown by the done of a unit of work, is logged and ignored, as an
 * exception is (UnitOfWorkHelper.doneUow).
 */
class UnitOfWorkDoneThrowsErrorTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, exchange -> new DefaultUnitOfWork(
                exchange) {
            @Override
            public void done(Exchange exchange) {
                super.done(exchange);
                throw new AssertionError("Forced done");
            }
        });
        return context;
    }

    @Test
    void testErrorOfDoneIsLoggedAndIgnored() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // bounded, as a guard: before, the Error reached the caller
        Exchange out = template.asyncSend("direct:start", e -> e.getMessage().setBody("Hello")).get(10, TimeUnit.SECONDS);

        assertMockEndpointsSatisfied();
        assertNull(out.getException());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("mock:result");
            }
        };
    }
}
