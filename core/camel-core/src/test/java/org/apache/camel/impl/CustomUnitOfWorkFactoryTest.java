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
package org.apache.camel.impl;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.spi.UnitOfWorkFactory;
import org.junit.jupiter.api.Test;

public class CustomUnitOfWorkFactoryTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().addContextPlugin(UnitOfWorkFactory.class, new MyUnitOfWorkFactory());
        return context;
    }

    @Test
    public void testCustomUnitOfWorkFactory() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").expectedHeaderReceived("before", "I was here");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }

    private static class MyUnitOfWorkFactory implements UnitOfWorkFactory {

        @Override
        public UnitOfWork createUnitOfWork(Exchange exchange) {
            return new MyUnitOfWork(exchange);
        }
    }

    private static class MyUnitOfWork extends DefaultUnitOfWork {

        MyUnitOfWork(Exchange exchange) {
            super(exchange);
        }

        @Override
        public AsyncCallback beforeProcess(Processor processor, Exchange exchange, AsyncCallback callback) {
            exchange.getIn().setHeader("before", "I was here");
            return callback;
        }

        @Override
        public boolean isBeforeAfterProcess() {
            return true;
        }
    }
}
