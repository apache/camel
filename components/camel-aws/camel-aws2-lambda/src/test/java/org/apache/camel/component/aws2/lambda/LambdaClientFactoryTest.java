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
package org.apache.camel.component.aws2.lambda;

import org.apache.camel.component.aws2.lambda.client.Lambda2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.LambdaClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LambdaClientFactoryTest {

    @Test
    public void getLambdaClientWithDefaultCredentials() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setUseDefaultCredentialsProvider(true);
        lambda2Configuration.setRegion("eu-west-1");
        LambdaClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertNotNull(lambdaClient);
        lambdaClient.close();
    }

    @Test
    public void getLambdaClientWithStaticCredentials() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setAccessKey("testAccessKey");
        lambda2Configuration.setSecretKey("testSecretKey");
        lambda2Configuration.setRegion("eu-west-1");
        LambdaClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertNotNull(lambdaClient);
        lambdaClient.close();
    }

    @Test
    public void getLambdaClientWithEndpointOverride() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setUseDefaultCredentialsProvider(true);
        lambda2Configuration.setRegion("eu-west-1");
        lambda2Configuration.setOverrideEndpoint(true);
        lambda2Configuration.setUriEndpointOverride("http://localhost:4566");
        LambdaClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertNotNull(lambdaClient);
        lambdaClient.close();
    }
}
