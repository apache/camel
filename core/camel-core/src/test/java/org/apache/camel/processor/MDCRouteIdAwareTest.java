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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.UnitOfWork;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class MDCRouteIdAwareTest extends ContextTestSupport {

    @Test
    public void testMDC() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // enable MDC
                context.setUseMDCLogging(true);
                context.getRegistry().bind("myFilter", new MyFilter());

                from(fileUri("?filter=#myFilter")).routeId("myRoute").to("mock:result");
            }
        };
    }

    private class MyFilter implements GenericFileFilter {

        @Override
        public boolean accept(GenericFile file) {
            String rid = MDC.get(UnitOfWork.MDC_ROUTE_ID);
            String name = MDC.get(UnitOfWork.MDC_CAMEL_CONTEXT_ID);
            return "myRoute".equals(rid) && context.getName().equals(name);
        }
    }
}
