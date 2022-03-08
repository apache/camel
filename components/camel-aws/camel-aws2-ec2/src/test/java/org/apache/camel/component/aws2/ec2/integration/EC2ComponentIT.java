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
package org.apache.camel.component.aws2.ec2.integration;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ec2.AWS2EC2Constants;
import org.apache.camel.component.aws2.ec2.AWS2EC2Operations;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.model.InstanceType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class EC2ComponentIT extends Aws2EC2Base {

    @Test
    public void createAndRunInstancesTest() {
        assertDoesNotThrow(() -> execCreateAndRun());
    }

    private void execCreateAndRun() {
        template.send("direct:createAndRun", exchange -> {
            exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
        });
    }

    @Test
    public void createAndRunInstancesWithSecurityGroupsTest() {
        assertDoesNotThrow(() -> execCreateAndRunWithSecurityGroups());
    }

    private void execCreateAndRunWithSecurityGroups() {
        template.send("direct:createAndRun", exchange -> {
            exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
            Collection<String> secGroups = new ArrayList<>();
            secGroups.add("secgroup-1");
            secGroups.add("secgroup-2");
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_SECURITY_GROUPS, secGroups);
        });
    }

    @Test
    public void ec2CreateAndRunTestWithKeyPair() {
        assertDoesNotThrow(() -> execCreateAndRunWithKeyPair());
    }

    private void execCreateAndRunWithKeyPair() {
        template.request("direct:createAndRun", exchange -> {
            exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
            exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
            exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR, "keypair-1");
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createAndRun")
                        .to("aws2-ec2://TestDomain?operation=createAndRunInstances");
            }
        };
    }
}
