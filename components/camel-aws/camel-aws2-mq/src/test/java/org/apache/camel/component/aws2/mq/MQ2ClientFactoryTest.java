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
package org.apache.camel.component.aws2.mq;

import org.apache.camel.component.aws2.mq.client.MQ2ClientFactory;
import org.apache.camel.component.aws2.mq.client.MQ2InternalClient;
import org.apache.camel.component.aws2.mq.client.impl.MQ2ClientOptimizedImpl;
import org.apache.camel.component.aws2.mq.client.impl.MQ2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MQ2ClientFactoryTest {

    @Test
    public void getStandardMQClientDefault() {
        MQ2Configuration mq2Configuration = new MQ2Configuration();
        MQ2InternalClient mqClient = MQ2ClientFactory.getMqClient(mq2Configuration);
        assertTrue(mqClient instanceof MQ2ClientStandardImpl);
    }

    @Test
    public void getStandardMQClient() {
        MQ2Configuration mq2Configuration = new MQ2Configuration();
        mq2Configuration.setUseDefaultCredentialsProvider(false);
        MQ2InternalClient mqClient = MQ2ClientFactory.getMqClient(mq2Configuration);
        assertTrue(mqClient instanceof MQ2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedMQClient() {
        MQ2Configuration mq2Configuration = new MQ2Configuration();
        mq2Configuration.setUseDefaultCredentialsProvider(true);
        MQ2InternalClient mqClient = MQ2ClientFactory.getMqClient(mq2Configuration);
        assertTrue(mqClient instanceof MQ2ClientOptimizedImpl);
    }

    @Test
    public void getSessionTokenMQClient() {
        MQ2Configuration mq2Configuration = new MQ2Configuration();
        mq2Configuration.setUseDefaultCredentialsProvider(true);
        MQ2InternalClient mqClient = MQ2ClientFactory.getMqClient(mq2Configuration);
        assertTrue(mqClient instanceof MQ2ClientOptimizedImpl);
    }
}
