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
package org.apache.camel.component.aws.ec2;

import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class EC2ProducerTest extends CamelTestSupport {
    
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;
    
    @Test
    public void ec2CreateAndRunTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.OPERATION, EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });
        
        assertMockEndpointsSatisfied();
        
        RunInstancesResult resultGet = (RunInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getReservation().getInstances().get(0).getImageId(), "test-1");
        assertEquals(resultGet.getReservation().getInstances().get(0).getInstanceType(), InstanceType.T2Micro.toString());
        assertEquals(resultGet.getReservation().getInstances().get(0).getInstanceId(), "instance-1");
    }
    
    @Test
    public void ec2CreateAndRunKoTest() throws Exception {

        mock.expectedMessageCount(0);
        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.OPERATION, EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "test-2");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });
        
        assertMockEndpointsSatisfied();
    }
    
    
    @Test
    public void ec2StartTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        assertMockEndpointsSatisfied();
        
        StartInstancesResult resultGet = (StartInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getStartingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getStartingInstances().get(0).getPreviousState().getName(), InstanceStateName.Stopped.toString());
        assertEquals(resultGet.getStartingInstances().get(0).getCurrentState().getName(), InstanceStateName.Running.toString());
    }
    
    @Test
    public void ec2StopTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:stop", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        assertMockEndpointsSatisfied();
        
        StopInstancesResult resultGet = (StopInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getStoppingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getStoppingInstances().get(0).getPreviousState().getName(), InstanceStateName.Running.toString());
        assertEquals(resultGet.getStoppingInstances().get(0).getCurrentState().getName(), InstanceStateName.Stopped.toString());
    }

    
    @Test
    public void ec2TerminateTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:terminate", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        assertMockEndpointsSatisfied();
        
        TerminateInstancesResult resultGet = (TerminateInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getTerminatingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getTerminatingInstances().get(0).getPreviousState().getName(), InstanceStateName.Running.toString());
        assertEquals(resultGet.getTerminatingInstances().get(0).getCurrentState().getName(), InstanceStateName.Terminated.toString());
    }
    
    @Test
    public void ec2DescribeInstancesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describe", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                
            }
        });
        
        assertMockEndpointsSatisfied();
        
        DescribeInstancesResult resultGet = (DescribeInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getReservations().size(), 1);
        assertEquals(resultGet.getReservations().get(0).getInstances().size(), 2);
    }
    
    @Test
    public void ec2DescribeSpecificInstancesTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:describe", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("instance-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        assertMockEndpointsSatisfied();
        
        DescribeInstancesResult resultGet = (DescribeInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getReservations().size(), 1);
        assertEquals(resultGet.getReservations().get(0).getInstances().size(), 1);
    }
    
    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        
        AmazonEC2ClientMock clientMock = new AmazonEC2ClientMock();
        
        registry.bind("amazonEc2Client", clientMock);
        
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createAndRun")
                    .to("aws-ec2://test?amazonEc2Client=#amazonEc2Client&operation=createAndRunInstances")
                    .to("mock:result");
                from("direct:start")
                    .to("aws-ec2://test?amazonEc2Client=#amazonEc2Client&operation=startInstances")
                    .to("mock:result");
                from("direct:stop")
                    .to("aws-ec2://test?amazonEc2Client=#amazonEc2Client&operation=stopInstances")
                    .to("mock:result");
                from("direct:terminate")
                    .to("aws-ec2://test?amazonEc2Client=#amazonEc2Client&operation=terminateInstances")
                    .to("mock:result");
                from("direct:describe")
                    .to("aws-ec2://test?amazonEc2Client=#amazonEc2Client&operation=describeInstances")
                    .to("mock:result");
            }
        };
    }
}