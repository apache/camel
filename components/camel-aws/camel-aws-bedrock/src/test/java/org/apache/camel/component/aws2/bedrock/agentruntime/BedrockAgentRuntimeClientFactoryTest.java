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

package org.apache.camel.component.aws2.bedrock.agentruntime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.component.aws2.bedrock.agentruntime.client.BedrockAgentRuntimeClientFactory;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.BedrockAgentRuntimeInternalClient;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientStandardImpl;
import org.junit.jupiter.api.Test;

public class BedrockAgentRuntimeClientFactoryTest {

    @Test
    public void getStandardBedrockAgentRuntimeClientDefault() {
        BedrockAgentRuntimeConfiguration bedrockConfiguration = new BedrockAgentRuntimeConfiguration();
        BedrockAgentRuntimeInternalClient bedrockClient =
                BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentRuntimeClientStandardImpl);
    }

    @Test
    public void getStandardDefaultBedrockAgentRuntimeClient() {
        BedrockAgentRuntimeConfiguration bedrockConfiguration = new BedrockAgentRuntimeConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(false);
        BedrockAgentRuntimeInternalClient bedrockClient =
                BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentRuntimeClientStandardImpl);
    }

    @Test
    public void getIAMOptimizedBedrockAgentRuntimeClient() {
        BedrockAgentRuntimeConfiguration bedrockConfiguration = new BedrockAgentRuntimeConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(true);
        BedrockAgentRuntimeInternalClient bedrockClient =
                BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentRuntimeClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionTokenBedrockAgentRuntimeClient() {
        BedrockAgentRuntimeConfiguration bedrockConfiguration = new BedrockAgentRuntimeConfiguration();
        bedrockConfiguration.setUseSessionCredentials(true);
        BedrockAgentRuntimeInternalClient bedrockClient =
                BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentRuntimeClientSessionTokenImpl);
    }

    @Test
    public void getProfileBedrockAgentRuntimeClient() {
        BedrockAgentRuntimeConfiguration bedrockConfiguration = new BedrockAgentRuntimeConfiguration();
        bedrockConfiguration.setUseProfileCredentialsProvider(true);
        BedrockAgentRuntimeInternalClient bedrockClient =
                BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentRuntimeClientIAMProfileOptimizedImpl);
    }
}
