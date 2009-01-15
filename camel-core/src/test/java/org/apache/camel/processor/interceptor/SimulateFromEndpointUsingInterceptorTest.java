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
package org.apache.camel.processor.interceptor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.spi.InterceptStrategy;

/**
 * Using a interceptor to simulate setting the fromEndpointUri (we added in Camel 2.0)
 * from Camel 1.x.
 */
public class SimulateFromEndpointUsingInterceptorTest extends ContextTestSupport {

    public void testSimulateFromEndpointUri() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).property("fromEndpointUri").equals("direct:start");
        
        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addInterceptStrategy(new MyFromEndpointInterceptor());

                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }

    /**
     * Interceptor strategy that is weaven in the route above and also a delegate processor so
     * we can continue processing the original Exchange after we have decorated and added the
     * from endpoint uri.
     */
    private final class MyFromEndpointInterceptor extends DelegateProcessor implements InterceptStrategy {

        private ProcessorType node;

        private MyFromEndpointInterceptor() {
        }

        private MyFromEndpointInterceptor(ProcessorType node, Processor target) {
            super(target);
            this.node = node;
        }

        public Processor wrapProcessorInInterceptors(ProcessorType processorType, Processor target) throws Exception {
            return new MyFromEndpointInterceptor(processorType, target);
        }

        public void process(Exchange exchange) throws Exception {
            // compuate and set from endpoint uri
            if (exchange.getProperty("fromEndpointUri") == null) {
                ProcessorType parent = node.getParent();
                if (parent instanceof RouteType) {
                    RouteType rt = (RouteType)parent;
                    // note assumes that we only have one input (that is very common anyway)
                    String fromEndpointUri = rt.getInputs().get(0).getEndpoint().getEndpointUri();
                    exchange.setProperty("fromEndpointUri", fromEndpointUri);
                }
            }

            // must invoke the target to continue the routing
            getProcessor().process(exchange);
        }
    }
}
