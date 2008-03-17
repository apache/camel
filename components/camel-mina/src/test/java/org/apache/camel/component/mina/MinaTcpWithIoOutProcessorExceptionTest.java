/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.builder.RouteBuilder;

/**
 * To unit test CAMEL-364.
 */
public class MinaTcpWithIoOutProcessorExceptionTest extends ContextTestSupport {

    protected CamelContext container = new DefaultCamelContext();

    private static final int PORT = 6334;
    // use parameter sync=true to force InOut pattern of the MinaExchange
    protected String uri = "mina:tcp://localhost:" + PORT + "?textline=true&sync=true";

    public void testExceptionThrownInProcessor() {
        String body = "Hello World";
        String out = (String) template.requestBody(uri, body);
        assertNull("Should not have sent data back", out);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri).process(new Processor() {
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
