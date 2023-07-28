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
import software.amazon.awssdk.services.ecs.model.ListClustersRequest.Builder;
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
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListClustersRequest) {
                ListClustersResponse result;
                try {
                    ListClustersRequest request = (ListClustersRequest) payload;
                    result = ecsClient.listClusters(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Builder builder = ListClustersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECS2Constants.MAX_RESULTS))) {
                int maxRes = exchange.getIn().getHeader(ECS2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxRes);
            }
            ListClustersResponse result;
            try {
                ListClustersRequest request = builder.build();
                result = ecsClient.listClusters(request);
            } catch (AwsServiceException ase) {
                LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateClusterRequest) {
                CreateClusterResponse result;
                try {
                    CreateClusterRequest request = (CreateClusterRequest) payload;
                    result = ecsClient.createCluster(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateClusterRequest.Builder builder = CreateClusterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME))) {
                String name = exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME, String.class);
                builder.clusterName(name);
            }
            CreateClusterResponse result;
            try {
                CreateClusterRequest request = builder.build();
                result = ecsClient.createCluster(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Create Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeClustersRequest) {
                DescribeClustersResponse result;
                try {
                    DescribeClustersRequest request = (DescribeClustersRequest) payload;
                    result = ecsClient.describeClusters(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeClustersRequest.Builder builder = DescribeClustersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME))) {
                String clusterName = exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME, String.class);
                builder.clusters(clusterName);
            }
            DescribeClustersResponse result;
            try {
                DescribeClustersRequest request = builder.build();
                result = ecsClient.describeClusters(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteCluster(EcsClient ecsClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteClusterRequest) {
                DeleteClusterResponse result;
                try {
                    DeleteClusterRequest request = (DeleteClusterRequest) payload;
                    result = ecsClient.deleteCluster(request);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteClusterRequest.Builder builder = DeleteClusterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME))) {
                String name = exchange.getIn().getHeader(ECS2Constants.CLUSTER_NAME, String.class);
                builder.cluster(name);
            } else {
                throw new IllegalArgumentException("Cluster name must be specified");
            }
            DeleteClusterResponse result;
            try {
                DeleteClusterRequest request = builder.build();
                result = ecsClient.deleteCluster(request);
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
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
            producerHealthCheck = new ECS2ProducerHealthCheck(getEndpoint(), id);
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
