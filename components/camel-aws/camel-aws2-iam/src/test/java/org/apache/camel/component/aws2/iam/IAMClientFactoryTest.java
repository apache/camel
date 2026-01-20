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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.IamClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IAMClientFactoryTest {

    @Test
    public void getIamClientWithDefaultCredentials() {
        IAM2Configuration configuration = new IAM2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("aws-global");
        IamClient iamClient = IAM2ClientFactory.getIamClient(configuration);
        assertNotNull(iamClient);
        iamClient.close();
    }

    @Test
    public void getIamClientWithStaticCredentials() {
        IAM2Configuration configuration = new IAM2Configuration();
        configuration.setAccessKey("testAccessKey");
        configuration.setSecretKey("testSecretKey");
        configuration.setRegion("aws-global");
        IamClient iamClient = IAM2ClientFactory.getIamClient(configuration);
        assertNotNull(iamClient);
        iamClient.close();
    }

    @Test
    public void getIamClientWithEndpointOverride() {
        IAM2Configuration configuration = new IAM2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("aws-global");
        configuration.setOverrideEndpoint(true);
        configuration.setUriEndpointOverride("http://localhost:4566");
        IamClient iamClient = IAM2ClientFactory.getIamClient(configuration);
        assertNotNull(iamClient);
        iamClient.close();
    }
}
