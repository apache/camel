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
package org.apache.camel.component.ironmq;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class IronMQPreserveHeadersTest extends CamelTestSupport {

    private IronMQEndpoint endpoint;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void testPreserveHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.expectedBodiesReceived("some payload");
        mock.expectedHeaderReceived("MyHeader", "HeaderValue");
        template.sendBodyAndHeader("direct:start", "some payload", "MyHeader", "HeaderValue");

        assertMockEndpointsSatisfied();
        String id = mock.getExchanges().get(0).getIn().getHeader(IronMQConstants.MESSAGE_ID, String.class);
        Assert.assertNotNull(id);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        IronMQComponent component = new IronMQComponent(context);
        endpoint = (IronMQEndpoint)component.createEndpoint("ironmq://TestQueue?projectId=xxx&token=yyy&preserveHeaders=true");
        endpoint.setClient(new IronMQClientMock("dummy", "dummy"));
        context.addComponent("ironmq", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(endpoint);

                from(endpoint).to("mock:result");
            }
        };
    }
}
