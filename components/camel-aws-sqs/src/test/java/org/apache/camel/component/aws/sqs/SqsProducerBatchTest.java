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
package org.apache.camel.component.aws.sqs;

import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.sqs.model.SendMessageBatchResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SqsProducerBatchTest extends CamelTestSupport {

    @BindToRegistry("client")
    AmazonSQSClientMock mock = new AmazonSQSClientMock();

    @EndpointInject("direct:start")
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void sendBatchMessage() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:start", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection c = new ArrayList<Integer>();
                c.add("team1");
                c.add("team2");
                c.add("team3");
                c.add("team4");
                exchange.getIn().setBody(c);
            }
        });
        assertMockEndpointsSatisfied();
        SendMessageBatchResult res = result.getExchanges().get(0).getIn().getBody(SendMessageBatchResult.class);
        assertEquals(2, res.getFailed().size());
        assertEquals(2, res.getSuccessful().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("aws-sqs://camel-1?amazonSQSClient=#client&operation=sendBatchMessage").to("mock:result");
            }
        };
    }

}
