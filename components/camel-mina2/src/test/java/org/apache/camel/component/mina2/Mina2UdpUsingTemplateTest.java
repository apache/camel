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
package org.apache.camel.component.mina2;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class Mina2UdpUsingTemplateTest extends BaseMina2Test {

    private int messageCount = 3;

    @Test
    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(3);
        endpoint.expectedBodiesReceived("Hello Message: 0", "Hello Message: 1", "Hello Message: 2");

        sendUdpMessages();
        // sleeping for while to let the mock endpoint get all the message
        Thread.sleep(2000);

        assertMockEndpointsSatisfied();
    }

    protected void sendUdpMessages() throws Exception {
        for (int i = 0; i < messageCount; i++) {
            template.sendBody(String.format("mina2:udp://127.0.0.1:%1$s?sync=false", getPort()), "Hello Message: " + i);
        }
    }

    @Test
    public void testSendingByteMessages() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        byte[] in = "Hello from bytes".getBytes();
        template.sendBody(String.format("mina2:udp://127.0.0.1:%1$s?sync=false", getPort()), in);

        // sleeping for while to let the mock endpoint get all the message
        Thread.sleep(2000);

        assertMockEndpointsSatisfied();
        List<Exchange> list = endpoint.getReceivedExchanges();
        byte[] out = list.get(0).getIn().getBody(byte[].class);

        for (int i = 0; i < in.length; i++) {
            assertEquals("Thew bytes should be the same", in[i], out[i]);
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(String.format("mina2:udp://127.0.0.1:%1$s?sync=false&minaLogger=true", getPort()))
                    .to("mock:result");
            }
        };
    }
}
