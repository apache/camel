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
package org.apache.camel.component.aws.cloudtrail;

import org.apache.camel.component.aws.cloudtrail.client.CloudtrailClientFactory;
import org.apache.camel.component.aws.cloudtrail.client.CloudtrailInternalClient;
import org.apache.camel.component.aws.cloudtrail.client.impl.CloudtrailClientIAMOptimizedImpl;
import org.apache.camel.component.aws.cloudtrail.client.impl.CloudtrailClientSessionTokenImpl;
import org.apache.camel.component.aws.cloudtrail.client.impl.CloudtrailClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CloudtrailClientFactoryTest {

    @Test
    public void getStandardCloudtrailClientDefault() {
        CloudtrailConfiguration cloudtrailConf = new CloudtrailConfiguration();
        CloudtrailInternalClient cloudtrailClient = CloudtrailClientFactory.getCloudtrailClient(cloudtrailConf);
        assertTrue(cloudtrailClient instanceof CloudtrailClientStandardImpl);
    }

    @Test
    public void getStandardCloudtrailClient() {
        CloudtrailConfiguration cloudtrailConf = new CloudtrailConfiguration();
        cloudtrailConf.setUseDefaultCredentialsProvider(false);
        CloudtrailInternalClient cloudtrailClient = CloudtrailClientFactory.getCloudtrailClient(cloudtrailConf);
        assertTrue(cloudtrailClient instanceof CloudtrailClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedCloudtrailClient() {
        CloudtrailConfiguration cloudtrailConf = new CloudtrailConfiguration();
        cloudtrailConf.setUseDefaultCredentialsProvider(true);
        CloudtrailInternalClient cloudtrailClient = CloudtrailClientFactory.getCloudtrailClient(cloudtrailConf);
        assertTrue(cloudtrailClient instanceof CloudtrailClientIAMOptimizedImpl);
    }

    @Test
    public void getIAMSessionTokenCloudtrailClient() {
        CloudtrailConfiguration cloudtrailConf = new CloudtrailConfiguration();
        cloudtrailConf.setUseSessionCredentials(true);
        CloudtrailInternalClient cloudtrailClient = CloudtrailClientFactory.getCloudtrailClient(cloudtrailConf);
        assertTrue(cloudtrailClient instanceof CloudtrailClientSessionTokenImpl);
    }
}
