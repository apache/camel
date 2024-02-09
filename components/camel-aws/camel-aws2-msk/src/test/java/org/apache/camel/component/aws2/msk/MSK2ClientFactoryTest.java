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
package org.apache.camel.component.aws2.msk;

import org.apache.camel.component.aws2.msk.client.MSK2ClientFactory;
import org.apache.camel.component.aws2.msk.client.MSK2InternalClient;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientOptimizedImpl;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.msk.client.impl.MSK2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MSK2ClientFactoryTest {

    @Test
    public void getStandardMSKClientDefault() {
        MSK2Configuration msk2Configuration = new MSK2Configuration();
        MSK2InternalClient mskClient = MSK2ClientFactory.getKafkaClient(msk2Configuration);
        assertTrue(mskClient instanceof MSK2ClientStandardImpl);
    }

    @Test
    public void getStandardMSKClient() {
        MSK2Configuration msk2Configuration = new MSK2Configuration();
        msk2Configuration.setUseDefaultCredentialsProvider(false);
        MSK2InternalClient mskClient = MSK2ClientFactory.getKafkaClient(msk2Configuration);
        assertTrue(mskClient instanceof MSK2ClientStandardImpl);
    }

    @Test
    public void getMSKOptimizedMSKClient() {
        MSK2Configuration msk2Configuration = new MSK2Configuration();
        msk2Configuration.setUseDefaultCredentialsProvider(true);
        MSK2InternalClient mskClient = MSK2ClientFactory.getKafkaClient(msk2Configuration);
        assertTrue(mskClient instanceof MSK2ClientOptimizedImpl);
    }

    @Test
    public void getMSKSessionTokenClient() {
        MSK2Configuration msk2Configuration = new MSK2Configuration();
        msk2Configuration.setUseSessionCredentials(true);
        MSK2InternalClient mskClient = MSK2ClientFactory.getKafkaClient(msk2Configuration);
        assertTrue(mskClient instanceof MSK2ClientSessionTokenImpl);
    }
}
