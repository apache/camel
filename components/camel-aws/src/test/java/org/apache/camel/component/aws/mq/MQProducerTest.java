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
package org.apache.camel.component.aws.mq;

import com.amazonaws.services.mq.model.BrokerState;
import com.amazonaws.services.mq.model.CreateBrokerResult;
import com.amazonaws.services.mq.model.DeleteBrokerResult;
import com.amazonaws.services.mq.model.DeploymentMode;
import com.amazonaws.services.mq.model.ListBrokersResult;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MQProducerTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;
    
    @Test
    public void mqListBrokersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listBrokers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQConstants.OPERATION, MQOperations.listBrokers);
            }
        });

        assertMockEndpointsSatisfied();
        
        ListBrokersResult resultGet = (ListBrokersResult) exchange.getIn().getBody();
        assertEquals(1, resultGet.getBrokerSummaries().size());
        assertEquals("mybroker", resultGet.getBrokerSummaries().get(0).getBrokerName());
        assertEquals(BrokerState.RUNNING.toString(), resultGet.getBrokerSummaries().get(0).getBrokerState());
    }
    
    @Test
    public void mqCreateBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQConstants.OPERATION, MQOperations.createBroker);
                exchange.getIn().setHeader(MQConstants.BROKER_NAME, "test");
                exchange.getIn().setHeader(MQConstants.BROKER_DEPLOYMENT_MODE, DeploymentMode.SINGLE_INSTANCE);
            }
        });
        
        assertMockEndpointsSatisfied();
        
        CreateBrokerResult resultGet = (CreateBrokerResult) exchange.getIn().getBody();
        assertEquals(resultGet.getBrokerId(), "1");
        assertEquals(resultGet.getBrokerArn(), "test");
    }
    
    @Test
    public void mqDeleteBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQConstants.OPERATION, MQOperations.deleteBroker);
                exchange.getIn().setHeader(MQConstants.BROKER_ID, "1");
            }
        });
        
        assertMockEndpointsSatisfied();
        
        DeleteBrokerResult resultGet = (DeleteBrokerResult) exchange.getIn().getBody();
        assertEquals(resultGet.getBrokerId(), "1");
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        AmazonMQClientMock clientMock = new AmazonMQClientMock();
        
        registry.bind("amazonMqClient", clientMock);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listBrokers")
                    .to("aws-mq://test?amazonMqClient=#amazonMqClient&operation=listBrokers")
                    .to("mock:result");
                from("direct:createBroker")
                    .to("aws-mq://test?amazonMqClient=#amazonMqClient&operation=createBroker")
                    .to("mock:result");
                from("direct:deleteBroker")
                    .to("aws-mq://test?amazonMqClient=#amazonMqClient&operation=deleteBroker")
                    .to("mock:result");
            }
        };
    }
}