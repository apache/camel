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
package org.apache.camel.component.aws2.bedrock.runtime;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.bedrock.runtime.client.BedrockClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Invoke Model of AWS Bedrock service.
 */
@UriEndpoint(firstVersion = "4.5.0", scheme = "aws-bedrock", title = "AWS Bedrock",
             syntax = "aws-bedrock:label", producerOnly = true, category = { Category.AI, Category.CLOUD },
             headersClass = BedrockConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.TEXT,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "converse", carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "converseStream",
                                                         carriers = Carrier.ANY),
                                          @BodyType.When(option = "operation", value = "applyGuardrail",
                                                         carriers = Carrier.ANY) },
                                  description = "The request of the model as JSON text for the invoke operations, which also"
                                                + " need the CamelAwsBedrockContentType and CamelAwsBedrockAcceptContentType"
                                                + " headers. Bytes, a stream or a file are sent as their toString(): convert"
                                                + " them to a String first, and marshal a Map or a POJO to JSON. converse,"
                                                + " converseStream and applyGuardrail take their input from headers, such as"
                                                + " CamelAwsBedrockConverseMessages. With pojoRequest=true an"
                                                + " InvokeModelRequest of the AWS SDK: another body fails"),
           producerOut = @BodyType(value = { Carrier.TEXT, Carrier.LIST },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "invokeTextModel",
                                                          carriers = { Carrier.TEXT, Carrier.POJO }),
                                           @BodyType.When(option = "operation", value = "invokeImageModel",
                                                          carriers = Carrier.POJO),
                                           @BodyType.When(option = "operation", value = "invokeEmbeddingsModel",
                                                          carriers = Carrier.POJO,
                                                          javaType = "software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse"),
                                           @BodyType.When(option = "operation", value = "converse", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "applyGuardrail",
                                                          carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String") },
                                   description = "The streaming operations and converseStream give the text, or a List of the"
                                                 + " chunks with streamOutputMode=chunks. invokeTextModel gives the JSON"
                                                 + " response as text for the Amazon Titan models, and a Jackson JsonNode for"
                                                 + " the others: the response, or the generated text for Llama, Cohere and"
                                                 + " Claude 2. invokeImageModel gives a JsonNode array of the images as Base64"
                                                 + " text, and invokeEmbeddingsModel the InvokeModelResponse of the AWS SDK."
                                                 + " converse gives the answer as text, empty when the model asks for a tool,"
                                                 + " and applyGuardrail its action as text: NONE or GUARDRAIL_INTERVENED"))
public class BedrockEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private BedrockRuntimeClient bedrockRuntimeClient;
    private BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient;

    @UriParam
    private BedrockConfiguration configuration;

    public BedrockEndpoint(String uri, Component component, BedrockConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BedrockProducer(this);
    }

    @Override
    public BedrockComponent getComponent() {
        return (BedrockComponent) super.getComponent();
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        // Get sync client
        bedrockRuntimeClient = ObjectHelper.isNotEmpty(configuration.getBedrockRuntimeClient())
                ? configuration.getBedrockRuntimeClient()
                : BedrockClientFactory.getBedrockRuntimeClient(configuration);

        // Get async client for streaming operations
        bedrockRuntimeAsyncClient = ObjectHelper.isNotEmpty(configuration.getBedrockRuntimeAsyncClient())
                ? configuration.getBedrockRuntimeAsyncClient()
                : BedrockClientFactory.getBedrockRuntimeAsyncClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getBedrockRuntimeClient())) {
            if (ObjectHelper.isNotEmpty(bedrockRuntimeClient)) {
                bedrockRuntimeClient.close();
            }
        }
        if (ObjectHelper.isEmpty(configuration.getBedrockRuntimeAsyncClient())) {
            if (ObjectHelper.isNotEmpty(bedrockRuntimeAsyncClient)) {
                bedrockRuntimeAsyncClient.close();
            }
        }
        super.doStop();
    }

    public BedrockConfiguration getConfiguration() {
        return configuration;
    }

    public BedrockRuntimeClient getBedrockRuntimeClient() {
        return bedrockRuntimeClient;
    }

    public BedrockRuntimeAsyncClient getBedrockRuntimeAsyncClient() {
        return bedrockRuntimeAsyncClient;
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
        return "bedrock-runtime";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        HashMap<String, String> metadata = new HashMap<>();
        if (ObjectHelper.isNotEmpty(configuration.getModelId())) {
            metadata.put("modelId", configuration.getModelId());
        }
        return metadata;
    }
}
