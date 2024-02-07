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
package org.apache.camel.component.aws2.iam.client;

import org.apache.camel.component.aws2.iam.IAM2Configuration;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientOptimizedImpl;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientProfileOptimizedImpl;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientSessionTokenImpl;
import org.apache.camel.component.aws2.iam.client.impl.IAM2ClientStandardImpl;

/**
 * Factory class to return the correct type of AWS IAM client.
 */
public final class IAM2ClientFactory {

    private IAM2ClientFactory() {
    }

    /**
     * Return the correct AWS IAM client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               IamClient
     */
    public static IAM2InternalClient getIamClient(IAM2Configuration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new IAM2ClientOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new IAM2ClientProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new IAM2ClientSessionTokenImpl(configuration);
        } else {
            return new IAM2ClientStandardImpl(configuration);
        }
    }
}
