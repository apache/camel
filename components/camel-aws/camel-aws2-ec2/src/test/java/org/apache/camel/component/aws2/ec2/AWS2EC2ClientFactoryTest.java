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
import org.apache.camel.component.aws2.ec2.client.AWS2EC2InternalClient;
import org.apache.camel.component.aws2.ec2.client.impl.AWS2EC2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.ec2.client.impl.AWS2EC2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.ec2.client.impl.AWS2EC2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AWS2EC2ClientFactoryTest {

    @Test
    public void getStandardEC2ClientDefault() {
        AWS2EC2Configuration ec2Configuration = new AWS2EC2Configuration();
        AWS2EC2InternalClient ec2Client = AWS2EC2ClientFactory.getEc2Client(ec2Configuration);
        assertTrue(ec2Client instanceof AWS2EC2ClientStandardImpl);
    }

    @Test
    public void getStandardEC2Client() {
        AWS2EC2Configuration ec2Configuration = new AWS2EC2Configuration();
        ec2Configuration.setUseDefaultCredentialsProvider(false);
        AWS2EC2InternalClient ec2Client = AWS2EC2ClientFactory.getEc2Client(ec2Configuration);
        assertTrue(ec2Client instanceof AWS2EC2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedEC2Client() {
        AWS2EC2Configuration ec2Configuration = new AWS2EC2Configuration();
        ec2Configuration.setUseDefaultCredentialsProvider(true);
        AWS2EC2InternalClient ec2Client = AWS2EC2ClientFactory.getEc2Client(ec2Configuration);
        assertTrue(ec2Client instanceof AWS2EC2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenEC2Client() {
        AWS2EC2Configuration ec2Configuration = new AWS2EC2Configuration();
        ec2Configuration.setUseSessionCredentials(true);
        AWS2EC2InternalClient ec2Client = AWS2EC2ClientFactory.getEc2Client(ec2Configuration);
        assertTrue(ec2Client instanceof AWS2EC2ClientSessionTokenImpl);
    }
}
