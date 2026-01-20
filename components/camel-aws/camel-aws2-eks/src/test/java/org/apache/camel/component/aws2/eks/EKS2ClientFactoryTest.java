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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.eks.EksClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class EKS2ClientFactoryTest {

    @Test
    public void getEksClientWithDefaultCredentials() {
        EKS2Configuration configuration = new EKS2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        EksClient eksClient = EKS2ClientFactory.getEksClient(configuration);
        assertNotNull(eksClient);
        eksClient.close();
    }

    @Test
    public void getEksClientWithStaticCredentials() {
        EKS2Configuration configuration = new EKS2Configuration();
        configuration.setAccessKey("testAccessKey");
        configuration.setSecretKey("testSecretKey");
        configuration.setRegion("eu-west-1");
        EksClient eksClient = EKS2ClientFactory.getEksClient(configuration);
        assertNotNull(eksClient);
        eksClient.close();
    }

    @Test
    public void getEksClientWithEndpointOverride() {
        EKS2Configuration configuration = new EKS2Configuration();
        configuration.setUseDefaultCredentialsProvider(true);
        configuration.setRegion("eu-west-1");
        configuration.setOverrideEndpoint(true);
        configuration.setUriEndpointOverride("http://localhost:4566");
        EksClient eksClient = EKS2ClientFactory.getEksClient(configuration);
        assertNotNull(eksClient);
        eksClient.close();
    }
}
