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
package org.apache.camel.component.aws2.kinesis;

import org.apache.camel.component.aws2.kinesis.client.KinesisClientFactory;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KinesisClientFactoryTest {

    @Test
    void getKinesisClientWithDefaultCredentials() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(true);
        kinesis2Configuration.setRegion("eu-west-1");
        KinesisClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertNotNull(kinesisClient);
        kinesisClient.close();
    }

    @Test
    void getKinesisClientWithStaticCredentials() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setAccessKey("testAccessKey");
        kinesis2Configuration.setSecretKey("testSecretKey");
        kinesis2Configuration.setRegion("eu-west-1");
        KinesisClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertNotNull(kinesisClient);
        kinesisClient.close();
    }

    @Test
    void getKinesisAsyncClientWithDefaultCredentials() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(true);
        kinesis2Configuration.setRegion("eu-west-1");
        KinesisAsyncClient kinesisAsyncClient = KinesisClientFactory.getKinesisAsyncClient(kinesis2Configuration);
        assertNotNull(kinesisAsyncClient);
        kinesisAsyncClient.close();
    }

    @Test
    void getKinesisAsyncClientWithStaticCredentials() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setAccessKey("testAccessKey");
        kinesis2Configuration.setSecretKey("testSecretKey");
        kinesis2Configuration.setRegion("eu-west-1");
        KinesisAsyncClient kinesisAsyncClient = KinesisClientFactory.getKinesisAsyncClient(kinesis2Configuration);
        assertNotNull(kinesisAsyncClient);
        kinesisAsyncClient.close();
    }

    @Test
    void getKinesisClientWithEndpointOverride() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(true);
        kinesis2Configuration.setRegion("eu-west-1");
        kinesis2Configuration.setOverrideEndpoint(true);
        kinesis2Configuration.setUriEndpointOverride("http://localhost:4566");
        KinesisClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertNotNull(kinesisClient);
        kinesisClient.close();
    }
}
