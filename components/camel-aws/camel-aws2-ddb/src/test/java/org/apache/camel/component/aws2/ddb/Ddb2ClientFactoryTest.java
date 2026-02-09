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
package org.apache.camel.component.aws2.ddb;

import org.apache.camel.component.aws2.ddb.client.Ddb2ClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Ddb2ClientFactoryTest {

    @Test
    public void getDdb2ClientWithDefaultCredentials() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setUseDefaultCredentialsProvider(true);
        ddb2Configuration.setRegion("eu-west-1");
        DynamoDbClient ddbClient = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertNotNull(ddbClient);
        ddbClient.close();
    }

    @Test
    public void getDdb2ClientWithStaticCredentials() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setAccessKey("testAccessKey");
        ddb2Configuration.setSecretKey("testSecretKey");
        ddb2Configuration.setRegion("eu-west-1");
        DynamoDbClient ddbClient = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertNotNull(ddbClient);
        ddbClient.close();
    }

    @Test
    public void getDdb2ClientWithEndpointOverride() {
        Ddb2Configuration ddb2Configuration = new Ddb2Configuration();
        ddb2Configuration.setUseDefaultCredentialsProvider(true);
        ddb2Configuration.setRegion("eu-west-1");
        ddb2Configuration.setOverrideEndpoint(true);
        ddb2Configuration.setUriEndpointOverride("http://localhost:4566");
        DynamoDbClient ddbClient = Ddb2ClientFactory.getDynamoDBClient(ddb2Configuration);
        assertNotNull(ddbClient);
        ddbClient.close();
    }
}
