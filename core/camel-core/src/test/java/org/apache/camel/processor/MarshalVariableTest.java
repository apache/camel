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
import org.apache.camel.spi.DataFormat;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;

public class MarshalVariableTest extends ContextTestSupport {

    @Test
    public void testSend() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("World");
        getMockEndpoint("mock:before").expectedVariableReceived("hello", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedVariableReceived("hello", "Camel");

        template.sendBody("direct:send", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testReceive() throws Exception {
        getMockEndpoint("mock:after").expectedBodiesReceived("World");
        getMockEndpoint("mock:after").expectedVariableReceived("bye", "Bye World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:result").expectedVariableReceived("bye", "Bye World");

        template.sendBody("direct:receive", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendAndReceive() throws Exception {
        getMockEndpoint("mock:before").expectedBodiesReceived("World");
        getMockEndpoint("mock:before").expectedVariableReceived("hello", "Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("World");
        getMockEndpoint("mock:result").expectedVariableReceived("bye", "Bye Camel");

        template.sendBody("direct:sendAndReceive", "World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("myDF", new MyByeDataFormat());

                from("direct:send")
                        .setVariable("hello", simple("Camel"))
                        .to("mock:before")
                        .marshal().variableSend("hello").custom("myDF")
                        .to("mock:result");

                from("direct:receive")
                        .marshal().variableReceive("bye").custom("myDF")
                        .to("mock:after")
                        .setBody(simple("${variable:bye}"))
                        .to("mock:result");

                from("direct:sendAndReceive")
                        .setVariable("hello", simple("Camel"))
                        .to("mock:before")
                        .marshal().variableSend("hello").variableReceive("bye").custom("myDF")
                        .to("mock:result");
            }
        };
    }

    public static class MyByeDataFormat extends ServiceSupport implements DataFormat {

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
            String line = "Bye " + graph.toString();
            stream.write(line.getBytes());
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            // noop
            return null;
        }
    }
}
