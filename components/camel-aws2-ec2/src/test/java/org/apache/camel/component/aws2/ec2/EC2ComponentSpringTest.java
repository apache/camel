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
package org.apache.camel.component.aws2.ec2;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.InstanceStateName;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.MonitorInstancesResponse;
import software.amazon.awssdk.services.ec2.model.MonitoringState;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StartInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EC2ComponentSpringTest extends CamelSpringTestSupport {

    @Test
    public void createAndRunInstances() {

        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });

        RunInstancesResponse resultGet = (RunInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.instances().get(0).imageId(), "test-1");
        assertEquals(resultGet.instances().get(0).instanceType(), InstanceType.T2_MICRO);
        assertEquals(resultGet.instances().get(0).instanceId(), "instance-1");
    }

    @Test
    public void ec2CreateAndRunTestWithKeyPair() throws Exception {

        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR, "keypair-1");
            }
        });

        RunInstancesResponse resultGet = (RunInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.instances().get(0).imageId(), "test-1");
        assertEquals(resultGet.instances().get(0).instanceType(), InstanceType.T2_MICRO);
        assertEquals(resultGet.instances().get(0).instanceId(), "instance-1");
        assertEquals(resultGet.instances().get(0).securityGroups().size(), 2);
        assertEquals(resultGet.instances().get(0).securityGroups().get(0).groupId(), "id-3");
        assertEquals(resultGet.instances().get(0).securityGroups().get(1).groupId(), "id-4");
    }

    @Test
    public void startInstances() {

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        StartInstancesResponse resultGet = (StartInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.startingInstances().get(0).instanceId(), "test-1");
        assertEquals(resultGet.startingInstances().get(0).previousState().name(), InstanceStateName.STOPPED);
        assertEquals(resultGet.startingInstances().get(0).currentState().name(), InstanceStateName.RUNNING);
    }

    @Test
    public void stopInstances() {

        Exchange exchange = template.request("direct:stop", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        StopInstancesResponse resultGet = (StopInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.stoppingInstances().get(0).instanceId(), "test-1");
        assertEquals(resultGet.stoppingInstances().get(0).previousState().name(), InstanceStateName.RUNNING);
        assertEquals(resultGet.stoppingInstances().get(0).currentState().name(), InstanceStateName.STOPPED);
    }

    @Test
    public void terminateInstances() {

        Exchange exchange = template.request("direct:terminate", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        TerminateInstancesResponse resultGet = (TerminateInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.terminatingInstances().get(0).instanceId(), "test-1");
        assertEquals(resultGet.terminatingInstances().get(0).previousState().name(), InstanceStateName.RUNNING);
        assertEquals(resultGet.terminatingInstances().get(0).currentState().name(), InstanceStateName.TERMINATED);
    }

    @Test
    public void ec2DescribeSpecificInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:describe", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("instance-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        DescribeInstancesResponse resultGet = (DescribeInstancesResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.reservations().size(), 1);
        assertEquals(resultGet.reservations().get(0).instances().size(), 1);
    }

    @Test
    public void ec2DescribeStatusSpecificInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:describeStatus", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        assertMockEndpointsSatisfied();

        DescribeInstanceStatusResponse resultGet = (DescribeInstanceStatusResponse)exchange.getMessage().getBody();
        assertEquals(resultGet.instanceStatuses().size(), 1);
        assertEquals(resultGet.instanceStatuses().get(0).instanceState().name(), InstanceStateName.RUNNING);
    }

    @Test
    public void ec2RebootInstancesTest() throws Exception {

        template.request("direct:reboot", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2MonitorInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:monitor", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        MonitorInstancesResponse resultGet = (MonitorInstancesResponse)exchange.getMessage().getBody();

        assertEquals(resultGet.instanceMonitorings().size(), 1);
        assertEquals(resultGet.instanceMonitorings().get(0).instanceId(), "test-1");
        assertEquals(resultGet.instanceMonitorings().get(0).monitoring().state(), MonitoringState.ENABLED);
    }

    @Test
    public void ec2UnmonitorInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:unmonitor", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        UnmonitorInstancesResponse resultGet = (UnmonitorInstancesResponse)exchange.getMessage().getBody();

        assertEquals(resultGet.instanceMonitorings().size(), 1);
        assertEquals(resultGet.instanceMonitorings().get(0).instanceId(), "test-1");
        assertEquals(resultGet.instanceMonitorings().get(0).monitoring().state(), MonitoringState.DISABLED);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/ec2/EC2ComponentSpringTest-context.xml");
    }
}
