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
package org.apache.camel.component.ahc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AhcProducePostTest extends BaseAhcTest {

    @Test
    public void testAhcProduce() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAhcProduceByteArray() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World");
        // should not use chunked when its byte array
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.CONTENT_LENGTH, 5);
        getMockEndpoint("mock:input").message(0).header(Exchange.TRANSFER_ENCODING).isNull();
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "World".getBytes(StandardCharsets.UTF_8));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAhcProduceStream() throws Exception {
        getMockEndpoint("mock:input").expectedBodiesReceived("World");
        // should use chunked for stream
        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.TRANSFER_ENCODING, "chunked");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", new ByteArrayInputStream("World".getBytes(StandardCharsets.UTF_8)));

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAhcProduceInOut() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.requestBody("direct:start", "World", String.class);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testAhcProduceDirectly() {
        Object out = template.requestBody(getAhcEndpointUri(), "World", String.class);
        assertEquals("Bye World", out);
    }

    @Test
    public void testAhcProducePostHeader() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");

        template.sendBodyAndHeader("direct:start", "World", Exchange.HTTP_METHOD, "POST");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to(getAhcEndpointUri())
                        .to("mock:result");

                from(getTestServerEndpointUri())
                        .convertBodyTo(String.class)
                        .to("mock:input")
                        .transform(simple("Bye ${body}"));
            }
        };
    }
}
