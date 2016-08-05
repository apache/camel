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


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.iron.ironmq.Ids;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;

public class IronMQBatchProducerTest extends CamelTestSupport {

    private IronMQEndpoint endpoint;

    @Test
    public void testProduceBatch() throws Exception {
        String[] messages = new String[] {"{foo:bar}", "{foo2:bar2}"};
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        template.sendBody("direct:start", messages);
        assertMockEndpointsSatisfied();
        assertThat(mock.getReceivedExchanges().size(), equalTo(1));
        Object header = mock.getReceivedExchanges().get(0).getIn().getHeader(IronMQConstants.MESSAGE_ID);
        assertIsInstanceOf(Ids.class, header);
        assertThat(((Ids)header).getSize(), equalTo(2));
    }

    @Test(expected = CamelExecutionException.class)
    public void testProduceBatchWithIllegalPayload() throws Exception {
        template.sendBody("direct:start", Arrays.asList("foo", "bar"));
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        IronMQComponent component = new IronMQComponent(context);
        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("projectId", "dummy");
        parameters.put("token", "dummy");
        endpoint = (IronMQEndpoint)component.createEndpoint("ironmq", "testqueue", parameters);
        endpoint.setClient(new IronMQClientMock("dummy", "dummy"));
        context.addComponent("ironmq", component);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(endpoint).to("mock:result");
            }
        };
    }
}
