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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.DeletableItem;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.ReplaceableItem;
import com.amazonaws.services.simpledb.model.UpdateCondition;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class EC2ComponentSpringTest extends CamelSpringTestSupport {
    
    private AmazonEC2ClientMock amazonEc2Client;
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        
        amazonEc2Client = context.getRegistry().lookupByNameAndType("amazonEC2Client", AmazonEC2ClientMock.class);
    }
    
    @Test
    public void createAndRunInstances() {
        
        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.OPERATION, EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });
        
        RunInstancesResult resultGet = (RunInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getReservation().getInstances().get(0).getImageId(), "test-1");
        assertEquals(resultGet.getReservation().getInstances().get(0).getInstanceType(), InstanceType.T2Micro.toString());
        assertEquals(resultGet.getReservation().getInstances().get(0).getInstanceId(), "instance-1");
    }
    
    @Test
    public void startInstances() {
        
        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
                
        StartInstancesResult resultGet = (StartInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getStartingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getStartingInstances().get(0).getPreviousState().getName(), InstanceStateName.Stopped.toString());
        assertEquals(resultGet.getStartingInstances().get(0).getCurrentState().getName(), InstanceStateName.Running.toString());
    }
    
    @Test
    public void stopInstances() {
        
        Exchange exchange = template.request("direct:stop", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
                
        StopInstancesResult resultGet = (StopInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getStoppingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getStoppingInstances().get(0).getPreviousState().getName(), InstanceStateName.Running.toString());
        assertEquals(resultGet.getStoppingInstances().get(0).getCurrentState().getName(), InstanceStateName.Stopped.toString());
    }

    @Test
    public void terminateInstances() {
        
        Exchange exchange = template.request("direct:terminate", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
                
        TerminateInstancesResult resultGet = (TerminateInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getTerminatingInstances().get(0).getInstanceId(), "test-1");
        assertEquals(resultGet.getTerminatingInstances().get(0).getPreviousState().getName(), InstanceStateName.Running.toString());
        assertEquals(resultGet.getTerminatingInstances().get(0).getCurrentState().getName(), InstanceStateName.Terminated.toString());
    }
    
    @Test
    public void ec2DescribeSpecificInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:describe", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("instance-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        DescribeInstancesResult resultGet = (DescribeInstancesResult) exchange.getIn().getBody();
        assertEquals(resultGet.getReservations().size(), 1);
        assertEquals(resultGet.getReservations().get(0).getInstances().size(), 1);
    }
    
    @Test
    public void ec2DescribeStatusSpecificInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:describeStatus", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
        assertMockEndpointsSatisfied();
        
        DescribeInstanceStatusResult resultGet = (DescribeInstanceStatusResult) exchange.getIn().getBody();
        assertEquals(resultGet.getInstanceStatuses().size(), 1);
        assertEquals(resultGet.getInstanceStatuses().get(0).getInstanceState().getName(), InstanceStateName.Running.toString());
    }
    
    @Test
    public void ec2RebootInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:reboot", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection l = new ArrayList();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);   
            }
        });
        
    }
    
    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/component/aws/ec2/EC2ComponentSpringTest-context.xml");
    }
}