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
import org.apache.camel.component.aws2.lambda.client.Lambda2InternalClient;
import org.apache.camel.component.aws2.lambda.client.impl.Lambda2ClientOptimizedImpl;
import org.apache.camel.component.aws2.lambda.client.impl.Lambda2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.lambda.client.impl.Lambda2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaClientFactoryTest {

    @Test
    public void getStandardLambdaClientDefault() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        Lambda2InternalClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertTrue(lambdaClient instanceof Lambda2ClientStandardImpl);
    }

    @Test
    public void getStandardLambdaClient() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setUseDefaultCredentialsProvider(false);
        Lambda2InternalClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertTrue(lambdaClient instanceof Lambda2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedLambdaClient() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setUseDefaultCredentialsProvider(true);
        Lambda2InternalClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertTrue(lambdaClient instanceof Lambda2ClientOptimizedImpl);
    }

    @Test
    public void getSessionTokenLambdaClient() {
        Lambda2Configuration lambda2Configuration = new Lambda2Configuration();
        lambda2Configuration.setUseSessionCredentials(true);
        Lambda2InternalClient lambdaClient = Lambda2ClientFactory.getLambdaClient(lambda2Configuration);
        assertTrue(lambdaClient instanceof Lambda2ClientSessionTokenImpl);
    }
}
