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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;

/**
 * Unit test for verifying that error handler is wrapped each individual node in a pipeline.
 * Based on CAMEL-1548.
 *
 * @version 
 */
public class ErrorHandlerWrappedEachNodeTest extends ContextTestSupport {

    private static int kabom;
    private static int hi;

    public void testKabom() throws Exception {
        kabom = 0;
        hi = 0;

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Hi Kabom");

        getMockEndpoint("mock:error").expectedMessageCount(0);

        template.sendBody("direct:start", "Kabom");

        assertMockEndpointsSatisfied();

        // we invoke kabom 3 times
        assertEquals(3, kabom);
        // but hi is only invoke 1 time
        assertEquals(1, hi);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use dead letter channel that supports redeliveries
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(3).redeliveryDelay(0).logStackTrace(false));

                from("direct:start")
                    .pipeline("bean:foo?method=hi", "bean:foo?method=kabom").to("mock:result");
            }
        };
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("foo", new MyFooBean());
        return jndi;
    }

    public static final class MyFooBean {

        public void kabom() throws Exception {
            if (kabom++ < 2) {
                throw new IllegalArgumentException("Kabom");
            }
        }

        public String hi(String payload) throws Exception {
            hi++;
            return "Hi " + payload;
        }
    }
}
