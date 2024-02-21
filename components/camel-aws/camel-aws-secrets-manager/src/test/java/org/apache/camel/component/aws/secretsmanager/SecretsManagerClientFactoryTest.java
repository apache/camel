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
package org.apache.camel.component.aws.secretsmanager;

import org.apache.camel.component.aws.secretsmanager.client.SecretsManagerClientFactory;
import org.apache.camel.component.aws.secretsmanager.client.SecretsManagerInternalClient;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientIAMOptimized;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientSessionTokenImpl;
import org.apache.camel.component.aws.secretsmanager.client.impl.SecretsManagerClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecretsManagerClientFactoryTest {

    @Test
    public void getStandardSecretsManagerClientDefault() {
        SecretsManagerConfiguration secretsManagerConfiguration = new SecretsManagerConfiguration();
        SecretsManagerInternalClient secretsManagerClient
                = SecretsManagerClientFactory.getSecretsManagerClient(secretsManagerConfiguration);
        assertTrue(secretsManagerClient instanceof SecretsManagerClientStandardImpl);
    }

    @Test
    public void getStandardSecretsManagerClient() {
        SecretsManagerConfiguration secretsManagerConfiguration = new SecretsManagerConfiguration();
        secretsManagerConfiguration.setUseDefaultCredentialsProvider(false);
        SecretsManagerInternalClient secretsManagerClient
                = SecretsManagerClientFactory.getSecretsManagerClient(secretsManagerConfiguration);
        assertTrue(secretsManagerClient instanceof SecretsManagerClientStandardImpl);
    }

    @Test
    public void getSecretsManagerOptimizedIAMClient() {
        SecretsManagerConfiguration secretsManagerConfiguration = new SecretsManagerConfiguration();
        secretsManagerConfiguration.setUseDefaultCredentialsProvider(true);
        SecretsManagerInternalClient secretsManagerClient
                = SecretsManagerClientFactory.getSecretsManagerClient(secretsManagerConfiguration);
        assertTrue(secretsManagerClient instanceof SecretsManagerClientIAMOptimized);
    }

    @Test
    public void getSecretsManagerSessionTokenClient() {
        SecretsManagerConfiguration secretsManagerConfiguration = new SecretsManagerConfiguration();
        secretsManagerConfiguration.setUseSessionCredentials(true);
        SecretsManagerInternalClient secretsManagerClient
                = SecretsManagerClientFactory.getSecretsManagerClient(secretsManagerConfiguration);
        assertTrue(secretsManagerClient instanceof SecretsManagerClientSessionTokenImpl);
    }
}
