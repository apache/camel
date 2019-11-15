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
package org.apache.camel.component.aws.msk;

import com.amazonaws.services.kafka.model.BrokerNodeGroupInfo;
import com.amazonaws.services.kafka.model.ClusterState;
import com.amazonaws.services.kafka.model.CreateClusterResult;
import com.amazonaws.services.kafka.model.DeleteClusterResult;
import com.amazonaws.services.kafka.model.DescribeClusterResult;
import com.amazonaws.services.kafka.model.ListClustersResult;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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

        ListClustersResult resultGet = (ListClustersResult)exchange.getIn().getBody();
        assertEquals(1, resultGet.getClusterInfoList().size());
        assertEquals("test-kafka", resultGet.getClusterInfoList().get(0).getClusterName());
    }

    @Test
    public void mskCreateClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSKConstants.CLUSTER_NAME, "test-kafka");
                exchange.getIn().setHeader(MSKConstants.CLUSTER_KAFKA_VERSION, "2.1.1");
                exchange.getIn().setHeader(MSKConstants.BROKER_NODES_NUMBER, 2);
                BrokerNodeGroupInfo groupInfo = new BrokerNodeGroupInfo();
                exchange.getIn().setHeader(MSKConstants.BROKER_NODES_GROUP_INFO, groupInfo);
            }
        });

        assertMockEndpointsSatisfied();

        CreateClusterResult resultGet = (CreateClusterResult)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.getClusterName());
        assertEquals(ClusterState.CREATING.name(), resultGet.getState());
    }

    @Test
    public void mskDeleteClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSKConstants.CLUSTER_ARN, "test-kafka");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteClusterResult resultGet = (DeleteClusterResult)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.getClusterArn());
        assertEquals(ClusterState.DELETING.name(), resultGet.getState());
    }
    
    
    @Test
    public void mskDescribeClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(MSKConstants.CLUSTER_ARN, "test-kafka");
            }
        });

        assertMockEndpointsSatisfied();

        DescribeClusterResult resultGet = (DescribeClusterResult)exchange.getIn().getBody();
        assertEquals("test-kafka", resultGet.getClusterInfo().getClusterArn());
        assertEquals(ClusterState.ACTIVE.name(), resultGet.getClusterInfo().getState());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws/msk/MSKComponentSpringTest-context.xml");
    }
}
