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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.*;
import org.apache.camel.component.aws2.bedrock.agentruntime.client.BedrockAgentRuntimeClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

/**
 * Invoke Model of AWS Bedrock Agent Runtime service.
 */
@UriEndpoint(firstVersion = "4.5.0", scheme = "aws-bedrock-agent-runtime", title = "AWS Bedrock Agent Runtime",
             syntax = "aws-bedrock-agent-runtime:label", producerOnly = true, category = { Category.AI, Category.CLOUD },
             headersClass = BedrockAgentRuntimeConstants.class)
public class BedrockAgentRuntimeEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private BedrockAgentRuntimeClient bedrockAgentRuntimeClient;

    @UriParam
    private BedrockAgentRuntimeConfiguration configuration;

    public BedrockAgentRuntimeEndpoint(String uri, Component component, BedrockAgentRuntimeConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BedrockAgentRuntimeProducer(this);
    }

    @Override
    public BedrockAgentRuntimeComponent getComponent() {
        return (BedrockAgentRuntimeComponent) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        bedrockAgentRuntimeClient = configuration.getBedrockAgentRuntimeClient() != null
                ? configuration.getBedrockAgentRuntimeClient()
                : BedrockAgentRuntimeClientFactory.getBedrockAgentRuntimeClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getBedrockAgentRuntimeClient())) {
            if (bedrockAgentRuntimeClient != null) {
                bedrockAgentRuntimeClient.close();
            }
        }
        super.doStop();
    }

    public BedrockAgentRuntimeConfiguration getConfiguration() {
        return configuration;
    }

    public BedrockAgentRuntimeClient getBedrockAgentRuntimeClient() {
        return bedrockAgentRuntimeClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "bedrock-agent-runtime";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        HashMap<String, String> metadata = new HashMap<>();
        if (configuration.getModelId() != null) {
            metadata.put("modelId", configuration.getModelId());
        }
        return metadata;
    }
}
