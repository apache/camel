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
package org.apache.camel.component.mina;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class MinaUdpUsingTemplateTest extends ContextTestSupport {

    private int messageCount = 3;

    public void testMinaRoute() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(3);
        endpoint.expectedBodiesReceived("Hello Message: 0", "Hello Message: 1", "Hello Message: 2");

        sendUdpMessages();
        // sleeping for while to let the mock endpoint get all the message
        Thread.sleep(2000);

        assertMockEndpointsSatisifed();
    }

    protected void sendUdpMessages() throws Exception {
        for (int i = 0; i < messageCount; i++) {
            template.sendBody("mina:udp://127.0.0.1:4445", "Hello Message: " + i);
        }
    }

    public void testSendingByteMessages() throws Exception {
        MockEndpoint endpoint = getMockEndpoint("mock:result");
        endpoint.expectedMessageCount(1);

        byte[] in = "Hello from bytes".getBytes();
        template.sendBody("mina:udp://127.0.0.1:4445?sync=false", in);

        // sleeping for while to let the mock endpoint get all the message
        Thread.sleep(2000);

        assertMockEndpointsSatisifed();
        List<Exchange> list = endpoint.getReceivedExchanges();
        byte[] out = list.get(0).getIn().getBody(byte[].class);

        for (int i = 0; i < in.length; i++) {
            assertEquals("Thew bytes should be the same", in[i], out[i]);
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("mina:udp://127.0.0.1:4445?sync=false").to("mock:result");
            }
        };
    }
}
