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
package org.apache.camel.component.aws2.ecs;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.ecs.model.CreateClusterResponse;
import software.amazon.awssdk.services.ecs.model.DeleteClusterResponse;
import software.amazon.awssdk.services.ecs.model.DescribeClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ECS2ProducerSpringTest extends CamelSpringTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void kmsListClustersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listClusters", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(ECS2Constants.OPERATION, ECS2Operations.listClusters);
            }
        });

        assertMockEndpointsSatisfied();

        ListClustersResponse resultGet = (ListClustersResponse)exchange.getIn().getBody();
        assertEquals(1, resultGet.clusterArns().size());
        assertEquals("Test", resultGet.clusterArns().get(0));
    }

    @Test
    public void ecsCreateClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(ECS2Constants.OPERATION, ECS2Operations.createCluster);
                exchange.getIn().setHeader(ECS2Constants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateClusterResponse resultGet = (CreateClusterResponse)exchange.getIn().getBody();
        assertEquals("Test", resultGet.cluster().clusterName());
    }

    @Test
    public void eksDescribeClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(ECS2Constants.OPERATION, ECS2Operations.describeCluster);
                exchange.getIn().setHeader(ECS2Constants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DescribeClustersResponse resultGet = exchange.getIn().getBody(DescribeClustersResponse.class);
        assertEquals("Test", resultGet.clusters().get(0).clusterName());
    }

    @Test
    public void eksDeleteClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(ECS2Constants.OPERATION, ECS2Operations.deleteCluster);
                exchange.getIn().setHeader(ECS2Constants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteClusterResponse resultGet = exchange.getIn().getBody(DeleteClusterResponse.class);
        assertEquals("Test", resultGet.cluster().clusterName());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/ecs/ECSComponentSpringTest-context.xml");
    }
}
