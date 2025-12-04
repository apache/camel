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

package org.apache.camel.component.aws2.bedrock.agentruntime.client;

import org.apache.camel.component.aws2.bedrock.agentruntime.BedrockAgentRuntimeConfiguration;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.impl.BedrockAgentRuntimeClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Bedrock runtime client.
 */
public final class BedrockAgentRuntimeClientFactory {

    private BedrockAgentRuntimeClientFactory() {}

    /**
     * Return the correct AWS Bedrock Agent runtime client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               BedrockAgentRuntimeInternalClient
     */
    public static BedrockAgentRuntimeInternalClient getBedrockAgentRuntimeClient(
            BedrockAgentRuntimeConfiguration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new BedrockAgentRuntimeClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new BedrockAgentRuntimeClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new BedrockAgentRuntimeClientSessionTokenImpl(configuration);
        } else {
            return new BedrockAgentRuntimeClientStandardImpl(configuration);
        }
    }
}
