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

package org.apache.camel.component.aws2.bedrock.agent.client;

import org.apache.camel.component.aws2.bedrock.agent.BedrockAgentConfiguration;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.agent.client.impl.BedrockAgentClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Bedrock runtime client.
 */
public final class BedrockAgentClientFactory {

    private BedrockAgentClientFactory() {}

    /**
     * Return the correct AWS Bedrock Agent client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               BedrockAgentInternalClient
     */
    public static BedrockAgentInternalClient getBedrockAgentClient(BedrockAgentConfiguration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new BedrockAgentClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new BedrockAgentClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new BedrockAgentClientSessionTokenImpl(configuration);
        } else {
            return new BedrockAgentClientStandardImpl(configuration);
        }
    }
}
