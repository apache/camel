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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;

/**
 * Operate on AWS Bedrock through its Agent.
 */
@UriEndpoint(firstVersion = "4.5.0", scheme = "aws-bedrock-agent", title = "AWS Bedrock Agent",
             syntax = "aws-bedrock-agent:label", category = { Category.AI, Category.CLOUD },
             headersClass = BedrockAgentRuntimeConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "software.amazon.awssdk.services.bedrockagent.model.IngestionJob",
                                   description = "The ingestion job of the knowledgeBaseId, dataSourceId and ingestionJobId"
                                                 + " options, one message per poll, also when the job did not change, with its"
                                                 + " status in the CamelAwsBedrockAgentIngestionJobStatus header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO),
                                  description = "Not used: the knowledge base, the data source and the ingestion job come from"
                                                + " the options, or from headers such as CamelAwsBedrockAgentKnowledgeBaseId"
                                                + " when the option is not set. With pojoRequest=true the request of the AWS"
                                                + " SDK for the operation, such as a StartIngestionJobRequest: another body"
                                                + " fails"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   javaType = "software.amazon.awssdk.services.bedrockagent.model.IngestionJob",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "startIngestionJob",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "listIngestionJobs",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<software.amazon.awssdk.services.bedrockagent.model.IngestionJobSummary>") },
                                   description = "The id of the ingestion job as text for startIngestionJob, a List of"
                                                 + " IngestionJobSummary for listIngestionJobs, and the IngestionJob for"
                                                 + " getIngestionJob, with its status in the"
                                                 + " CamelAwsBedrockAgentIngestionJobStatus header"))
public class BedrockAgentEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private BedrockAgentClient bedrockAgentClient;

    @UriParam
    private BedrockAgentConfiguration configuration;

    public BedrockAgentEndpoint(String uri, Component component, BedrockAgentConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        final BedrockAgentIngestionJobConsumer consumer = new BedrockAgentIngestionJobConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
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

        bedrockAgentClient = ObjectHelper.isNotEmpty(configuration.getBedrockAgentClient())
                ? configuration.getBedrockAgentClient()
                : BedrockAgentClientFactory.getBedrockAgentClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getBedrockAgentClient())) {
            if (ObjectHelper.isNotEmpty(bedrockAgentClient)) {
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
        return "bedrock-agent";
    }
}
