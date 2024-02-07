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
package org.apache.camel.component.aws2.iam;

import org.apache.camel.component.aws2.iam.client.IAM2ClientFactory;
import org.apache.camel.component.aws2.iam.client.IAM2InternalClient;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientOptimizedImpl;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IAMClientFactoryTest {

    @Test
    public void getStandardEIamClientDefault() {
        IAM2Configuration iam2Configuration = new IAM2Configuration();
        IAM2InternalClient iamClient = IAM2ClientFactory.getIamClient(iam2Configuration);
        assertTrue(iamClient instanceof IAM2ClientStandardImpl);
    }

    @Test
    public void getStandardIamClient() {
        IAM2Configuration iam2Configuration = new IAM2Configuration();
        iam2Configuration.setUseDefaultCredentialsProvider(false);
        IAM2InternalClient iamClient = IAM2ClientFactory.getIamClient(iam2Configuration);
        assertTrue(iamClient instanceof IAM2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedIamClient() {
        IAM2Configuration iam2Configuration = new IAM2Configuration();
        iam2Configuration.setUseDefaultCredentialsProvider(true);
        IAM2InternalClient iamClient = IAM2ClientFactory.getIamClient(iam2Configuration);
        assertTrue(iamClient instanceof IAM2ClientOptimizedImpl);
    }

    @Test
    public void getSessionTokenIamClient() {
        IAM2Configuration iam2Configuration = new IAM2Configuration();
        iam2Configuration.setUseSessionCredentials(true);
        IAM2InternalClient iamClient = IAM2ClientFactory.getIamClient(iam2Configuration);
        assertTrue(iamClient instanceof IAM2ClientSessionTokenImpl);
    }
}
