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
package org.apache.camel.processor.onexception;

import java.io.IOException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 *
 */
public class OnExceptionHandleAndThrowNewExceptionTest extends ContextTestSupport {

    public void testOnExceptionHandleAndThrowNewException() throws Exception {
        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IOException cause = assertIsInstanceOf(IOException.class, e.getCause());
            assertNotNull(cause);
            assertEquals("First failure message is: Damn", cause.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(IllegalArgumentException.class)
                    .handled(true)
                    .to("log:onException")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            assertNotNull(cause);

                            throw new IOException("First failure message is: " + cause.getMessage());
                        }
                    });

                from("direct:start")
                    .throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
