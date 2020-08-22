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
package org.apache.camel.component.mina;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * To unit test CAMEL-364.
 */
public class MinaTcpWithIoOutProcessorExceptionTest extends BaseMinaTest {

    @Test
    public void testExceptionThrownInProcessor() {
        String body = "Hello World";
        Object result
                = template.requestBody(String.format("mina:tcp://localhost:%1$s?textline=true&sync=true", getPort()), body);
        // The exception should be passed to the client
        assertNotNull(result, "the result should not be null");
        assertEquals("java.lang.IllegalArgumentException: Forced exception", result, "result is IllegalArgumentException");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                // use no delay for fast unit testing
                errorHandler(defaultErrorHandler().maximumRedeliveries(2));

                from(String.format("mina:tcp://localhost:%1$s?textline=true&sync=true", getPort())).process(new Processor() {

                    public void process(Exchange e) {
                        assertEquals("Hello World", e.getIn().getBody(String.class));
                        // simulate a problem processing the input to see if we can handle it properly
                        throw new IllegalArgumentException("Forced exception");
                    }
                });
            }
        };
    }
}
