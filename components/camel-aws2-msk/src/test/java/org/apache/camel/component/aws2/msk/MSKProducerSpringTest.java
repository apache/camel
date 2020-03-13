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
package org.apache.camel.component.aws2.msk;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.kafka.model.BrokerNodeGroupInfo;
import software.amazon.awssdk.services.kafka.model.ClusterState;
import software.amazon.awssdk.services.kafka.model.CreateClusterResponse;
import software.amazon.awssdk.services.kafka.model.DeleteClusterResponse;
import software.amazon.awssdk.services.kafka.model.DescribeClusterResponse;
import software.amazon.awssdk.services.kafka.model.ListClustersResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MSKProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void mskListKeysTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listClusters", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertMockEndpointsSatisfied();

        ListClustersResponse resultGet = (ListClustersResponse)exchange.getIn().getBody();
        assertEquals(1, resultGet.clusterInfoList().size());
        assertEquals("test-kafka", resultGet.clusterInfoList().get(0).clusterName());
    }

    @Test
    public void mskCreateClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSK2Constants.CLUSTER_NAME, "test-kafka");
                exchange.getIn().setHeader(MSK2Constants.CLUSTER_KAFKA_VERSION, "2.1.1");
                exchange.getIn().setHeader(MSK2Constants.BROKER_NODES_NUMBER, 2);
                BrokerNodeGroupInfo groupInfo = BrokerNodeGroupInfo.builder().build();
                exchange.getIn().setHeader(MSK2Constants.BROKER_NODES_GROUP_INFO, groupInfo);
            }
        });

        assertMockEndpointsSatisfied();

        CreateClusterResponse resultGet = (CreateClusterResponse)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.clusterName());
        assertEquals(ClusterState.CREATING.name(), resultGet.state().toString());
    }

    @Test
    public void mskDeleteClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSK2Constants.CLUSTER_ARN, "test-kafka");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteClusterResponse resultGet = (DeleteClusterResponse)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.clusterArn());
        assertEquals(ClusterState.DELETING.name(), resultGet.state().toString());
    }

    @Test
    public void mskDescribeClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSK2Constants.CLUSTER_ARN, "test-kafka");
            }
        });

        assertMockEndpointsSatisfied();

        DescribeClusterResponse resultGet = (DescribeClusterResponse)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.clusterInfo().clusterArn());
        assertEquals(ClusterState.ACTIVE.name(), resultGet.clusterInfo().state().toString());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/msk/MSKComponentSpringTest-context.xml");
    }
}
