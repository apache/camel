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
package org.apache.camel.component.aws2.eventbridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.stream.Stream;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DeleteRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.DisableRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DisableRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.EnableRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.EnableRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.ListRuleNamesByTargetRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRuleNamesByTargetResponse;
import software.amazon.awssdk.services.eventbridge.model.ListRulesRequest;
import software.amazon.awssdk.services.eventbridge.model.ListRulesResponse;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.ListTargetsByRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.PutRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsRequest;
import software.amazon.awssdk.services.eventbridge.model.RemoveTargetsResponse;
import software.amazon.awssdk.services.eventbridge.model.Target;

/**
 * A Producer which sends messages to the Amazon Eventbridge Service SDK v2
 * <a href="http://aws.amazon.com/eventbridge/">AWS Eventbridge</a>
 */
public class EventbridgeProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EventbridgeProducer.class);

    private transient String eventbridgeProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public EventbridgeProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case putRule:
                putRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case putTargets:
                putTargets(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case removeTargets:
                removeTargets(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case deleteRule:
                deleteRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case enableRule:
                enableRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case disableRule:
                disableRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case listRules:
                listRules(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case describeRule:
                describeRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case listTargetsByRule:
                listTargetsByRule(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case listRuleNamesByTarget:
                listRuleNamesByTarget(getEndpoint().getEventbridgeClient(), exchange);
                break;
            case putEvent:
                putEvent(getEndpoint().getEventbridgeClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private EventbridgeOperations determineOperation(Exchange exchange) {
        EventbridgeOperations operation
                = exchange.getIn().getHeader(EventbridgeConstants.OPERATION, EventbridgeOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected EventbridgeConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (eventbridgeProducerToString == null) {
            eventbridgeProducerToString = "EventbridgeProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return eventbridgeProducerToString;
    }

    private void putRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException, IOException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutRuleRequest) {
                PutRuleResponse result;
                try {
                    result = eventbridgeClient.putRule((PutRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("PutRule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutRuleRequest.Builder builder = PutRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            if (ObjectHelper.isEmpty(exchange.getIn().getHeader(EventbridgeConstants.EVENT_PATTERN))) {
                try (InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getEndpoint().getCamelContext(),
                        getConfiguration().getEventPatternFile())) {
                    String eventPattern = IOUtils.toString(s, Charset.defaultCharset());
                    builder.eventPattern(eventPattern);
                }
            } else {
                String eventPattern = exchange.getIn().getHeader(EventbridgeConstants.EVENT_PATTERN, String.class);
                builder.eventPattern(eventPattern);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            PutRuleResponse result;
            try {
                result = eventbridgeClient.putRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Put Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void putTargets(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutTargetsRequest) {
                PutTargetsResponse result;
                try {
                    result = eventbridgeClient.putTargets((PutTargetsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("PutTargets command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutTargetsRequest.Builder builder = PutTargetsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.rule(ruleName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.TARGETS))) {
                Collection<Target> targets = exchange.getIn().getHeader(EventbridgeConstants.TARGETS, Collection.class);
                builder.targets(targets);
            } else {
                throw new IllegalArgumentException("At least one targets must be specified");
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            PutTargetsResponse result;
            try {
                result = eventbridgeClient.putTargets(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Put Targets command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void removeTargets(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RemoveTargetsRequest) {
                RemoveTargetsResponse result;
                try {
                    result = eventbridgeClient.removeTargets((RemoveTargetsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("RemoveTargets command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            RemoveTargetsRequest.Builder builder = RemoveTargetsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.rule(ruleName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.TARGETS_IDS))) {
                Collection<String> ids = exchange.getIn().getHeader(EventbridgeConstants.TARGETS_IDS, Collection.class);
                builder.ids(ids);
            } else {
                throw new IllegalArgumentException("At least one targets must be specified");
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            RemoveTargetsResponse result;
            try {
                result = eventbridgeClient.removeTargets(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Remove Targets command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteRuleRequest) {
                DeleteRuleResponse result;
                try {
                    result = eventbridgeClient.deleteRule((DeleteRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteRuleRequest.Builder builder = DeleteRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            DeleteRuleResponse result;
            try {
                result = eventbridgeClient.deleteRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void enableRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof EnableRuleRequest) {
                EnableRuleResponse result;
                try {
                    result = eventbridgeClient.enableRule((EnableRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Enable Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            EnableRuleRequest.Builder builder = EnableRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            EnableRuleResponse result;
            try {
                result = eventbridgeClient.enableRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Enable Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void disableRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DisableRuleRequest) {
                DisableRuleResponse result;
                try {
                    result = eventbridgeClient.disableRule((DisableRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Disable Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DisableRuleRequest.Builder builder = DisableRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            DisableRuleResponse result;
            try {
                result = eventbridgeClient.disableRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Disable Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listRules(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListRulesRequest) {
                ListRulesResponse result;
                try {
                    result = eventbridgeClient.listRules((ListRulesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Rules command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListRulesRequest.Builder builder = ListRulesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME_PREFIX))) {
                String ruleNamePrefix = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME_PREFIX, String.class);
                builder.namePrefix(ruleNamePrefix);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            ListRulesResponse result;
            try {
                result = eventbridgeClient.listRules(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Disable Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeRuleRequest) {
                DescribeRuleResponse result;
                try {
                    result = eventbridgeClient.describeRule((DescribeRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeRuleRequest.Builder builder = DescribeRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.name(ruleName);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            DescribeRuleResponse result;
            try {
                result = eventbridgeClient.describeRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listTargetsByRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListTargetsByRuleRequest) {
                ListTargetsByRuleResponse result;
                try {
                    result = eventbridgeClient.listTargetsByRule((ListTargetsByRuleRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Targets by Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListTargetsByRuleRequest.Builder builder = ListTargetsByRuleRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME))) {
                String ruleName = exchange.getIn().getHeader(EventbridgeConstants.RULE_NAME, String.class);
                builder.rule(ruleName);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            ListTargetsByRuleResponse result;
            try {
                result = eventbridgeClient.listTargetsByRule(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Targets by Rule command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listRuleNamesByTarget(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListRuleNamesByTargetRequest) {
                ListRuleNamesByTargetResponse result;
                try {
                    result = eventbridgeClient.listRuleNamesByTarget((ListRuleNamesByTargetRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Rule Name by Targets command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListRuleNamesByTargetRequest.Builder builder = ListRuleNamesByTargetRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.TARGET_ARN))) {
                String targetArn = exchange.getIn().getHeader(EventbridgeConstants.TARGET_ARN, String.class);
                builder.targetArn(targetArn);
            }
            builder.eventBusName(getConfiguration().getEventbusName());
            ListRuleNamesByTargetResponse result;
            try {
                result = eventbridgeClient.listRuleNamesByTarget(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Rule by Target command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void putEvent(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof PutEventsRequest) {
                PutEventsResponse result;
                try {
                    result = eventbridgeClient.putEvents((PutEventsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("PutEvents command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            PutEventsRequest.Builder builder = PutEventsRequest.builder();
            PutEventsRequestEntry.Builder entryBuilder = PutEventsRequestEntry.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.EVENT_RESOURCES_ARN))) {
                String resourcesArn = exchange.getIn().getHeader(EventbridgeConstants.EVENT_RESOURCES_ARN, String.class);
                entryBuilder.resources(Stream.of(resourcesArn.split(",")).toList());
            } else {
                throw new IllegalArgumentException("At least one resource ARN must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.EVENT_DETAIL_TYPE))) {
                String detailType = exchange.getIn().getHeader(EventbridgeConstants.EVENT_DETAIL_TYPE, String.class);
                entryBuilder.detailType(detailType);
            } else {
                throw new IllegalArgumentException("Detail Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EventbridgeConstants.EVENT_SOURCE))) {
                String source = exchange.getIn().getHeader(EventbridgeConstants.EVENT_SOURCE, String.class);
                entryBuilder.source(source);
            } else {
                throw new IllegalArgumentException("Source must be specified");
            }
            entryBuilder.eventBusName(getConfiguration().getEventbusName());
            entryBuilder.detail(exchange.getMessage().getMandatoryBody(String.class));

            builder.entries(entryBuilder.build());
            PutEventsResponse result;
            try {
                result = eventbridgeClient.putEvents(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Put Events command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @Override
    public EventbridgeEndpoint getEndpoint() {
        return (EventbridgeEndpoint) super.getEndpoint();
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
            producerHealthCheck = new EventbridgeProducerHealthCheck(getEndpoint(), id);
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
