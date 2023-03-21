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
import org.apache.camel.component.aws2.sqs.client.Sqs2InternalClient;
import org.apache.camel.component.aws2.sqs.client.impl.Sqs2ClientIAMOptimized;
import org.apache.camel.component.aws2.sqs.client.impl.Sqs2ClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.sqs.client.impl.Sqs2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqsClientFactoryTest {

    @Test
    public void getStandardSqsClientDefault() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        Sqs2InternalClient awsssqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertTrue(awsssqsClient instanceof Sqs2ClientStandardImpl);
    }

    @Test
    public void getStandardSqsClient() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(false);
        Sqs2InternalClient awsssqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertTrue(awsssqsClient instanceof Sqs2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedSqsClient() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseDefaultCredentialsProvider(true);
        Sqs2InternalClient awsssqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertTrue(awsssqsClient instanceof Sqs2ClientIAMOptimized);
    }

    @Test
    public void getIAMProfileOptimizedSqsClient() {
        Sqs2Configuration sqsConfiguration = new Sqs2Configuration();
        sqsConfiguration.setUseProfileCredentialsProvider(true);
        Sqs2InternalClient awsssqsClient = Sqs2ClientFactory.getSqsClient(sqsConfiguration);
        assertTrue(awsssqsClient instanceof Sqs2ClientIAMProfileOptimizedImpl);
    }
}
