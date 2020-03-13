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
package org.apache.camel.component.aws2.mq;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.mq.model.BrokerState;
import software.amazon.awssdk.services.mq.model.ConfigurationId;
import software.amazon.awssdk.services.mq.model.CreateBrokerResponse;
import software.amazon.awssdk.services.mq.model.DeleteBrokerResponse;
import software.amazon.awssdk.services.mq.model.DeploymentMode;
import software.amazon.awssdk.services.mq.model.DescribeBrokerResponse;
import software.amazon.awssdk.services.mq.model.EngineType;
import software.amazon.awssdk.services.mq.model.ListBrokersResponse;
import software.amazon.awssdk.services.mq.model.UpdateBrokerResponse;
import software.amazon.awssdk.services.mq.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MQProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void mqListBrokersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listBrokers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.listBrokers);
            }
        });

        assertMockEndpointsSatisfied();

        ListBrokersResponse resultGet = (ListBrokersResponse)exchange.getIn().getBody();
        assertEquals(1, resultGet.brokerSummaries().size());
        assertEquals("mybroker", resultGet.brokerSummaries().get(0).brokerName());
        assertEquals(BrokerState.RUNNING.toString(), resultGet.brokerSummaries().get(0).brokerState().toString());
    }

    @Test
    public void mqCreateBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.createBroker);
                exchange.getIn().setHeader(MQ2Constants.BROKER_NAME, "test");
                exchange.getIn().setHeader(MQ2Constants.BROKER_DEPLOYMENT_MODE, DeploymentMode.SINGLE_INSTANCE);
                exchange.getIn().setHeader(MQ2Constants.BROKER_INSTANCE_TYPE, "mq.t2.micro");
                exchange.getIn().setHeader(MQ2Constants.BROKER_ENGINE, EngineType.ACTIVEMQ.name());
                exchange.getIn().setHeader(MQ2Constants.BROKER_ENGINE_VERSION, "5.15.6");
                exchange.getIn().setHeader(MQ2Constants.BROKER_PUBLICLY_ACCESSIBLE, false);
                List<User> users = new ArrayList<>();
                User.Builder user = User.builder();
                user.username("camel");
                user.password("camelcamel12");
                users.add(user.build());
                exchange.getIn().setHeader(MQ2Constants.BROKER_USERS, users);
            }
        });

        assertMockEndpointsSatisfied();

        CreateBrokerResponse resultGet = (CreateBrokerResponse)exchange.getIn().getBody();
        assertEquals(resultGet.brokerId(), "1");
        assertEquals(resultGet.brokerArn(), "test");
    }

    @Test
    public void mqDeleteBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.deleteBroker);
                exchange.getIn().setHeader(MQ2Constants.BROKER_ID, "1");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteBrokerResponse resultGet = (DeleteBrokerResponse)exchange.getIn().getBody();
        assertEquals(resultGet.brokerId(), "1");
    }

    @Test
    public void mqRebootBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        template.request("direct:rebootBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.rebootBroker);
                exchange.getIn().setHeader(MQ2Constants.BROKER_ID, "1");
            }
        });

        assertMockEndpointsSatisfied();
    }

    @Test
    public void mqUpdateBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.updateBroker);
                exchange.getIn().setHeader(MQ2Constants.BROKER_ID, "1");
                ConfigurationId.Builder cId = ConfigurationId.builder();
                cId.id("1");
                cId.revision(12);
                exchange.getIn().setHeader(MQ2Constants.CONFIGURATION_ID, cId.build());
            }
        });

        assertMockEndpointsSatisfied();
        UpdateBrokerResponse resultGet = (UpdateBrokerResponse)exchange.getIn().getBody();
        assertEquals(resultGet.brokerId(), "1");
    }

    @Test
    public void mqDescribeBrokerTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeBroker", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MQ2Constants.OPERATION, MQ2Operations.describeBroker);
                exchange.getIn().setHeader(MQ2Constants.BROKER_ID, "1");
                ConfigurationId.Builder cId = ConfigurationId.builder();
                cId.id("1");
                cId.revision(12);
                exchange.getIn().setHeader(MQ2Constants.CONFIGURATION_ID, cId.build());
            }
        });

        assertMockEndpointsSatisfied();
        DescribeBrokerResponse resultGet = (DescribeBrokerResponse)exchange.getIn().getBody();
        assertEquals(resultGet.brokerId(), "1");
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/mq/MQComponentSpringTest-context.xml");
    }
}
