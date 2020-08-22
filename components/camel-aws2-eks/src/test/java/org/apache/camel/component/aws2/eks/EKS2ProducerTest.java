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
package org.apache.camel.component.aws2.eks;

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eks.model.CreateClusterResponse;
import software.amazon.awssdk.services.eks.model.DeleteClusterResponse;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;
import software.amazon.awssdk.services.eks.model.ListClustersRequest;
import software.amazon.awssdk.services.eks.model.ListClustersResponse;
import software.amazon.awssdk.services.eks.model.VpcConfigRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EKS2ProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonEksClient")
    AmazonEKSClientMock clientMock = new AmazonEKSClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void eksListClustersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listClusters", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKS2Constants.OPERATION, EKS2Operations.listClusters);
            }
        });

        assertMockEndpointsSatisfied();

        ListClustersResponse resultGet = (ListClustersResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.clusters().size());
        assertEquals("Test", resultGet.clusters().get(0));
    }

    @Test
    public void eksListClustersPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listPojoClusters", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKS2Constants.OPERATION, EKS2Operations.listClusters);
                exchange.getIn().setBody(ListClustersRequest.builder().maxResults(12).build());
            }
        });

        assertMockEndpointsSatisfied();

        ListClustersResponse resultGet = (ListClustersResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.clusters().size());
        assertEquals("Test", resultGet.clusters().get(0));
    }

    @Test
    public void eksCreateClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKS2Constants.OPERATION, EKS2Operations.createCluster);
                exchange.getIn().setHeader(EKS2Constants.CLUSTER_NAME, "Test");
                VpcConfigRequest req = VpcConfigRequest.builder().build();
                exchange.getIn().setHeader(EKS2Constants.VPC_CONFIG, req);
                exchange.getIn().setHeader(EKS2Constants.ROLE_ARN, "arn:aws:eks::123456789012:user/Camel");
            }
        });

        assertMockEndpointsSatisfied();

        CreateClusterResponse resultGet = (CreateClusterResponse) exchange.getIn().getBody();
        assertEquals("Test", resultGet.cluster().name());
    }

    @Test
    public void eksDescribeClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKS2Constants.OPERATION, EKS2Operations.describeCluster);
                exchange.getIn().setHeader(EKS2Constants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DescribeClusterResponse resultGet = exchange.getIn().getBody(DescribeClusterResponse.class);
        assertEquals("Test", resultGet.cluster().name());
    }

    @Test
    public void eksDeleteClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKS2Constants.OPERATION, EKS2Operations.deleteCluster);
                exchange.getIn().setHeader(EKS2Constants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteClusterResponse resultGet = exchange.getIn().getBody(DeleteClusterResponse.class);
        assertEquals("Test", resultGet.cluster().name());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listClusters").to("aws2-eks://test?eksClient=#amazonEksClient&operation=listClusters")
                        .to("mock:result");
                from("direct:listPojoClusters")
                        .to("aws2-eks://test?eksClient=#amazonEksClient&operation=listClusters&pojoRequest=true")
                        .to("mock:result");
                from("direct:createCluster").to("aws2-eks://test?eksClient=#amazonEksClient&operation=createCluster")
                        .to("mock:result");
                from("direct:deleteCluster").to("aws2-eks://test?eksClient=#amazonEksClient&operation=deleteCluster")
                        .to("mock:result");
                from("direct:describeCluster").to("aws2-eks://test?eksClient=#amazonEksClient&operation=describeCluster")
                        .to("mock:result");
            }
        };
    }
}
