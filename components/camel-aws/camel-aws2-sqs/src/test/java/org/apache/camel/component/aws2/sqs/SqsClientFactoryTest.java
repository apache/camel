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
package org.apache.camel.component.aws2.sqs;

import org.apache.camel.component.aws2.sqs.client.Sqs2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class SqsClientFactoryTest {

    @Test
    public void getSqsClientWithDefaultCredentials() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(true);
        sqsConfiguration.setRegion("eu-west-1");
        SqsClient sqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertNotNull(sqsClient);
        sqsClient.close();
    }

    @Test
    void getSqsClientsWithDefaultCredentialsDoNotShareTheCredentialsProvider() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(true);
        sqsConfiguration.setRegion("eu-west-1");
        SqsClient first = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        SqsClient second = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        IdentityProvider<?> firstProvider = first.serviceClientConfiguration().credentialsProvider();
        IdentityProvider<?> secondProvider = second.serviceClientConfiguration().credentialsProvider();
        // a client closes its credentials provider when it is closed, so a provider shared by several
        // clients would break all of them as soon as one endpoint is stopped
        assertInstanceOf(DefaultCredentialsProvider.class, firstProvider);
        assertNotSame(firstProvider, secondProvider);
        first.close();
        second.close();
    }

    @Test
    public void getSqsClientWithStaticCredentials() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setAccessKey("testAccessKey");
        sqsConfiguration.setSecretKey("testSecretKey");
        sqsConfiguration.setRegion("eu-west-1");
        SqsClient sqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertNotNull(sqsClient);
        sqsClient.close();
    }

    @Test
    public void getSqsClientWithEndpointOverride() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(true);
        sqsConfiguration.setRegion("eu-west-1");
        sqsConfiguration.setOverrideEndpoint(true);
        sqsConfiguration.setUriEndpointOverride("http://localhost:4566");
        SqsClient sqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertNotNull(sqsClient);
        sqsClient.close();
    }

    @Test
    public void getSqsClientWithCustomHost() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(true);
        sqsConfiguration.setRegion("eu-west-1");
        sqsConfiguration.setAmazonAWSHost("localhost:4566");
        sqsConfiguration.setProtocol("http");
        SqsClient sqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertNotNull(sqsClient);
        sqsClient.close();
    }
}
