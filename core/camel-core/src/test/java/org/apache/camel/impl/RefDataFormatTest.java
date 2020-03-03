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
package org.apache.camel.impl;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

/**
 * Unit test of the string data format.
 */
public class RefDataFormatTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("reverse", new MyReverseDataFormat());
        return registry;
    }

    @Test
    public void testMarshalRef() throws Exception {
        getMockEndpoint("mock:a").expectedBodiesReceived("CBA");

        template.sendBody("direct:a", "ABC");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshalRef() throws Exception {
        getMockEndpoint("mock:b").expectedBodiesReceived("ABC");

        template.sendBody("direct:b", "CBA");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:a").marshal().custom("reverse").to("mock:a");

                from("direct:b").unmarshal().custom("reverse").to("mock:b");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public static final class MyReverseDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            byte[] bytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, graph);
            String body = reverseBytes(bytes);
            stream.write(body.getBytes());
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            byte[] bytes = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, stream);
            String body = reverseBytes(bytes);
            return body;
        }

        private String reverseBytes(byte[] data) {
            StringBuilder sb = new StringBuilder(data.length);
            for (int i = data.length - 1; i >= 0; i--) {
                char ch = (char)data[i];
                sb.append(ch);
            }
            return sb.toString();
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
    // END SNIPPET: e2

}
