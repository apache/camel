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

package org.apache.camel.component.aws2.bedrock.agent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.component.aws2.bedrock.agent.client.BedrockAgentClientFactory;
import org.apache.camel.component.aws2.bedrock.agent.client.BedrockAgentInternalClient;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientStandardImpl;
import org.junit.jupiter.api.Test;

public class BedrockAgentClientFactoryTest {

    @Test
    public void getStandardBedrockAgentClientDefault() {
        BedrockAgentConfiguration bedrockConfiguration = new BedrockAgentConfiguration();
        BedrockAgentInternalClient bedrockClient =
                BedrockAgentClientFactory.getBedrockAgentClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentClientStandardImpl);
    }

    @Test
    public void getStandardNoIamBedrockAgentClientDefault() {
        BedrockAgentConfiguration bedrockConfiguration = new BedrockAgentConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(false);
        BedrockAgentInternalClient bedrockClient =
                BedrockAgentClientFactory.getBedrockAgentClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentClientStandardImpl);
    }

    @Test
    public void getIamBedrockAgentClientDefault() {
        BedrockAgentConfiguration bedrockConfiguration = new BedrockAgentConfiguration();
        bedrockConfiguration.setUseDefaultCredentialsProvider(true);
        BedrockAgentInternalClient bedrockClient =
                BedrockAgentClientFactory.getBedrockAgentClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentClientIAMOptimizedImpl);
    }

    @Test
    public void getSessionBedrockAgentClientDefault() {
        BedrockAgentConfiguration bedrockConfiguration = new BedrockAgentConfiguration();
        bedrockConfiguration.setUseSessionCredentials(true);
        BedrockAgentInternalClient bedrockClient =
                BedrockAgentClientFactory.getBedrockAgentClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentClientSessionTokenImpl);
    }

    @Test
    public void getIamProfileBedrockAgentClientDefault() {
        BedrockAgentConfiguration bedrockConfiguration = new BedrockAgentConfiguration();
        bedrockConfiguration.setUseProfileCredentialsProvider(true);
        BedrockAgentInternalClient bedrockClient =
                BedrockAgentClientFactory.getBedrockAgentClient(bedrockConfiguration);
        assertTrue(bedrockClient instanceof BedrockAgentClientIAMProfileOptimizedImpl);
    }
}
