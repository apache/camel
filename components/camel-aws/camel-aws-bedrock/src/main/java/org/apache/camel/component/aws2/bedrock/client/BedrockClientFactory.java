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
package org.apache.camel.component.aws2.bedrock.client;

import org.apache.camel.component.aws2.bedrock.BedrockConfiguration;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientIAMOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientIAMProfileOptimizedImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientSessionTokenImpl;
import org.apache.camel.component.aws2.bedrock.client.impl.BedrockRuntimeClientStandardImpl;

/**
 * Factory class to return the correct type of AWS Bedrock runtime client.
 */
public final class BedrockClientFactory {

    private BedrockClientFactory() {
    }

    /**
     * Return the correct AWS Bedrock runtime client (based on remote vs local).
     *
     * @param  configuration configuration
     * @return               BedrockRuntimeInternalClient
     */
    public static BedrockRuntimeInternalClient getBedrockRuntimeClient(BedrockConfiguration configuration) {
        if (Boolean.TRUE.equals(configuration.isUseDefaultCredentialsProvider())) {
            return new BedrockRuntimeClientIAMOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseProfileCredentialsProvider())) {
            return new BedrockRuntimeClientIAMProfileOptimizedImpl(configuration);
        } else if (Boolean.TRUE.equals(configuration.isUseSessionCredentials())) {
            return new BedrockRuntimeClientSessionTokenImpl(configuration);
        } else {
            return new BedrockRuntimeClientStandardImpl(configuration);
        }
    }
}
