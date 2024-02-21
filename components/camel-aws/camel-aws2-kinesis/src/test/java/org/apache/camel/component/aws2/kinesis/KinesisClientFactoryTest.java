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

import org.apache.camel.component.aws2.kinesis.client.KinesisAsyncInternalClient;
import org.apache.camel.component.aws2.kinesis.client.KinesisClientFactory;
import org.apache.camel.component.aws2.kinesis.client.KinesisInternalClient;
import org.apache.camel.component.aws2.kinesis.client.impl.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KinesisClientFactoryTest {

    @Test
    void getStandardKinesisClientDefault() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        KinesisInternalClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisClientStandardImpl);
    }

    @Test
    void getStandardKinesisClient() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(false);
        KinesisInternalClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisClientStandardImpl);
    }

    @Test
    void getIAMOptimizedKinesisClient() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(true);
        KinesisInternalClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisClientIAMOptimizedImpl);
    }

    @Test
    void getSessionTokenKinesisClient() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseSessionCredentials(true);
        KinesisInternalClient kinesisClient = KinesisClientFactory.getKinesisClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisClientSessionTokenImpl);
    }

    @Test
    void getSessionTokenAsyncKinesisClient() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setUseSessionCredentials(true);
        KinesisAsyncInternalClient kinesisClient = KinesisClientFactory.getKinesisAsyncClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisAsyncClientSessionTokenImpl);
    }

    @Test
    void getStandardKinesisAsyncClient() {
        Kinesis2Configuration kinesis2Configuration = new Kinesis2Configuration();
        kinesis2Configuration.setAsyncClient(true);
        KinesisAsyncInternalClient kinesisClient = KinesisClientFactory.getKinesisAsyncClient(kinesis2Configuration);
        assertTrue(kinesisClient instanceof KinesisAsyncClientStandardImpl);
    }
}
