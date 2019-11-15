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
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class MSKProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonMskClient")
    AmazonMSKClientMock clientMock = new AmazonMSKClientMock();
    
    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void mskListClustersTest() throws Exception {

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
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listClusters").to("aws-msk://test?mskClient=#amazonMskClient&operation=listClusters").to("mock:result");
                from("direct:createCluster").to("aws-msk://test?mskClient=#amazonMskClient&operation=createCluster").to("mock:result");
                from("direct:deleteCluster").to("aws-msk://test?mskClient=#amazonMskClient&operation=deleteCluster").to("mock:result");
                from("direct:describeCluster").to("aws-msk://test?mskClient=#amazonMskClient&operation=describeCluster").to("mock:result");
            }
        };
    }
}
