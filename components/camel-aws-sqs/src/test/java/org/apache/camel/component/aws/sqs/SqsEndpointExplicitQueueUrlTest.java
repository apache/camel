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
package org.apache.camel.component.aws.sqs;

import com.amazonaws.services.sqs.AmazonSQSClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SqsEndpointExplicitQueueUrlTest extends Assert {

    private static final String QUEUE_URL = "http://localhost:9324/queue/default";
    private SqsEndpoint endpoint;
    private AmazonSQSClient amazonSQSClient;

    @Before
    public void setUp() {
        amazonSQSClient = Mockito.mock(AmazonSQSClient.class);

        SqsConfiguration config = new SqsConfiguration();
        config.setQueueUrl(QUEUE_URL);
        config.setAmazonSQSClient(amazonSQSClient);

        endpoint = new SqsEndpoint("aws-sqs://test-queue", new SqsComponent(new DefaultCamelContext()), config);
    }

    @Test
    public void doStartWithExplicitQueueUrlInConfigShouldNotCallSqsClientListQueues() throws Exception {
        endpoint.doInit();

        assertEquals(endpoint.getQueueUrl(), QUEUE_URL);
    }
}
