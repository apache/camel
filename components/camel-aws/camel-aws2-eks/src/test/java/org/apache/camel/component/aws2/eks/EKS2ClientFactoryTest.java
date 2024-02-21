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
package org.apache.camel.component.aws2.eks;

import org.apache.camel.component.aws2.eks.client.EKS2ClientFactory;
import org.apache.camel.component.aws2.eks.client.EKS2InternalClient;
import org.apache.camel.component.aws2.eks.client.impl.EKS2ClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.eks.client.impl.EKS2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.eks.client.impl.EKS2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EKS2ClientFactoryTest {

    @Test
    public void getStandardEKS2ClientDefault() {
        EKS2Configuration eks2Configuration = new EKS2Configuration();
        EKS2InternalClient eks2Client = EKS2ClientFactory.getEksClient(eks2Configuration);
        assertTrue(eks2Client instanceof EKS2ClientStandardImpl);
    }

    @Test
    public void getStandardEKS2Client() {
        EKS2Configuration eks2Configuration = new EKS2Configuration();
        eks2Configuration.setUseDefaultCredentialsProvider(false);
        EKS2InternalClient eks2Client = EKS2ClientFactory.getEksClient(eks2Configuration);
        assertTrue(eks2Client instanceof EKS2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedEKS2Client() {
        EKS2Configuration eks2Configuration = new EKS2Configuration();
        eks2Configuration.setUseDefaultCredentialsProvider(true);
        EKS2InternalClient eks2Client = EKS2ClientFactory.getEksClient(eks2Configuration);
        assertTrue(eks2Client instanceof EKS2ClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenEKS2Client() {
        EKS2Configuration eks2Configuration = new EKS2Configuration();
        eks2Configuration.setUseSessionCredentials(true);
        EKS2InternalClient eks2Client = EKS2ClientFactory.getEksClient(eks2Configuration);
        assertTrue(eks2Client instanceof EKS2ClientSessionTokenImpl);
    }
}
