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
package org.apache.camel.component.timer;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.ExceptionHandler;

/**
 * @version 
 */
public class TimerGracefulShutdownTest extends ContextTestSupport {

    private MyExceptionHandler eh = new MyExceptionHandler();

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("eh", eh);
        return jndi;
    }

    public void testTimerShutdown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        assertFalse("Should not throw exception during graceful shutdown", eh.isError());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:foo?period=10&delay=10&exceptionHandler=#eh")
                    .delay(10)
                    .to("log:time")
                    .to("mock:result");
            }
        };
    }

    private static final class MyExceptionHandler implements ExceptionHandler {

        private volatile boolean error;

        @Override
        public void handleException(Throwable exception) {
            error = true;
        }

        @Override
        public void handleException(String message, Throwable exception) {
            error = true;
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            error = true;
        }

        public boolean isError() {
            return error;
        }
    }
}
