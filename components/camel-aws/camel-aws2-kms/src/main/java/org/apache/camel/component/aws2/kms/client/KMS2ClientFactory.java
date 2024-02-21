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
package org.apache.camel.component.aws2.kms.client;

import org.apache.camel.component.aws2.kms.KMS2Configuration;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientOptimizedImpl;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientProfileOptimizedImpl;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.kms.client.impl.KMS2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS KMS client.
 */
public final class KMS2ClientFactory {

    private KMS2ClientFactory() {
    }

    /**
     * Return the correct AWS KMS client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               KMSClient
     */
    public static KMS2InternalClient getKmsClient(KMS2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new KMS2ClientOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new KMS2ClientProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new KMS2ClientSessionTokenImpl(configuration);
        } else {
            return new KMS2ClientStandardImpl(configuration);
        }
    }
}
