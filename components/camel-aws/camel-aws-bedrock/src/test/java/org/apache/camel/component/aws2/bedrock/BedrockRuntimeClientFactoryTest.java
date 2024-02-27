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
package org.apache.camel.component.aws2.bedrock;

import org.apache.camel.component.aws2.bedrock.client.BedrockClientFactory;
import org.apache.camel.component.aws2.bedrock.client.BedrockRuntimeInternalClient;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientStandardImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BedrockRuntimeClientFactoryTest {

    @Test
    public void getStandardBedrockClientDefault() {
        BedrockConfiguration bedrockConfiguration = new BedrockConfiguration();
        BedrockRuntimeInternalClient bedrockClient = BedrockClientFactory.getBedrockRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockRuntimeClientStandardImpl);
    }

    @Test
    public void getStandardDefaultBedrockClient() {
        BedrockConfiguration bedrockConfiguration = new BedrockConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(false);
        BedrockRuntimeInternalClient bedrockClient = BedrockClientFactory.getBedrockRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockRuntimeClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedBedrockClient() {
        BedrockConfiguration bedrockConfiguration = new BedrockConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(true);
        BedrockRuntimeInternalClient bedrockClient = BedrockClientFactory.getBedrockRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockRuntimeClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenBedrockClient() {
        BedrockConfiguration bedrockConfiguration = new BedrockConfiguration();
        bedrockConfiguration.setUseSessionCredentials(true);
        BedrockRuntimeInternalClient bedrockClient = BedrockClientFactory.getBedrockRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockRuntimeClientSessionTokenImpl);
    }

    @Test
    public void getProfileBedrockClient() {
        BedrockConfiguration bedrockConfiguration = new BedrockConfiguration();
        bedrockConfiguration.setUseProfileCredentialsProvider(true);
        BedrockRuntimeInternalClient bedrockClient = BedrockClientFactory.getBedrockRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockRuntimeClientIAMProfileOptimizedImpl);
    }
}
