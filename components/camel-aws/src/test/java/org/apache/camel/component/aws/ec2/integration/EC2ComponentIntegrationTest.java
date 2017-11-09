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
package org.apache.camel.component.aws.ec2.integration;

import java.util.ArrayList;
import java.util.Collection;

import com.amazonaws.services.ec2.model.InstanceType;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.ec2.EC2Constants;
import org.apache.camel.component.aws.ec2.EC2Operations;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Must be manually tested. Provide your own accessKey and secretKey!")
public class EC2ComponentIntegrationTest extends CamelTestSupport {

    @Test
    public void createAndRunInstancesTest() {
        
        template.send("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1); 
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);
            }
        });
    }
    
    @Test
    public void createAndRunInstancesWithSecurityGroupsTest() {
        
        template.send("direct:createAndRun", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1); 
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);  
                Collection<String> secGroups = new ArrayList<String>();
                secGroups.add("secgroup-1");
                secGroups.add("secgroup-2");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_SECURITY_GROUPS, secGroups);
            }
        });
    }
    
    @Test
    public void ec2CreateAndRunTestWithKeyPair() throws Exception {
        
        template.request("direct:createAndRun", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(EC2Constants.OPERATION, EC2Operations.createAndRunInstances);
                exchange.getIn().setHeader(EC2Constants.IMAGE_ID, "ami-fd65ba94");
                exchange.getIn().setHeader(EC2Constants.INSTANCE_TYPE, InstanceType.T2Micro);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MIN_COUNT, 1);
                exchange.getIn().setHeader(EC2Constants.INSTANCE_MAX_COUNT, 1);
                exchange.getIn().setHeader(EC2Constants.INSTANCES_KEY_PAIR, "keypair-1");
            }
        });
    }
    
    @Test
    public void stopInstances() {
        
        template.send("direct:stop", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void startInstances() {
        
        template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void terminateInstances() {
        
        template.send("direct:terminate", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void ec2DescribeInstancesTest() throws Exception {
        
        template.request("direct:describe", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                
            }
        });
    }
    
    @Test
    public void ec2DescribeSpecificInstancesTest() throws Exception {
        
        template.request("direct:describe", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("instance-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void ec2DescribeInstancesStatusTest() throws Exception {
        
        template.request("direct:describeStatus", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                
            }
        });
    }
    
    @Test
    public void ec2DescribeStatusSpecificInstancesTest() throws Exception {
        
        template.request("direct:describeStatus", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void ec2RebootInstancesTest() throws Exception {
        
        template.request("direct:reboot", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void ec2MonitorInstancesTest() throws Exception {
        
        template.request("direct:monitor", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Test
    public void ec2UnmonitorInstancesTest() throws Exception {
        
        template.request("direct:unmonitor", new Processor() {
            
            @Override
            public void process(Exchange exchange) throws Exception {
                Collection<String> l = new ArrayList<>();
                l.add("test-1");
                exchange.getIn().setHeader(EC2Constants.INSTANCES_IDS, l);
            }
        });
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:createAndRun")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=createAndRunInstances");
                from("direct:stop")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=stopInstances");
                from("direct:start")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=startInstances");
                from("direct:terminate")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=terminateInstances");
                from("direct:describe")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=describeInstances");
                from("direct:describeStatus")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=describeInstancesStatus");
                from("direct:reboot")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=rebootInstances");
                from("direct:monitor")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=monitorInstances");
                from("direct:unmonitor")
                        .to("aws-ec2://TestDomain?accessKey=xxxx&secretKey=xxxx&operation=unmonitorInstances");
            }
        };
    }
}
