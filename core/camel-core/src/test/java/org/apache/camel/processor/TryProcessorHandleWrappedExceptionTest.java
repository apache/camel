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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for try .. handle routing where it should handle wrapped exceptions
 * as well.
 */
public class TryProcessorHandleWrappedExceptionTest extends ContextTestSupport {

    private boolean handled;

    @Test
    public void testTryCatchFinally() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        getMockEndpoint("mock:finally").expectedMessageCount(1);

        sendBody("direct:start", "<test>Hello World!</test>");
        assertTrue("Should have been handled", handled);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").doTry().process(new ProcessorFail()).to("mock:result").doCatch(IllegalStateException.class).process(new ProcessorHandle()).doFinally()
                    .to("mock:finally").end();
            }
        };
    }

    private class ProcessorFail implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            throw new IllegalStateException("Force to fail");
        }
    }

    private class ProcessorHandle implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            handled = true;

            assertEquals("Should not be marked as failed", false, exchange.isFailed());

            Exception e = (Exception)exchange.getProperty(Exchange.EXCEPTION_CAUGHT);
            assertNotNull("There should be an exception", e);
            assertTrue(e instanceof IllegalStateException);
            assertEquals("Force to fail", e.getMessage());
        }
    }

}
