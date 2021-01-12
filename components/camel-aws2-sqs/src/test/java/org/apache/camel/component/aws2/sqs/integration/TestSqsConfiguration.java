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

package org.apache.camel.component.aws2.sqs.integration;

import org.apache.camel.component.aws2.sqs.Sqs2Configuration;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.common.SharedNameGenerator;
import org.apache.camel.test.infra.common.SharedNameRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;

public class TestSqsConfiguration extends Sqs2Configuration {
    private static final Logger LOG = LoggerFactory.getLogger(TestSqsConfiguration.class);
    private final SqsClient sqsClient = AWSSDKClientUtils.newSQSClient();

    public TestSqsConfiguration() {
        SharedNameGenerator sharedNameGenerator = SharedNameRegistry.getInstance().getSharedNameGenerator();

        String name = sharedNameGenerator.getName();
        LOG.debug("Using the following shared resource name for the test: {}", name);
        setQueueName(name);
    }

    @Override
    public SqsClient getAmazonSQSClient() {
        return sqsClient;
    }
}
