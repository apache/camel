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
package org.apache.camel.component.ironmq.integrationtest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ironmq.IronMQConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Integration test that requires ironmq account.")
public class IronMQFIFOTest extends CamelTestSupport {
    private String projectId = "replace-this";
    private String token = "replace-this";

    private final String ironMQEndpoint = "ironmq:testqueue?projectId=" + projectId + "&token=" + token + "&maxMessagesPerPoll=20&ironMQCloud=https://mq-v3-aws-us-east-1.iron.io";

    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Before
    public void clearQueue() {
        template.sendBodyAndHeader(ironMQEndpoint, "fo", IronMQConstants.OPERATION, IronMQConstants.CLEARQUEUE);
        for (int i = 1; i <= 50; i++) {
            template.sendBody(ironMQEndpoint, "<foo>" + i + "</foo>");
        }
    }

    @Test
    public void testIronMQFifo() throws Exception {
        result.setExpectedMessageCount(50);
        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);
        int i = 1;
        List<Exchange> exchanges = result.getExchanges();
        for (Exchange exchange : exchanges) {
            assertEquals("<foo>" + i + "</foo>", exchange.getIn().getBody(String.class));
            i++;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from(ironMQEndpoint).log("got message ${body}").to("mock:result");
            }
        };
    }
}
