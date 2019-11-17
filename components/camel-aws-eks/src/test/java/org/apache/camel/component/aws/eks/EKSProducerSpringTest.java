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
package org.apache.camel.component.aws.eks;

import com.amazonaws.services.eks.model.CreateClusterResult;
import com.amazonaws.services.eks.model.DeleteClusterResult;
import com.amazonaws.services.eks.model.DescribeClusterResult;
import com.amazonaws.services.eks.model.ListClustersResult;
import com.amazonaws.services.eks.model.VpcConfigRequest;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class EKSProducerSpringTest extends CamelSpringTestSupport {
    
    @EndpointInject("mock:result")
    private MockEndpoint mock;
    
    @Test
    public void kmsListClustersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listClusters", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKSConstants.OPERATION, EKSOperations.listClusters);
            }
        });

        assertMockEndpointsSatisfied();
        
        ListClustersResult resultGet = (ListClustersResult) exchange.getIn().getBody();
        assertEquals(1, resultGet.getClusters().size());
        assertEquals("Test", resultGet.getClusters().get(0));
    }
    
    @Test
    public void eksCreateClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKSConstants.OPERATION, EKSOperations.createCluster);
                exchange.getIn().setHeader(EKSConstants.CLUSTER_NAME, "Test");
                VpcConfigRequest req = new VpcConfigRequest();
                exchange.getIn().setHeader(EKSConstants.VPC_CONFIG, req);
                exchange.getIn().setHeader(EKSConstants.ROLE_ARN, "arn:aws:eks::123456789012:user/Camel");
            }
        });

        assertMockEndpointsSatisfied();
        
        CreateClusterResult resultGet = (CreateClusterResult) exchange.getIn().getBody();
        assertEquals("Test", resultGet.getCluster().getName());
    }
    
    @Test
    public void eksDescribeClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describeCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKSConstants.OPERATION, EKSOperations.describeCluster);
                exchange.getIn().setHeader(EKSConstants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();
        
        DescribeClusterResult resultGet = exchange.getIn().getBody(DescribeClusterResult.class);
        assertEquals("Test", resultGet.getCluster().getName());
    }
    
    @Test
    public void eksDeleteClusterTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteCluster", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EKSConstants.OPERATION, EKSOperations.deleteCluster);
                exchange.getIn().setHeader(EKSConstants.CLUSTER_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();
        
        DeleteClusterResult resultGet = exchange.getIn().getBody(DeleteClusterResult.class);
        assertEquals("Test", resultGet.getCluster().getName());
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws/eks/EKSComponentSpringTest-context.xml");
    }
}
