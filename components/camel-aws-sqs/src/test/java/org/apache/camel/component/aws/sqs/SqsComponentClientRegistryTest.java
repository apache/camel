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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SqsComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalSQSClientConfiguration() throws Exception {

        AmazonSQSClientMock awsSQSClient = new AmazonSQSClientMock();
        context.getRegistry().bind("awsSQSClient", awsSQSClient);
        SqsComponent component = context.getComponent("aws-sqs", SqsComponent.class);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue");

        assertNotNull(endpoint.getConfiguration().getAmazonSQSClient());
    }

    @Test
    public void createEndpointWithMinimalSQSClientMisconfiguration() throws Exception {

        SqsComponent component = context.getComponent("aws-sqs", SqsComponent.class);
        assertThrows(IllegalArgumentException.class,
                () -> component.createEndpoint("aws-sqs://MyQueue"));
    }

    @Test
    public void createEndpointWithAutoDiscoverClientFalse() throws Exception {

        AmazonSQSClientMock awsSQSClient = new AmazonSQSClientMock();
        context.getRegistry().bind("awsSQSClient", awsSQSClient);
        SqsComponent component = context.getComponent("aws-sqs", SqsComponent.class);
        SqsEndpoint endpoint = (SqsEndpoint) component
                .createEndpoint("aws-sqs://MyQueue?accessKey=xxx&secretKey=yyy&autoDiscoverClient=false");

        assertNotSame(awsSQSClient, endpoint.getConfiguration().getAmazonSQSClient());
    }

    @Test
    public void createEndpointWithAutoDiscoverClientTrue() throws Exception {

        AmazonSQSClientMock awsSQSClient = new AmazonSQSClientMock();
        context.getRegistry().bind("awsSQSClient", awsSQSClient);
        SqsComponent component = context.getComponent("aws-sqs", SqsComponent.class);
        SqsEndpoint endpoint = (SqsEndpoint) component.createEndpoint("aws-sqs://MyQueue?accessKey=xxx&secretKey=yyy");

        assertSame(awsSQSClient, endpoint.getConfiguration().getAmazonSQSClient());
    }
}
