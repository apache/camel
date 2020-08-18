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
package org.apache.camel.component.aws.ec2;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EC2ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalEC2ClientConfiguration() throws Exception {

        AmazonEC2ClientMock clientMock = new AmazonEC2ClientMock();
        context.getRegistry().bind("amazonEc2Client", clientMock);
        EC2Component component = context.getComponent("aws-ec2", EC2Component.class);
        EC2Endpoint endpoint = (EC2Endpoint) component.createEndpoint("aws-ec2://TestDomain");

        assertNotNull(endpoint.getConfiguration().getAmazonEc2Client());
    }

    @Test
    public void createEndpointWithMinimalEC2ClientMisconfiguration() throws Exception {

        EC2Component component = context.getComponent("aws-ec2", EC2Component.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-ec2://TestDomain"));
    }

    @Test
    public void createEndpointWithAutoDiscoverClientFalse() throws Exception {

        AmazonEC2ClientMock clientMock = new AmazonEC2ClientMock();
        context.getRegistry().bind("amazonEc2Client", clientMock);
        EC2Component component = context.getComponent("aws-ec2", EC2Component.class);
        EC2Endpoint endpoint = (EC2Endpoint) component
                .createEndpoint("aws-ec2://TestDomain?accessKey=xxx&secretKey=yyy&autoDiscoverClient=false");

        assertNotSame(clientMock, endpoint.getConfiguration().getAmazonEc2Client());
    }

    @Test
    public void createEndpointWithAutoDiscoverClientTrue() throws Exception {

        AmazonEC2ClientMock clientMock = new AmazonEC2ClientMock();
        context.getRegistry().bind("amazonEc2Client", clientMock);
        EC2Component component = context.getComponent("aws-ec2", EC2Component.class);
        EC2Endpoint endpoint = (EC2Endpoint) component.createEndpoint("aws-ec2://TestDomain?accessKey=xxx&secretKey=yyy");

        assertNotNull(endpoint.getConfiguration().getAmazonEc2Client());
        assertSame(clientMock, endpoint.getConfiguration().getAmazonEc2Client());
    }
}
