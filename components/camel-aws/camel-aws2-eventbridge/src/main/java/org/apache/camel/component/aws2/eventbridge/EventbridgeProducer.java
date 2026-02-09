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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
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
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.DescribeRuleResponse;
import software.amazon.awssdk.services.eventbridge.model.DisableRuleRequest;
import software.amazon.awssdk.services.eventbridge.model.EnableRuleRequest;
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
                message.setHeader(EventbridgeConstants.RULE_ARN, result.ruleArn());
            }
        } else {
            PutRuleRequest.Builder builder = PutRuleRequest.builder();
            String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
            if (ObjectHelper.isNotEmpty(ruleName)) {
                builder.name(ruleName);
            }
            String eventPattern = getOptionalHeader(exchange, EventbridgeConstants.EVENT_PATTERN, String.class);
            if (ObjectHelper.isEmpty(eventPattern)) {
                try (InputStream s = ResourceHelper.resolveMandatoryResourceAsInputStream(this.getEndpoint().getCamelContext(),
                        getConfiguration().getEventPatternFile())) {
                    eventPattern = IOUtils.toString(s, Charset.defaultCharset());
                }
            }
            builder.eventPattern(eventPattern);
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
            message.setHeader(EventbridgeConstants.RULE_ARN, result.ruleArn());
        }
    }

    @SuppressWarnings("unchecked")
    private void putTargets(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                PutTargetsRequest.class,
                eventbridgeClient::putTargets,
                () -> {
                    PutTargetsRequest.Builder builder = PutTargetsRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.rule(ruleName);
                    }
                    Collection<Target> targets = getOptionalHeader(exchange, EventbridgeConstants.TARGETS, Collection.class);
                    if (ObjectHelper.isEmpty(targets)) {
                        throw new IllegalArgumentException("At least one targets must be specified");
                    }
                    builder.targets(targets);
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.putTargets(builder.build());
                },
                "Put Targets",
                (PutTargetsResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.FAILED_ENTRY_COUNT, response.failedEntryCount());
                });
    }

    @SuppressWarnings("unchecked")
    private void removeTargets(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                RemoveTargetsRequest.class,
                eventbridgeClient::removeTargets,
                () -> {
                    RemoveTargetsRequest.Builder builder = RemoveTargetsRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.rule(ruleName);
                    }
                    Collection<String> ids = getOptionalHeader(exchange, EventbridgeConstants.TARGETS_IDS, Collection.class);
                    if (ObjectHelper.isEmpty(ids)) {
                        throw new IllegalArgumentException("At least one target ID must be specified");
                    }
                    builder.ids(ids);
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.removeTargets(builder.build());
                },
                "Remove Targets",
                (RemoveTargetsResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.FAILED_ENTRY_COUNT, response.failedEntryCount());
                });
    }

    private void deleteRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteRuleRequest.class,
                eventbridgeClient::deleteRule,
                () -> {
                    DeleteRuleRequest.Builder builder = DeleteRuleRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.name(ruleName);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.deleteRule(builder.build());
                },
                "Delete Rule");
    }

    private void enableRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                EnableRuleRequest.class,
                eventbridgeClient::enableRule,
                () -> {
                    EnableRuleRequest.Builder builder = EnableRuleRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.name(ruleName);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.enableRule(builder.build());
                },
                "Enable Rule");
    }

    private void disableRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DisableRuleRequest.class,
                eventbridgeClient::disableRule,
                () -> {
                    DisableRuleRequest.Builder builder = DisableRuleRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.name(ruleName);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.disableRule(builder.build());
                },
                "Disable Rule");
    }

    private void listRules(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListRulesRequest.class,
                eventbridgeClient::listRules,
                () -> {
                    ListRulesRequest.Builder builder = ListRulesRequest.builder();
                    String ruleNamePrefix = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME_PREFIX, String.class);
                    if (ObjectHelper.isNotEmpty(ruleNamePrefix)) {
                        builder.namePrefix(ruleNamePrefix);
                    }
                    String nextToken = getOptionalHeader(exchange, EventbridgeConstants.NEXT_TOKEN, String.class);
                    if (ObjectHelper.isNotEmpty(nextToken)) {
                        builder.nextToken(nextToken);
                    }
                    Integer limit = getOptionalHeader(exchange, EventbridgeConstants.LIMIT, Integer.class);
                    if (ObjectHelper.isNotEmpty(limit)) {
                        builder.limit(limit);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.listRules(builder.build());
                },
                "List Rules",
                (ListRulesResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.NEXT_TOKEN, response.nextToken());
                    message.setHeader(EventbridgeConstants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
                });
    }

    private void describeRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeRuleRequest.class,
                eventbridgeClient::describeRule,
                () -> {
                    DescribeRuleRequest.Builder builder = DescribeRuleRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.name(ruleName);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.describeRule(builder.build());
                },
                "Describe Rule",
                (DescribeRuleResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.RULE_ARN, response.arn());
                });
    }

    private void listTargetsByRule(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListTargetsByRuleRequest.class,
                eventbridgeClient::listTargetsByRule,
                () -> {
                    ListTargetsByRuleRequest.Builder builder = ListTargetsByRuleRequest.builder();
                    String ruleName = getOptionalHeader(exchange, EventbridgeConstants.RULE_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(ruleName)) {
                        builder.rule(ruleName);
                    }
                    String nextToken = getOptionalHeader(exchange, EventbridgeConstants.NEXT_TOKEN, String.class);
                    if (ObjectHelper.isNotEmpty(nextToken)) {
                        builder.nextToken(nextToken);
                    }
                    Integer limit = getOptionalHeader(exchange, EventbridgeConstants.LIMIT, Integer.class);
                    if (ObjectHelper.isNotEmpty(limit)) {
                        builder.limit(limit);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.listTargetsByRule(builder.build());
                },
                "List Targets by Rule",
                (ListTargetsByRuleResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.NEXT_TOKEN, response.nextToken());
                    message.setHeader(EventbridgeConstants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
                });
    }

    private void listRuleNamesByTarget(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListRuleNamesByTargetRequest.class,
                eventbridgeClient::listRuleNamesByTarget,
                () -> {
                    ListRuleNamesByTargetRequest.Builder builder = ListRuleNamesByTargetRequest.builder();
                    String targetArn = getOptionalHeader(exchange, EventbridgeConstants.TARGET_ARN, String.class);
                    if (ObjectHelper.isNotEmpty(targetArn)) {
                        builder.targetArn(targetArn);
                    }
                    String nextToken = getOptionalHeader(exchange, EventbridgeConstants.NEXT_TOKEN, String.class);
                    if (ObjectHelper.isNotEmpty(nextToken)) {
                        builder.nextToken(nextToken);
                    }
                    Integer limit = getOptionalHeader(exchange, EventbridgeConstants.LIMIT, Integer.class);
                    if (ObjectHelper.isNotEmpty(limit)) {
                        builder.limit(limit);
                    }
                    builder.eventBusName(getConfiguration().getEventbusName());
                    return eventbridgeClient.listRuleNamesByTarget(builder.build());
                },
                "List Rule Names by Target",
                (ListRuleNamesByTargetResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.NEXT_TOKEN, response.nextToken());
                    message.setHeader(EventbridgeConstants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
                });
    }

    private void putEvent(EventBridgeClient eventbridgeClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                PutEventsRequest.class,
                eventbridgeClient::putEvents,
                () -> {
                    PutEventsRequest.Builder builder = PutEventsRequest.builder();
                    PutEventsRequestEntry.Builder entryBuilder = PutEventsRequestEntry.builder();
                    String resourcesArn = getRequiredHeader(exchange, EventbridgeConstants.EVENT_RESOURCES_ARN, String.class,
                            "At least one resource ARN must be specified");
                    entryBuilder.resources(Stream.of(resourcesArn.split(",")).toList());
                    String detailType = getRequiredHeader(exchange, EventbridgeConstants.EVENT_DETAIL_TYPE, String.class,
                            "Detail Type must be specified");
                    entryBuilder.detailType(detailType);
                    String source = getRequiredHeader(exchange, EventbridgeConstants.EVENT_SOURCE, String.class,
                            "Source must be specified");
                    entryBuilder.source(source);
                    entryBuilder.eventBusName(getConfiguration().getEventbusName());
                    try {
                        entryBuilder.detail(exchange.getMessage().getMandatoryBody(String.class));
                    } catch (InvalidPayloadException e) {
                        throw new RuntimeException(e);
                    }
                    builder.entries(entryBuilder.build());
                    return eventbridgeClient.putEvents(builder.build());
                },
                "Put Events",
                (PutEventsResponse response, Message message) -> {
                    message.setHeader(EventbridgeConstants.FAILED_ENTRY_COUNT, response.failedEntryCount());
                });
    }

    @Override
    public EventbridgeEndpoint getEndpoint() {
        return (EventbridgeEndpoint) super.getEndpoint();
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes an EventBridge operation with POJO request support.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName)
            throws InvalidPayloadException {
        executeOperation(exchange, requestClass, pojoExecutor, headerExecutor, operationName, null);
    }

    /**
     * Executes an EventBridge operation with POJO request support and optional response post-processing.
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName,
            BiConsumer<RES, Message> responseProcessor)
            throws InvalidPayloadException {

        RES result;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (requestClass.isInstance(payload)) {
                try {
                    result = pojoExecutor.apply(requestClass.cast(payload));
                } catch (AwsServiceException ase) {
                    LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            } else {
                throw new IllegalArgumentException(
                        String.format("Expected body of type %s but was %s",
                                requestClass.getName(),
                                ObjectHelper.isNotEmpty(payload) ? payload.getClass().getName() : "null"));
            }
        } else {
            try {
                result = headerExecutor.get();
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (ObjectHelper.isNotEmpty(responseProcessor)) {
            responseProcessor.accept(result, message);
        }
    }

    /**
     * Gets a required header value or throws an IllegalArgumentException.
     */
    private <T> T getRequiredHeader(Exchange exchange, String headerName, Class<T> headerType, String errorMessage) {
        T value = exchange.getIn().getHeader(headerName, headerType);
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    /**
     * Gets an optional header value.
     */
    private <T> T getOptionalHeader(Exchange exchange, String headerName, Class<T> headerType) {
        return exchange.getIn().getHeader(headerName, headerType);
    }

    @Override
    protected void doStart() throws Exception {
        // health-check is optional so discover and resolve
        healthCheckRepository = HealthCheckHelper.getHealthCheckRepository(
                getEndpoint().getCamelContext(),
                "producers",
                WritableHealthCheckRepository.class);

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new EventbridgeProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
