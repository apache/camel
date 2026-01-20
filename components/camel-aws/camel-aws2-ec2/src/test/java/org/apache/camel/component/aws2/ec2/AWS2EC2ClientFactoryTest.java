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

import org.apache.camel.component.aws2.ec2.client.AWS2EC2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AWS2EC2ClientFactoryTest {

    @Test
    public void getEc2ClientWithDefaultCredentials() {
        AWS2EC2Configuration configuration = new AWS2EC2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        Ec2Client ec2Client = AWS2EC2ClientFactory.getEc2Client(configuration);
        assertNotNull(ec2Client);
        ec2Client.close();
    }

    @Test
    public void getEc2ClientWithStaticCredentials() {
        AWS2EC2Configuration configuration = new AWS2EC2Configuration();
        configuration.setAccessKey("testAccessKey");
        configuration.setSecretKey("testSecretKey");
        configuration.setRegion("eu-west-1");
        Ec2Client ec2Client = AWS2EC2ClientFactory.getEc2Client(configuration);
        assertNotNull(ec2Client);
        ec2Client.close();
    }

    @Test
    public void getEc2ClientWithEndpointOverride() {
        AWS2EC2Configuration configuration = new AWS2EC2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        configuration.setOverrideEndpoint(true);
        configuration.setUriEndpointOverride("http://localhost:4566");
        Ec2Client ec2Client = AWS2EC2ClientFactory.getEc2Client(configuration);
        assertNotNull(ec2Client);
        ec2Client.close();
    }
}
