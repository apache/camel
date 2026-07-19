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
package org.apache.camel.component.ironmq;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class IronMQConsumerHeaderFilterTest extends CamelTestSupport {

    private IronMQEndpoint endpoint;
    private IronMQClientMock clientMock;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testCamelHeadersInMessageEnvelopeAreFiltered() throws Exception {
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("some payload");
        // a regular header embedded in the envelope must still be mapped onto the message
        result.expectedHeaderReceived("MyHeader", "HeaderValue");

        // message envelope as crafted by a sender: internal Camel headers embedded in the envelope must not be
        // mapped onto the Camel message (matched case-insensitively)
        String envelope = "{\"headers\":{\"MyHeader\":\"HeaderValue\",\"CamelFileName\":\"injected.txt\","
                          + "\"camelExecCommandExecutable\":\"injected\"},\"body\":\"some payload\"}";
        clientMock.queue("TestQueue").push(envelope, 0);

        MockEndpoint.assertIsSatisfied(context);

        Message received = result.getExchanges().get(0).getIn();
        assertNull(received.getHeader("CamelFileName"));
        assertNull(received.getHeader("camelExecCommandExecutable"));
        assertNotNull(received.getHeader(IronMQConstants.MESSAGE_ID));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        IronMQComponent component = new IronMQComponent(context);
        component.init();
        endpoint = (IronMQEndpoint) component
                .createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy&preserveHeaders=true");
        clientMock = new IronMQClientMock("dummy", "dummy");
        endpoint.setClient(clientMock);
        context.addComponent("ironmq", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(endpoint).to("mock:result");
            }
        };
    }
}
