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
package org.apache.camel.component.ironmq.integrationtest;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own projectId and token!")
public class IronMQRackspaceComponentTest extends CamelTestSupport {
    private String projectId = "myIronMQproject";
    private String token = "myIronMQToken";

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testIronMQ() throws Exception {
        result.setExpectedMessageCount(1);
        result.expectedBodiesReceived("some payload");
        template.sendBody("some payload");

        assertMockEndpointsSatisfied();
        String id = result.getExchanges().get(0).getIn().getHeader("MESSAGE_ID", String.class);
        Assert.assertNotNull(id);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String ironMQEndpoint = "ironmq:testqueue?projectId=" + projectId + "&token=" + token + "&ironMQCloud=https://mq-rackspace-lon.iron.io";
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to(ironMQEndpoint);

                from(ironMQEndpoint + "&maxMessagesPerPoll=5").to("mock:result");
            }
        };
    }
}
