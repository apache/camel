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
package org.apache.camel.component.aws2.ecs;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.CreateClusterRequest;
import software.amazon.awssdk.services.ecs.model.CreateClusterResponse;
import software.amazon.awssdk.services.ecs.model.DeleteClusterRequest;
import software.amazon.awssdk.services.ecs.model.DeleteClusterResponse;
import software.amazon.awssdk.services.ecs.model.DescribeClustersRequest;
import software.amazon.awssdk.services.ecs.model.DescribeClustersResponse;
import software.amazon.awssdk.services.ecs.model.ListClustersRequest;
import software.amazon.awssdk.services.ecs.model.ListClustersResponse;

/**
 * A Producer which sends messages to the Amazon ECS Service SDK v2 <a href="http://aws.amazon.com/ecs/">AWS ECS</a>
 */
public class ECS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ECS2Producer.class);

    private transient String ecsProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public ECS2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listClusters:
                listClusters(getEndpoint().getEcsClient(), exchange);
                break;
            case describeCluster:
                describeCluster(getEndpoint().getEcsClient(), exchange);
                break;
            case createCluster:
                createCluster(getEndpoint().getEcsClient(), exchange);
                break;
            case deleteCluster:
                deleteCluster(getEndpoint().getEcsClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private ECS2Operations determineOperation(Exchange exchange) {
        ECS2Operations operation = exchange.getIn().getHeader(ECS2Constants.OPERATION, ECS2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected ECS2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (ecsProducerToString == null) {
            ecsProducerToString = "ECSProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return ecsProducerToString;
    }

    @Override
    public ECS2Endpoint getEndpoint() {
        return (ECS2Endpoint) super.getEndpoint();
    }

    private void listClusters(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListClustersRequest.class,
                ecsClient::listClusters,
                () -> {
                    ListClustersRequest.Builder builder = ListClustersRequest.builder();
                    Integer maxResults = getOptionalHeader(exchange, ECS2Constants.MAX_RESULTS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxResults)) {
                        builder.maxResults(maxResults);
                    }
                    String nextToken = getOptionalHeader(exchange, ECS2Constants.NEXT_TOKEN, String.class);
                    if (ObjectHelper.isNotEmpty(nextToken)) {
                        builder.nextToken(nextToken);
                    }
                    return ecsClient.listClusters(builder.build());
                },
                "List Clusters",
                (ListClustersResponse response, Message message) -> {
                    message.setHeader(ECS2Constants.NEXT_TOKEN, response.nextToken());
                    message.setHeader(ECS2Constants.IS_TRUNCATED, ObjectHelper.isNotEmpty(response.nextToken()));
                });
    }

    private void createCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateClusterRequest.class,
                ecsClient::createCluster,
                () -> {
                    CreateClusterRequest.Builder builder = CreateClusterRequest.builder();
                    String clusterName = getOptionalHeader(exchange, ECS2Constants.CLUSTER_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(clusterName)) {
                        builder.clusterName(clusterName);
                    }
                    return ecsClient.createCluster(builder.build());
                },
                "Create Cluster",
                (CreateClusterResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.cluster())) {
                        message.setHeader(ECS2Constants.CLUSTER_ARN, response.cluster().clusterArn());
                    }
                });
    }

    private void describeCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeClustersRequest.class,
                ecsClient::describeClusters,
                () -> {
                    DescribeClustersRequest.Builder builder = DescribeClustersRequest.builder();
                    String clusterName = getOptionalHeader(exchange, ECS2Constants.CLUSTER_NAME, String.class);
                    if (ObjectHelper.isNotEmpty(clusterName)) {
                        builder.clusters(clusterName);
                    }
                    return ecsClient.describeClusters(builder.build());
                },
                "Describe Clusters",
                (DescribeClustersResponse response, Message message) -> {
                    if (response.hasClusters() && !response.clusters().isEmpty()) {
                        message.setHeader(ECS2Constants.CLUSTER_ARN, response.clusters().get(0).clusterArn());
                    }
                });
    }

    private void deleteCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteClusterRequest.class,
                ecsClient::deleteCluster,
                () -> {
                    String clusterName = getRequiredHeader(exchange, ECS2Constants.CLUSTER_NAME, String.class,
                            "Cluster name must be specified");
                    return ecsClient.deleteCluster(DeleteClusterRequest.builder().cluster(clusterName).build());
                },
                "Delete Cluster",
                (DeleteClusterResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.cluster())) {
                        message.setHeader(ECS2Constants.CLUSTER_ARN, response.cluster().clusterArn());
                    }
                });
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes an ECS operation with POJO request support.
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
     * Executes an ECS operation with POJO request support and optional response post-processing.
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
            producerHealthCheck = new ECS2ProducerHealthCheck(getEndpoint(), id);
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
