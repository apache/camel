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
package org.apache.camel.component.aws2.firehose;

import org.apache.camel.component.aws2.firehose.client.KinesisFirehoseClientFactory;
import org.apache.camel.component.aws2.firehose.client.KinesisFirehoseInternalClient;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientSessionTokenImpl;
import org.apache.camel.component.aws2.firehose.client.impl.KinesisFirehoseClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KinesisFirehoseClientFactoryTest {

    @Test
    public void getStandardFirehoseClientDefault() {
        KinesisFirehose2Configuration kinesis2Configuration = new KinesisFirehose2Configuration();
        KinesisFirehoseInternalClient kinesisFirehoseClient
                = KinesisFirehoseClientFactory.getKinesisFirehoseClient(kinesis2Configuration);
        assertTrue(kinesisFirehoseClient instanceof KinesisFirehoseClientStandardImpl);
    }

    @Test
    public void getStandardFirehoseClient() {
        KinesisFirehose2Configuration kinesis2Configuration = new KinesisFirehose2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(false);
        KinesisFirehoseInternalClient kinesisFirehoseClient
                = KinesisFirehoseClientFactory.getKinesisFirehoseClient(kinesis2Configuration);
        assertTrue(kinesisFirehoseClient instanceof KinesisFirehoseClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedFirehoseClient() {
        KinesisFirehose2Configuration kinesis2Configuration = new KinesisFirehose2Configuration();
        kinesis2Configuration.setUseDefaultCredentialsProvider(true);
        KinesisFirehoseInternalClient kinesisFirehoseClient
                = KinesisFirehoseClientFactory.getKinesisFirehoseClient(kinesis2Configuration);
        assertTrue(kinesisFirehoseClient instanceof KinesisFirehoseClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenFirehoseClient() {
        KinesisFirehose2Configuration kinesis2Configuration = new KinesisFirehose2Configuration();
        kinesis2Configuration.setUseSessionCredentials(true);
        KinesisFirehoseInternalClient kinesisFirehoseClient
                = KinesisFirehoseClientFactory.getKinesisFirehoseClient(kinesis2Configuration);
        assertTrue(kinesisFirehoseClient instanceof KinesisFirehoseClientSessionTokenImpl);
    }
}
