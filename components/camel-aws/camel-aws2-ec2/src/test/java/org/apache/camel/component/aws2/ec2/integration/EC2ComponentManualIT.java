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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.ec2.AWS2EC2Constants;
import org.apache.camel.component.aws2.ec2.AWS2EC2Operations;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.services.ec2.model.InstanceType;

// Must be manually tested. Provide your own accessKey and secretKey using -Daws.manual.access.key and -Daws.manual.secret.key
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "aws.manual.access.key", matches = ".*", disabledReason = "Access key not provided"),
        @EnabledIfSystemProperty(named = "aws.manual.secret.key", matches = ".*", disabledReason = "Secret key not provided")
})
public class EC2ComponentManualIT extends CamelTestSupport {

    @Test
    public void createAndRunInstancesTest() {

        template.send("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });
    }

    @Test
    public void createAndRunInstancesWithSecurityGroupsTest() {

        template.send("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
                Collection<String> secGroups = new ArrayList<>();
                secGroups.add("secgroup-1");
                secGroups.add("secgroup-2");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_SECURITY_GROUPS, secGroups);
            }
        });
    }

    @Test
    public void ec2CreateAndRunTestWithKeyPair() {

        template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(AWS2EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.T2_MICRO);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, 1);
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR, "keypair-1");
            }
        });
    }

    @Test
    public void stopInstances() {

        template.send("direct:stop", new Processor() {
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void startInstances() {

        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void terminateInstances() {

        template.send("direct:terminate", new Processor() {
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2DescribeInstancesTest() {

        template.request("direct:describe", new Processor() {

            @Override
            public void process(Exchange exchange) {

            }
        });
    }

    @Test
    public void ec2DescribeSpecificInstancesTest() {

        template.request("direct:describe", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("instance-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2DescribeInstancesStatusTest() {

        template.request("direct:describeStatus", new Processor() {

            @Override
            public void process(Exchange exchange) {

            }
        });
    }

    @Test
    public void ec2DescribeStatusSpecificInstancesTest() {

        template.request("direct:describeStatus", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2RebootInstancesTest() {

        template.request("direct:reboot", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2MonitorInstancesTest() {

        template.request("direct:monitor", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Test
    public void ec2UnmonitorInstancesTest() {

        template.request("direct:unmonitor", new Processor() {

            @Override
            public void process(Exchange exchange) {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(AWS2EC2Constants.INSTANCES_IDS, l);
            }
        });
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createAndRun")
                        .to("aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=createAndRunInstances");
                from("direct:stop").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=stopInstances");
                from("direct:start").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=startInstances");
                from("direct:terminate").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=terminateInstances");
                from("direct:describe").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=describeInstances");
                from("direct:describeStatus")
                        .to("aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=describeInstancesStatus");
                from("direct:reboot").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=rebootInstances");
                from("direct:monitor").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=monitorInstances");
                from("direct:unmonitor").to(
                        "aws2-ec2://TestDomain?accessKey={{aws.manual.access.key}}&secretKey={{aws.manual.secret.key}}&operation=unmonitorInstances");
            }
        };
    }
}
