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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.bedrock.agent.client.BedrockAgentClientFactory;
import org.apache.camel.component.aws2.bedrock.agentruntime.BedrockAgentRuntimeConstants;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;

/**
 * Invoke Model of AWS Bedrock Agent Runtime service.
 */
@UriEndpoint(firstVersion = "4.5.0", scheme = "aws-bedrock-agent", title = "AWS Bedrock Agent",
             syntax = "aws-bedrock-agent:label", producerOnly = true, category = { Category.AI, Category.CLOUD },
             headersClass = BedrockAgentRuntimeConstants.class)
public class BedrockAgentEndpoint extends ScheduledPollEndpoint {

    private BedrockAgentClient bedrockAgentClient;

    @UriParam
    private BedrockAgentConfiguration configuration;

    public BedrockAgentEndpoint(String uri, Component component, BedrockAgentConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BedrockAgentProducer(this);
    }

    @Override
    public BedrockAgentComponent getComponent() {
        return (BedrockAgentComponent) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        bedrockAgentClient = configuration.getBedrockAgentClient() != null
                ? configuration.getBedrockAgentClient()
                : BedrockAgentClientFactory.getBedrockAgentClient(configuration).getBedrockAgentClient();
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getBedrockAgentClient())) {
            if (bedrockAgentClient != null) {
                bedrockAgentClient.close();
            }
        }
        super.doStop();
    }

    public BedrockAgentConfiguration getConfiguration() {
        return configuration;
    }

    public BedrockAgentClient getBedrockAgentClient() {
        return bedrockAgentClient;
    }
}
