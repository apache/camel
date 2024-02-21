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
package org.apache.camel.component.aws.config;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.model.*;

/**
 * A Producer which sends messages to the Amazon Config Service SDK v2 <a href="http://aws.amazon.com/config/">AWS
 * Config</a>
 */
public class AWSConfigProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWSConfigProducer.class);

    private transient String configProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public AWSConfigProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case putConfigRule:
                putConfigRule(getEndpoint().getConfigClient(), exchange);
                break;
            case removeConfigRule:
                removeConfigRule(getEndpoint().getConfigClient(), exchange);
                break;
            case describeRuleCompliance:
                describeRuleCompliance(getEndpoint().getConfigClient(), exchange);
                break;
            case putConformancePack:
                putConformancePack(getEndpoint().getConfigClient(), exchange);
                break;
            case removeConformancePack:
                removeConformancePack(getEndpoint().getConfigClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private AWSConfigOperations determineOperation(Exchange exchange) {
        AWSConfigOperations operation = exchange.getIn().getHeader(AWSConfigConstants.OPERATION, AWSConfigOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected AWSConfigConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (configProducerToString == null) {
            configProducerToString = "AWSConfigProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return configProducerToString;
    }

    @Override
    public AWSConfigEndpoint getEndpoint() {
        return (AWSConfigEndpoint) super.getEndpoint();
    }

    private void putConfigRule(ConfigClient configClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutConfigRuleRequest) {
                PutConfigRuleResponse result;
                try {
                    PutConfigRuleRequest request = (PutConfigRuleRequest) payload;
                    result = configClient.putConfigRule(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Put Config rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutConfigRuleRequest.Builder builder = PutConfigRuleRequest.builder();
            ConfigRule.Builder configRule = ConfigRule.builder();
            Source.Builder source = Source.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.SOURCE))) {
                String sourceString = exchange.getIn().getHeader(AWSConfigConstants.SOURCE, String.class);
                source.owner(sourceString);
            } else {
                throw new IllegalArgumentException("Source Owner must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.RULE_SOURCE_IDENTIFIER))) {
                String ruleId = exchange.getIn().getHeader(AWSConfigConstants.RULE_SOURCE_IDENTIFIER, String.class);
                source.sourceIdentifier(ruleId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME, String.class);
                configRule.configRuleName(ruleName);
            }
            configRule.source(source.build());
            PutConfigRuleResponse result;
            try {
                PutConfigRuleRequest request = builder.configRule(configRule.build()).build();
                result = configClient.putConfigRule(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Put Config Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void removeConfigRule(ConfigClient configClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteConfigRuleRequest) {
                DeleteConfigRuleResponse result;
                try {
                    DeleteConfigRuleRequest request = (DeleteConfigRuleRequest) payload;
                    result = configClient.deleteConfigRule(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Config rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteConfigRuleRequest.Builder builder = DeleteConfigRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME, String.class);
                builder.configRuleName(ruleName);
            } else {
                throw new IllegalArgumentException("Rule Name must be specified");
            }
            DeleteConfigRuleResponse result;
            try {
                DeleteConfigRuleRequest request = builder.build();
                result = configClient.deleteConfigRule(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Config Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeRuleCompliance(ConfigClient configClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeComplianceByConfigRuleRequest) {
                DescribeComplianceByConfigRuleResponse result;
                try {
                    DescribeComplianceByConfigRuleRequest request = (DescribeComplianceByConfigRuleRequest) payload;
                    result = configClient.describeComplianceByConfigRule(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Compliance by Config rule command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeComplianceByConfigRuleRequest.Builder builder = DescribeComplianceByConfigRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME, String.class);
                builder.configRuleNames(ruleName);
            }
            DescribeComplianceByConfigRuleResponse result;
            try {
                DescribeComplianceByConfigRuleRequest request = builder.build();
                result = configClient.describeComplianceByConfigRule(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Compliance by Config Rule command returned the error code {}",
                        ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void putConformancePack(ConfigClient configClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutConformancePackRequest) {
                PutConformancePackResponse result;
                try {
                    PutConformancePackRequest request = (PutConformancePackRequest) payload;
                    result = configClient.putConformancePack(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Put Conformance Pack command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutConformancePackRequest.Builder builder = PutConformancePackRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_NAME))) {
                String conformancePackName = exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_NAME, String.class);
                builder.conformancePackName(conformancePackName);
            } else {
                throw new IllegalArgumentException("Rule Name must be specified");
            }
            String conformancePackS3TemplateUri = null;
            String conformancePackTemplateBody = null;
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_S3_TEMPLATE_URI))) {
                conformancePackS3TemplateUri
                        = exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_S3_TEMPLATE_URI, String.class);
                builder.templateS3Uri(conformancePackS3TemplateUri);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_TEMPLATE_BODY))) {
                conformancePackTemplateBody
                        = exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_TEMPLATE_BODY, String.class);
                builder.templateBody(conformancePackTemplateBody);
            }
            if (ObjectHelper.isEmpty(conformancePackS3TemplateUri) && ObjectHelper.isEmpty(conformancePackTemplateBody)) {
                throw new IllegalArgumentException("One of Conformace Pack S3 Template URI or Template Body must be specified");
            }
            PutConformancePackResponse result;
            try {
                PutConformancePackRequest request = builder.build();
                result = configClient.putConformancePack(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Put Conformance Pack command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void removeConformancePack(ConfigClient configClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteConformancePackRequest) {
                DeleteConformancePackResponse result;
                try {
                    DeleteConformancePackRequest request = (DeleteConformancePackRequest) payload;
                    result = configClient.deleteConformancePack(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Remove Conformance Pack rule command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteConformancePackRequest.Builder builder = DeleteConformancePackRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWSConfigConstants.CONFORMACE_PACK_NAME))) {
                String conformancePackName = exchange.getIn().getHeader(AWSConfigConstants.RULE_NAME, String.class);
                builder.conformancePackName(conformancePackName);
            } else {
                throw new IllegalArgumentException("Conformance Pack Name must be specified");
            }
            DeleteConformancePackResponse result;
            try {
                DeleteConformancePackRequest request = builder.build();
                result = configClient.deleteConformancePack(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Remove Conformance Pack command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new AWSConfigProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (healthCheckRepository != null && producerHealthCheck != null) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
