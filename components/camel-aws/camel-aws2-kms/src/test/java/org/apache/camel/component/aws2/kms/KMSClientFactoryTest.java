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
package org.apache.camel.component.aws2.kms;

import org.apache.camel.component.aws2.kms.client.KMS2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kms.KmsClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class KMSClientFactoryTest {

    @Test
    public void getKmsClientWithDefaultCredentials() {
        KMS2Configuration configuration = new KMS2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        KmsClient kmsClient = KMS2ClientFactory.getKmsClient(configuration);
        assertNotNull(kmsClient);
        kmsClient.close();
    }

    @Test
    public void getKmsClientWithStaticCredentials() {
        KMS2Configuration configuration = new KMS2Configuration();
        configuration.setAccessKey("testAccessKey");
        configuration.setSecretKey("testSecretKey");
        configuration.setRegion("eu-west-1");
        KmsClient kmsClient = KMS2ClientFactory.getKmsClient(configuration);
        assertNotNull(kmsClient);
        kmsClient.close();
    }

    @Test
    public void getKmsClientWithEndpointOverride() {
        KMS2Configuration configuration = new KMS2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        configuration.setOverrideEndpoint(true);
        configuration.setUriEndpointOverride("http://localhost:4566");
        KmsClient kmsClient = KMS2ClientFactory.getKmsClient(configuration);
        assertNotNull(kmsClient);
        kmsClient.close();
    }
}
