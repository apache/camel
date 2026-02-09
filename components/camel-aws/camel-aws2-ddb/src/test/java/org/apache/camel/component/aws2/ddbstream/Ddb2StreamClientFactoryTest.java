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
package org.apache.camel.component.aws2.ddbstream;

import org.apache.camel.component.aws2.ddbstream.client.Ddb2StreamClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Ddb2StreamClientFactoryTest {

    @Test
    public void getDdb2StreamClientWithDefaultCredentials() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setUseDefaultCredentialsProvider(true);
        ddb2StreamConfiguration.setRegion("eu-west-1");
        DynamoDbStreamsClient ddbStreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertNotNull(ddbStreamClient);
        ddbStreamClient.close();
    }

    @Test
    public void getDdb2StreamClientWithStaticCredentials() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setAccessKey("testAccessKey");
        ddb2StreamConfiguration.setSecretKey("testSecretKey");
        ddb2StreamConfiguration.setRegion("eu-west-1");
        DynamoDbStreamsClient ddbStreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertNotNull(ddbStreamClient);
        ddbStreamClient.close();
    }

    @Test
    public void getDdb2StreamClientWithEndpointOverride() {
        Ddb2StreamConfiguration ddb2StreamConfiguration = new Ddb2StreamConfiguration();
        ddb2StreamConfiguration.setUseDefaultCredentialsProvider(true);
        ddb2StreamConfiguration.setRegion("eu-west-1");
        ddb2StreamConfiguration.setOverrideEndpoint(true);
        ddb2StreamConfiguration.setUriEndpointOverride("http://localhost:4566");
        DynamoDbStreamsClient ddbStreamClient = Ddb2StreamClientFactory.getDynamoDBStreamClient(ddb2StreamConfiguration);
        assertNotNull(ddbStreamClient);
        ddbStreamClient.close();
    }
}
