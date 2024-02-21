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
package org.apache.camel.component.aws.secretsmanager.client;

import org.apache.camel.component.aws.secretsmanager.SecretsManagerConfiguration;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientIAMOptimized;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientIAMProfileOptimized;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientSessionTokenImpl;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Secrets Manager aws.
 */
public final class SecretsManagerClientFactory {

    private SecretsManagerClientFactory() {
    }

    /**
     * Return the correct aws Secrets Manager client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               SecretsManagerClient
     */
    public static SecretsManagerInternalClient getSecretsManagerClient(SecretsManagerConfiguration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new SecretsManagerClientIAMOptimized(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new SecretsManagerClientIAMProfileOptimized(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new SecretsManagerClientSessionTokenImpl(configuration);
        } else {
            return new SecretsManagerClientStandardImpl(configuration);
        }
    }
}
