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
package org.apache.camel.component.aws2.kms;

import org.apache.camel.component.aws2.kms.client.KMS2ClientFactory;
import org.apache.camel.component.aws2.kms.client.KMS2InternalClient;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientOptimizedImpl;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class KMSClientFactoryTest {

    @Test
    public void getStandardKMSClientDefault() {
        KMS2Configuration kms2Configuration = new KMS2Configuration();
        KMS2InternalClient kmsClient = KMS2ClientFactory.getKmsClient(kms2Configuration);
        assertTrue(kmsClient instanceof KMS2ClientStandardImpl);
    }

    @Test
    public void getStandardKMSClient() {
        KMS2Configuration kms2Configuration = new KMS2Configuration();
        kms2Configuration.setUseDefaultCredentialsProvider(false);
        KMS2InternalClient kmsClient = KMS2ClientFactory.getKmsClient(kms2Configuration);
        assertTrue(kmsClient instanceof KMS2ClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedKMSClient() {
        KMS2Configuration kms2Configuration = new KMS2Configuration();
        kms2Configuration.setUseDefaultCredentialsProvider(true);
        KMS2InternalClient kmsClient = KMS2ClientFactory.getKmsClient(kms2Configuration);
        assertTrue(kmsClient instanceof KMS2ClientOptimizedImpl);
    }

    @Test
    public void getSessionTokenKMSClient() {
        KMS2Configuration kms2Configuration = new KMS2Configuration();
        kms2Configuration.setUseSessionCredentials(true);
        KMS2InternalClient kmsClient = KMS2ClientFactory.getKmsClient(kms2Configuration);
        assertTrue(kmsClient instanceof KMS2ClientSessionTokenImpl);
    }
}
