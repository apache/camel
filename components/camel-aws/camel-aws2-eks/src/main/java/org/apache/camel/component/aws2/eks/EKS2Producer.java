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
package org.apache.camel.component.aws2.eks;

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
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.eks.model.CreateClusterRequest;
import software.amazon.awssdk.services.eks.model.CreateClusterResponse;
import software.amazon.awssdk.services.eks.model.DeleteClusterRequest;
import software.amazon.awssdk.services.eks.model.DeleteClusterResponse;
import software.amazon.awssdk.services.eks.model.DescribeClusterRequest;
import software.amazon.awssdk.services.eks.model.DescribeClusterResponse;
import software.amazon.awssdk.services.eks.model.ListClustersRequest;
import software.amazon.awssdk.services.eks.model.ListClustersResponse;
import software.amazon.awssdk.services.eks.model.VpcConfigRequest;

/**
 * A Producer which sends messages to the Amazon EKS Service <a href="http://aws.amazon.com/eks/">AWS EKS</a>
 */
public class EKS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EKS2Producer.class);
    private transient String eksProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public EKS2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listClusters:
                listClusters(getEndpoint().getEksClient(), exchange);
                break;
            case describeCluster:
                describeCluster(getEndpoint().getEksClient(), exchange);
                break;
            case createCluster:
                createCluster(getEndpoint().getEksClient(), exchange);
                break;
            case deleteCluster:
                deleteCluster(getEndpoint().getEksClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private EKS2Operations determineOperation(Exchange exchange) {
        EKS2Operations operation = exchange.getIn().getHeader(EKS2Constants.OPERATION, EKS2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected EKS2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (eksProducerToString == null) {
            eksProducerToString = "EKSProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return eksProducerToString;
    }

    @Override
    public EKS2Endpoint getEndpoint() {
        return (EKS2Endpoint) super.getEndpoint();
    }

    private void listClusters(EksClient eksClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListClustersRequest.class,
                eksClient::listClusters,
                () -> {
                    ListClustersRequest.Builder builder = ListClustersRequest.builder();
                    Integer maxResults = getOptionalHeader(exchange, EKS2Constants.MAX_RESULTS, Integer.class);
                    if (maxResults != null) {
                        builder.maxResults(maxResults);
                    }
                    String nextToken = getOptionalHeader(exchange, EKS2Constants.NEXT_TOKEN, String.class);
                    if (nextToken != null) {
                        builder.nextToken(nextToken);
                    }
                    return eksClient.listClusters(builder.build());
                },
                "List Clusters",
                (ListClustersResponse response, Message message) -> {
                    message.setHeader(EKS2Constants.NEXT_TOKEN, response.nextToken());
                    message.setHeader(EKS2Constants.IS_TRUNCATED, response.nextToken() != null);
                });
    }

    private void createCluster(EksClient eksClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateClusterRequest.class,
                eksClient::createCluster,
                () -> {
                    CreateClusterRequest.Builder builder = CreateClusterRequest.builder();
                    String clusterName = getOptionalHeader(exchange, EKS2Constants.CLUSTER_NAME, String.class);
                    if (clusterName != null) {
                        builder.name(clusterName);
                    }
                    String roleArn = getOptionalHeader(exchange, EKS2Constants.ROLE_ARN, String.class);
                    if (roleArn != null) {
                        builder.roleArn(roleArn);
                    }
                    VpcConfigRequest vpcConfig = getOptionalHeader(exchange, EKS2Constants.VPC_CONFIG, VpcConfigRequest.class);
                    if (vpcConfig != null) {
                        builder.resourcesVpcConfig(vpcConfig);
                    }
                    return eksClient.createCluster(builder.build());
                },
                "Create Cluster",
                (CreateClusterResponse response, Message message) -> {
                    if (response.cluster() != null) {
                        message.setHeader(EKS2Constants.CLUSTER_ARN, response.cluster().arn());
                    }
                });
    }

    private void describeCluster(EksClient eksClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DescribeClusterRequest.class,
                eksClient::describeCluster,
                () -> {
                    String clusterName = getRequiredHeader(exchange, EKS2Constants.CLUSTER_NAME, String.class,
                            "Cluster name must be specified");
                    return eksClient.describeCluster(DescribeClusterRequest.builder().name(clusterName).build());
                },
                "Describe Cluster",
                (DescribeClusterResponse response, Message message) -> {
                    if (response.cluster() != null) {
                        message.setHeader(EKS2Constants.CLUSTER_ARN, response.cluster().arn());
                    }
                });
    }

    private void deleteCluster(EksClient eksClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteClusterRequest.class,
                eksClient::deleteCluster,
                () -> {
                    String clusterName = getRequiredHeader(exchange, EKS2Constants.CLUSTER_NAME, String.class,
                            "Cluster name must be specified");
                    return eksClient.deleteCluster(DeleteClusterRequest.builder().name(clusterName).build());
                },
                "Delete Cluster",
                (DeleteClusterResponse response, Message message) -> {
                    if (response.cluster() != null) {
                        message.setHeader(EKS2Constants.CLUSTER_ARN, response.cluster().arn());
                    }
                });
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes an EKS operation with POJO request support.
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
     * Executes an EKS operation with POJO request support and optional response post-processing.
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
                                payload != null ? payload.getClass().getName() : "null"));
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
        if (responseProcessor != null) {
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

        if (healthCheckRepository != null) {
            String id = getEndpoint().getId();
            producerHealthCheck = new EKS2ProducerHealthCheck(getEndpoint(), id);
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
