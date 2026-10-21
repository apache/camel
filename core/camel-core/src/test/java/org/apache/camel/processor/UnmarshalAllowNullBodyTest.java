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
import java.nio.charset.StandardCharsets;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UnmarshalAllowNullBodyTest extends ContextTestSupport {

    @Test
    public void testAllowNullBodySkipsTheUnmarshalling() {
        Object out = template.requestBodyAndHeader("direct:allow", null, "foo", "bar");
        assertNull(out);
    }

    @Test
    public void testAllowNullBodyKeepsHeaders() throws Exception {
        getMockEndpoint("mock:allow").expectedHeaderReceived("foo", "bar");
        template.sendBodyAndHeader("direct:allow", null, "foo", "bar");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAllowNullBodyUnmarshalsABody() {
        Object out = template.requestBody("direct:allow", "Camel");
        assertEquals("Hello Camel", out);
    }

    @Test
    public void testAllowNullBodySetsTheReceiveVariableToNull() throws Exception {
        getMockEndpoint("mock:receive").expectedVariableReceived("out", null);
        getMockEndpoint("mock:receive").expectedHeaderReceived("foo", "bar");
        template.sendBodyAndHeader("direct:receive", null, "foo", "bar");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAllowNullVariableLeavesTheMessage() throws Exception {
        getMockEndpoint("mock:send").expectedBodiesReceived("Camel");
        getMockEndpoint("mock:send").expectedHeaderReceived("foo", "bar");
        template.sendBodyAndHeader("direct:send", "Camel", "foo", "bar");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("myDF", new MyHelloDataFormat());

                from("direct:allow")
                        .unmarshal().allowNullBody().custom("myDF")
                        .to("mock:allow");

                from("direct:receive")
                        .setVariable("out", constant("stale"))
                        .unmarshal().allowNullBody().variableReceive("out").custom("myDF")
                        .to("mock:receive");

                from("direct:send")
                        .unmarshal().allowNullBody().variableSend("missing").custom("myDF")
                        .to("mock:send");
            }
        };
    }

    /**
     * Unmarshals the text to a greeting, and fails on a null body, which allowNullBody must skip.
     */
    private static class MyHelloDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) {
            // not used
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            return "Hello " + new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
