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
package org.apache.camel.component.aws2.ecs;

import org.apache.camel.component.aws2.ecs.client.ECS2ClientFactory;
import org.apache.camel.component.aws2.ecs.client.ECS2InternalClient;
import org.apache.camel.component.aws2.ecs.client.impl.ECS2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.ecs.client.impl.ECS2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.ecs.client.impl.ECS2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ECS2ClientFactoryTest {

    @Test
    public void getStandardECS2ClientDefault() {
        ECS2Configuration ec2Configuration = new ECS2Configuration();
        ECS2InternalClient ec2Client = ECS2ClientFactory.getEcsClient(ec2Configuration);
        assertTrue(ec2Client instanceof ECS2ClientStandardImpl);
    }

    @Test
    public void getStandardECS2Client() {
        ECS2Configuration ec2Configuration = new ECS2Configuration();
        ec2Configuration.setUseDefaultCredentialsProvider(false);
        ECS2InternalClient ec2Client = ECS2ClientFactory.getEcsClient(ec2Configuration);
        assertTrue(ec2Client instanceof ECS2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedECS2Client() {
        ECS2Configuration ec2Configuration = new ECS2Configuration();
        ec2Configuration.setUseDefaultCredentialsProvider(true);
        ECS2InternalClient ec2Client = ECS2ClientFactory.getEcsClient(ec2Configuration);
        assertTrue(ec2Client instanceof ECS2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenECS2Client() {
        ECS2Configuration ec2Configuration = new ECS2Configuration();
        ec2Configuration.setUseSessionCredentials(true);
        ECS2InternalClient ec2Client = ECS2ClientFactory.getEcsClient(ec2Configuration);
        assertTrue(ec2Client instanceof ECS2ClientSessionTokenImpl);
    }
}
