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
import org.apache.camel.component.mock.MockEndpoint;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EC2ComponentSpringTest extends CamelSpringTestSupport {

    @Test
    public void createAndRunInstances() {

        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });

        RunInstancesResponse resultGet = (RunInstancesResponse) exchange.getMessage().getBody();
        assertEquals("test-1", resultGet.instances().get(0).imageId());
        assertEquals(InstanceType.T2_MICRO, resultGet.instances().get(0).instanceType());
        assertEquals("instance-1", resultGet.instances().get(0).instanceId());
    }

    @Test
    public void ec2CreateAndRunTestWithKeyPair() {

        Exchange exchange = template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR, "keypair-1");
            }
        });

        RunInstancesResponse resultGet = (RunInstancesResponse) exchange.getMessage().getBody();
        assertEquals("test-1", resultGet.instances().get(0).imageId());
        assertEquals(InstanceType.T2_MICRO, resultGet.instances().get(0).instanceType());
        assertEquals("instance-1", resultGet.instances().get(0).instanceId());
        assertEquals(2, resultGet.instances().get(0).securityGroups().size());
        assertEquals("id-3", resultGet.instances().get(0).securityGroups().get(0).groupId());
        assertEquals("id-4", resultGet.instances().get(0).securityGroups().get(1).groupId());
    }

    @Test
    public void startInstances() {

        Exchange exchange = template.request("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        StartInstancesResponse resultGet = (StartInstancesResponse) exchange.getMessage().getBody();
        assertEquals("test-1", resultGet.startingInstances().get(0).instanceId());
        assertEquals(InstanceStateName.STOPPED, resultGet.startingInstances().get(0).previousState().name());
        assertEquals(InstanceStateName.RUNNING, resultGet.startingInstances().get(0).currentState().name());
    }

    @Test
    public void stopInstances() {

        Exchange exchange = template.request("direct:stop", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        StopInstancesResponse resultGet = (StopInstancesResponse) exchange.getMessage().getBody();
        assertEquals("test-1", resultGet.stoppingInstances().get(0).instanceId());
        assertEquals(InstanceStateName.RUNNING, resultGet.stoppingInstances().get(0).previousState().name());
        assertEquals(InstanceStateName.STOPPED, resultGet.stoppingInstances().get(0).currentState().name());
    }

    @Test
    public void terminateInstances() {

        Exchange exchange = template.request("direct:terminate", new Processor() {
            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        TerminateInstancesResponse resultGet = (TerminateInstancesResponse) exchange.getMessage().getBody();
        assertEquals("test-1", resultGet.terminatingInstances().get(0).instanceId());
        assertEquals(InstanceStateName.RUNNING, resultGet.terminatingInstances().get(0).previousState().name());
        assertEquals(InstanceStateName.TERMINATED, resultGet.terminatingInstances().get(0).currentState().name());
    }

    @Test
    public void ec2DescribeSpecificInstancesTest() {

        Exchange exchange = template.request("direct:describe", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("instance-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        DescribeInstancesResponse resultGet = (DescribeInstancesResponse) exchange.getMessage().getBody();
        assertEquals(1, resultGet.reservations().size());
        assertEquals(1, resultGet.reservations().get(0).instances().size());
    }

    @Test
    public void ec2DescribeStatusSpecificInstancesTest() throws Exception {

        Exchange exchange = template.request("direct:describeStatus", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DescribeInstanceStatusResponse resultGet = (DescribeInstanceStatusResponse) exchange.getMessage().getBody();
        assertEquals(1, resultGet.instanceStatuses().size());
        assertEquals(InstanceStateName.RUNNING, resultGet.instanceStatuses().get(0).instanceState().name());
    }

    @Test
    public void ec2RebootInstancesTest() {
        assertDoesNotThrow(() -> issueReboot());
    }

    private void issueReboot() {
        template.request("direct:reboot", exchange -> {
            Collection<String> l = new ArrayList<>();
            l.add("test-1");
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
        });
    }

    @Test
    public void ec2MonitorInstancesTest() {

        Exchange exchange = template.request("direct:monitor", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        MonitorInstancesResponse resultGet = (MonitorInstancesResponse) exchange.getMessage().getBody();

        assertEquals(1, resultGet.instanceMonitorings().size());
        assertEquals("test-1", resultGet.instanceMonitorings().get(0).instanceId());
        assertEquals(MonitoringState.ENABLED, resultGet.instanceMonitorings().get(0).monitoring().state());
    }

    @Test
    public void ec2UnmonitorInstancesTest() {

        Exchange exchange = template.request("direct:unmonitor", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });

        UnmonitorInstancesResponse resultGet = (UnmonitorInstancesResponse) exchange.getMessage().getBody();

        assertEquals(1, resultGet.instanceMonitorings().size());
        assertEquals("test-1", resultGet.instanceMonitorings().get(0).instanceId());
        assertEquals(MonitoringState.DISABLED, resultGet.instanceMonitorings().get(0).monitoring().state());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/aws2/ec2/EC2ComponentSpringTest-context.xml");
    }
}
