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
package org.apache.camel.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 * Trying to reproduce CAMEL-927.
 */
public class TwoTimerWithJMXIssue extends ContextTestSupport {

    private static int counter;

    @Override
    protected void setUp() throws Exception {
        enableJMX(); // the bug was in the JMX so it must be enabled
        super.setUp();
    }

    public void testFromWithNoOutputs() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        assertMockEndpointsSatisfied();

        assertTrue("Counter should be 2 or higher", counter >= 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                context.addInterceptStrategy(new MyTracer());

                from("timer://kickoff_1?period=250").from("timer://kickoff_2?period=250&delay=10").to("mock:result");
            }
        };
    }

    private static class MyTracer implements InterceptStrategy {

        public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition,
                                                     Processor target, Processor nextTarget) throws Exception {
            assertNotNull(target);
            counter++;
            return target;
        }

    }

}
