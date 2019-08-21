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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

/**
 *
 */
public class DeadLetterChannelUnmarshalSetHeaderTest extends ContextTestSupport {

    @Test
    public void testDLCSetHeader() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedBodiesReceived("Hello World");
        mock.expectedHeaderReceived("foo", "123");
        mock.expectedHeaderReceived("bar", "456");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                MyDataFormat df = new MyDataFormat();

                from("direct:start").errorHandler(deadLetterChannel("direct:error")).unmarshal(df);

                from("direct:error").setHeader("foo", constant("123")).setHeader("bar", constant("456")).to("mock:error");
            }
        };
    }

    private class MyDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            // noop
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            throw new IllegalArgumentException("Damn");
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }
}
