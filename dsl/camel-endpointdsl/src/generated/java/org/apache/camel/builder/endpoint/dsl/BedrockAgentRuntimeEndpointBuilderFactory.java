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
package org.apache.camel.builder.endpoint.dsl;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.processing.Generated;
import org.apache.camel.builder.EndpointConsumerBuilder;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.builder.endpoint.AbstractEndpointBuilder;

/**
 * Invoke Model of AWS Bedrock Agent Runtime service.
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.EndpointDslMojo")
public interface BedrockAgentRuntimeEndpointBuilderFactory {


    /**
     * Builder for endpoint for the AWS Bedrock Agent Runtime component.
     */
    public interface BedrockAgentRuntimeEndpointBuilder
            extends
                EndpointProducerBuilder {
        default AdvancedBedrockAgentRuntimeEndpointBuilder advanced() {
            return (AdvancedBedrockAgentRuntimeEndpointBuilder) this;
        }
        /**
         * Define the Knowledge Base Id we are going to use.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param knowledgeBaseId the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder knowledgeBaseId(
                String knowledgeBaseId) {
            doSetProperty("knowledgeBaseId", knowledgeBaseId);
            return this;
        }
        /**
         * Define the model Id we are going to use.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param modelId the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder modelId(String modelId) {
            doSetProperty("modelId", modelId);
            return this;
        }
        /**
         * The operation to perform.
         * 
         * The option is a:
         * &lt;code&gt;org.apache.camel.component.aws2.bedrock.agentruntime.BedrockAgentRuntimeOperations&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param operation the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder operation(
                org.apache.camel.component.aws2.bedrock.agentruntime.BedrockAgentRuntimeOperations operation) {
            doSetProperty("operation", operation);
            return this;
        }
        /**
         * The operation to perform.
         * 
         * The option will be converted to a
         * &lt;code&gt;org.apache.camel.component.aws2.bedrock.agentruntime.BedrockAgentRuntimeOperations&lt;/code&gt; type.
         * 
         * Required: true
         * Group: producer
         * 
         * @param operation the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder operation(String operation) {
            doSetProperty("operation", operation);
            return this;
        }
        /**
         * Set the need for overriding the endpoint. This option needs to be
         * used in combination with the uriEndpointOverride option.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param overrideEndpoint the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder overrideEndpoint(
                boolean overrideEndpoint) {
            doSetProperty("overrideEndpoint", overrideEndpoint);
            return this;
        }
        /**
         * Set the need for overriding the endpoint. This option needs to be
         * used in combination with the uriEndpointOverride option.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param overrideEndpoint the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder overrideEndpoint(
                String overrideEndpoint) {
            doSetProperty("overrideEndpoint", overrideEndpoint);
            return this;
        }
        /**
         * If we want to use a POJO request as body or not.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param pojoRequest the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder pojoRequest(
                boolean pojoRequest) {
            doSetProperty("pojoRequest", pojoRequest);
            return this;
        }
        /**
         * If we want to use a POJO request as body or not.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param pojoRequest the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder pojoRequest(
                String pojoRequest) {
            doSetProperty("pojoRequest", pojoRequest);
            return this;
        }
        /**
         * If using a profile credentials provider, this parameter will set the
         * profile name.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param profileCredentialsName the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder profileCredentialsName(
                String profileCredentialsName) {
            doSetProperty("profileCredentialsName", profileCredentialsName);
            return this;
        }
        /**
         * The region in which Bedrock Agent Runtime client needs to work. When
         * using this parameter, the configuration will expect the lowercase
         * name of the region (for example, ap-east-1) You'll need to use the
         * name Region.EU_WEST_1.id().
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param region the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder region(String region) {
            doSetProperty("region", region);
            return this;
        }
        /**
         * Set the overriding uri endpoint. This option needs to be used in
         * combination with overrideEndpoint option.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param uriEndpointOverride the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder uriEndpointOverride(
                String uriEndpointOverride) {
            doSetProperty("uriEndpointOverride", uriEndpointOverride);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to load
         * credentials through a default credentials provider or to expect
         * static credentials to be passed in.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useDefaultCredentialsProvider the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useDefaultCredentialsProvider(
                boolean useDefaultCredentialsProvider) {
            doSetProperty("useDefaultCredentialsProvider", useDefaultCredentialsProvider);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to load
         * credentials through a default credentials provider or to expect
         * static credentials to be passed in.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useDefaultCredentialsProvider the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useDefaultCredentialsProvider(
                String useDefaultCredentialsProvider) {
            doSetProperty("useDefaultCredentialsProvider", useDefaultCredentialsProvider);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to load
         * credentials through a profile credentials provider.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useProfileCredentialsProvider the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useProfileCredentialsProvider(
                boolean useProfileCredentialsProvider) {
            doSetProperty("useProfileCredentialsProvider", useProfileCredentialsProvider);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to load
         * credentials through a profile credentials provider.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param useProfileCredentialsProvider the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useProfileCredentialsProvider(
                String useProfileCredentialsProvider) {
            doSetProperty("useProfileCredentialsProvider", useProfileCredentialsProvider);
            return this;
        }
        /**
         * To define a proxy host when instantiating the Bedrock Agent Runtime
         * client.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: proxy
         * 
         * @param proxyHost the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder proxyHost(String proxyHost) {
            doSetProperty("proxyHost", proxyHost);
            return this;
        }
        /**
         * To define a proxy port when instantiating the Bedrock Agent Runtime
         * client.
         * 
         * The option is a: &lt;code&gt;java.lang.Integer&lt;/code&gt; type.
         * 
         * Group: proxy
         * 
         * @param proxyPort the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder proxyPort(Integer proxyPort) {
            doSetProperty("proxyPort", proxyPort);
            return this;
        }
        /**
         * To define a proxy port when instantiating the Bedrock Agent Runtime
         * client.
         * 
         * The option will be converted to a
         * &lt;code&gt;java.lang.Integer&lt;/code&gt; type.
         * 
         * Group: proxy
         * 
         * @param proxyPort the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder proxyPort(String proxyPort) {
            doSetProperty("proxyPort", proxyPort);
            return this;
        }
        /**
         * To define a proxy protocol when instantiating the Bedrock Agent
         * Runtime client.
         * 
         * The option is a:
         * &lt;code&gt;software.amazon.awssdk.core.Protocol&lt;/code&gt; type.
         * 
         * Default: HTTPS
         * Group: proxy
         * 
         * @param proxyProtocol the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder proxyProtocol(
                software.amazon.awssdk.core.Protocol proxyProtocol) {
            doSetProperty("proxyProtocol", proxyProtocol);
            return this;
        }
        /**
         * To define a proxy protocol when instantiating the Bedrock Agent
         * Runtime client.
         * 
         * The option will be converted to a
         * &lt;code&gt;software.amazon.awssdk.core.Protocol&lt;/code&gt; type.
         * 
         * Default: HTTPS
         * Group: proxy
         * 
         * @param proxyProtocol the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder proxyProtocol(
                String proxyProtocol) {
            doSetProperty("proxyProtocol", proxyProtocol);
            return this;
        }
        /**
         * Amazon AWS Access Key.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: security
         * 
         * @param accessKey the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder accessKey(String accessKey) {
            doSetProperty("accessKey", accessKey);
            return this;
        }
        /**
         * Amazon AWS Secret Key.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: security
         * 
         * @param secretKey the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder secretKey(String secretKey) {
            doSetProperty("secretKey", secretKey);
            return this;
        }
        /**
         * Amazon AWS Session Token used when the user needs to assume an IAM
         * role.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: security
         * 
         * @param sessionToken the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder sessionToken(
                String sessionToken) {
            doSetProperty("sessionToken", sessionToken);
            return this;
        }
        /**
         * If we want to trust all certificates in case of overriding the
         * endpoint.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: security
         * 
         * @param trustAllCertificates the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder trustAllCertificates(
                boolean trustAllCertificates) {
            doSetProperty("trustAllCertificates", trustAllCertificates);
            return this;
        }
        /**
         * If we want to trust all certificates in case of overriding the
         * endpoint.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: security
         * 
         * @param trustAllCertificates the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder trustAllCertificates(
                String trustAllCertificates) {
            doSetProperty("trustAllCertificates", trustAllCertificates);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to use
         * Session Credentials. This is useful in a situation in which the user
         * needs to assume an IAM role for doing operations in Bedrock.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: security
         * 
         * @param useSessionCredentials the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useSessionCredentials(
                boolean useSessionCredentials) {
            doSetProperty("useSessionCredentials", useSessionCredentials);
            return this;
        }
        /**
         * Set whether the Bedrock Agent Runtime client should expect to use
         * Session Credentials. This is useful in a situation in which the user
         * needs to assume an IAM role for doing operations in Bedrock.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: security
         * 
         * @param useSessionCredentials the value to set
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder useSessionCredentials(
                String useSessionCredentials) {
            doSetProperty("useSessionCredentials", useSessionCredentials);
            return this;
        }
    }

    /**
     * Advanced builder for endpoint for the AWS Bedrock Agent Runtime
     * component.
     */
    public interface AdvancedBedrockAgentRuntimeEndpointBuilder
            extends
                EndpointProducerBuilder {
        default BedrockAgentRuntimeEndpointBuilder basic() {
            return (BedrockAgentRuntimeEndpointBuilder) this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedBedrockAgentRuntimeEndpointBuilder lazyStartProducer(
                boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option will be converted to a &lt;code&gt;boolean&lt;/code&gt;
         * type.
         * 
         * Default: false
         * Group: producer (advanced)
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default AdvancedBedrockAgentRuntimeEndpointBuilder lazyStartProducer(
                String lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
        /**
         * To use an existing configured AWS Bedrock Agent Runtime client.
         * 
         * The option is a:
         * &lt;code&gt;software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient&lt;/code&gt; type.
         * 
         * Group: advanced
         * 
         * @param bedrockAgentRuntimeClient the value to set
         * @return the dsl builder
         */
        default AdvancedBedrockAgentRuntimeEndpointBuilder bedrockAgentRuntimeClient(
                software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient bedrockAgentRuntimeClient) {
            doSetProperty("bedrockAgentRuntimeClient", bedrockAgentRuntimeClient);
            return this;
        }
        /**
         * To use an existing configured AWS Bedrock Agent Runtime client.
         * 
         * The option will be converted to a
         * &lt;code&gt;software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient&lt;/code&gt; type.
         * 
         * Group: advanced
         * 
         * @param bedrockAgentRuntimeClient the value to set
         * @return the dsl builder
         */
        default AdvancedBedrockAgentRuntimeEndpointBuilder bedrockAgentRuntimeClient(
                String bedrockAgentRuntimeClient) {
            doSetProperty("bedrockAgentRuntimeClient", bedrockAgentRuntimeClient);
            return this;
        }
    }

    public interface BedrockAgentRuntimeBuilders {
        /**
         * AWS Bedrock Agent Runtime (camel-aws-bedrock)
         * Invoke Model of AWS Bedrock Agent Runtime service.
         * 
         * Category: ai,cloud
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-aws-bedrock
         * 
         * @return the dsl builder for the headers' name.
         */
        default BedrockAgentRuntimeHeaderNameBuilder awsBedrockAgentRuntime() {
            return BedrockAgentRuntimeHeaderNameBuilder.INSTANCE;
        }
        /**
         * AWS Bedrock Agent Runtime (camel-aws-bedrock)
         * Invoke Model of AWS Bedrock Agent Runtime service.
         * 
         * Category: ai,cloud
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-aws-bedrock
         * 
         * Syntax: <code>aws-bedrock-agent-runtime:label</code>
         * 
         * Path parameter: label (required)
         * Logical name
         * 
         * @param path label
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder awsBedrockAgentRuntime(
                String path) {
            return BedrockAgentRuntimeEndpointBuilderFactory.endpointBuilder("aws-bedrock-agent-runtime", path);
        }
        /**
         * AWS Bedrock Agent Runtime (camel-aws-bedrock)
         * Invoke Model of AWS Bedrock Agent Runtime service.
         * 
         * Category: ai,cloud
         * Since: 4.5
         * Maven coordinates: org.apache.camel:camel-aws-bedrock
         * 
         * Syntax: <code>aws-bedrock-agent-runtime:label</code>
         * 
         * Path parameter: label (required)
         * Logical name
         * 
         * @param componentName to use a custom component name for the endpoint
         * instead of the default name
         * @param path label
         * @return the dsl builder
         */
        default BedrockAgentRuntimeEndpointBuilder awsBedrockAgentRuntime(
                String componentName,
                String path) {
            return BedrockAgentRuntimeEndpointBuilderFactory.endpointBuilder(componentName, path);
        }
    }

    /**
     * The builder of headers' name for the AWS Bedrock Agent Runtime component.
     */
    public static class BedrockAgentRuntimeHeaderNameBuilder {
        /**
         * The internal instance of the builder used to access to all the
         * methods representing the name of headers.
         */
        private static final BedrockAgentRuntimeHeaderNameBuilder INSTANCE = new BedrockAgentRuntimeHeaderNameBuilder();

        /**
         * The operation we want to perform.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * AwsBedrockAgentRuntimeOperation}.
         */
        public String awsBedrockAgentRuntimeOperation() {
            return "CamelAwsBedrockAgentRuntimeOperation";
        }

        /**
         * When retrieving and generating a response, this header will contain
         * the citations.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * AwsBedrockAgentRuntimeCitations}.
         */
        public String awsBedrockAgentRuntimeCitations() {
            return "CamelAwsBedrockAgentRuntimeCitations";
        }

        /**
         * When retrieving and generating a response, this header will contain
         * he unique identifier of the session. Reuse the same value to continue
         * the same session with the knowledge base.
         * 
         * The option is a: {@code String} type.
         * 
         * Group: producer
         * 
         * @return the name of the header {@code
         * AwsBedrockAgentRuntimeSessionId}.
         */
        public String awsBedrockAgentRuntimeSessionId() {
            return "CamelAwsBedrockAgentRuntimeSessionId";
        }
    }
    static BedrockAgentRuntimeEndpointBuilder endpointBuilder(
            String componentName,
            String path) {
        class BedrockAgentRuntimeEndpointBuilderImpl extends AbstractEndpointBuilder implements BedrockAgentRuntimeEndpointBuilder, AdvancedBedrockAgentRuntimeEndpointBuilder {
            public BedrockAgentRuntimeEndpointBuilderImpl(String path) {
                super(componentName, path);
            }
        }
        return new BedrockAgentRuntimeEndpointBuilderImpl(path);
    }
}