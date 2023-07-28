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
package org.apache.camel.component.aws2.msk;

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
import software.amazon.awssdk.services.kafka.KafkaClient;
import software.amazon.awssdk.services.kafka.model.BrokerNodeGroupInfo;
import software.amazon.awssdk.services.kafka.model.CreateClusterRequest;
import software.amazon.awssdk.services.kafka.model.CreateClusterResponse;
import software.amazon.awssdk.services.kafka.model.DeleteClusterRequest;
import software.amazon.awssdk.services.kafka.model.DeleteClusterResponse;
import software.amazon.awssdk.services.kafka.model.DescribeClusterRequest;
import software.amazon.awssdk.services.kafka.model.DescribeClusterResponse;
import software.amazon.awssdk.services.kafka.model.ListClustersRequest;
import software.amazon.awssdk.services.kafka.model.ListClustersResponse;

/**
 * A Producer which sends messages to the Amazon MSK Service <a href="http://aws.amazon.com/msk/">AWS MSK</a>
 */
public class MSK2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MSK2Producer.class);

    private transient String mskProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public MSK2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listClusters:
                listClusters(getEndpoint().getMskClient(), exchange);
                break;
            case createCluster:
                createCluster(getEndpoint().getMskClient(), exchange);
                break;
            case deleteCluster:
                deleteCluster(getEndpoint().getMskClient(), exchange);
                break;
            case describeCluster:
                describeCluster(getEndpoint().getMskClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private MSK2Operations determineOperation(Exchange exchange) {
        MSK2Operations operation = exchange.getIn().getHeader(MSK2Constants.OPERATION, MSK2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected MSK2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (mskProducerToString == null) {
            mskProducerToString = "MSKProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return mskProducerToString;
    }

    @Override
    public MSK2Endpoint getEndpoint() {
        return (MSK2Endpoint) super.getEndpoint();
    }

    private void listClusters(KafkaClient mskClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListClustersRequest) {
                ListClustersResponse result;
                try {
                    result = mskClient.listClusters((ListClustersRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListClustersRequest.Builder builder = ListClustersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.CLUSTERS_FILTER))) {
                String filter = exchange.getIn().getHeader(MSK2Constants.CLUSTERS_FILTER, String.class);
                builder.clusterNameFilter(filter);
            }
            ListClustersResponse result;
            try {
                result = mskClient.listClusters(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createCluster(KafkaClient mskClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateClusterRequest) {
                CreateClusterResponse response;
                try {
                    response = mskClient.createCluster((CreateClusterRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            CreateClusterRequest.Builder builder = CreateClusterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.CLUSTER_NAME))) {
                String name = exchange.getIn().getHeader(MSK2Constants.CLUSTER_NAME, String.class);
                builder.clusterName(name);
            } else {
                throw new IllegalArgumentException("Cluster Name must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.CLUSTER_KAFKA_VERSION))) {
                String version = exchange.getIn().getHeader(MSK2Constants.CLUSTER_KAFKA_VERSION, String.class);
                builder.kafkaVersion(version);
            } else {
                throw new IllegalArgumentException("Kafka Version must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.BROKER_NODES_NUMBER))) {
                Integer nodesNumber = exchange.getIn().getHeader(MSK2Constants.BROKER_NODES_NUMBER, Integer.class);
                builder.numberOfBrokerNodes(nodesNumber);
            } else {
                throw new IllegalArgumentException("Kafka Version must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.BROKER_NODES_GROUP_INFO))) {
                BrokerNodeGroupInfo brokerNodesGroupInfo
                        = exchange.getIn().getHeader(MSK2Constants.BROKER_NODES_GROUP_INFO, BrokerNodeGroupInfo.class);
                builder.brokerNodeGroupInfo(brokerNodesGroupInfo);
            } else {
                throw new IllegalArgumentException("BrokerNodeGroupInfo must be specified");
            }
            CreateClusterResponse response;
            try {
                response = mskClient.createCluster(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void deleteCluster(KafkaClient mskClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteClusterRequest) {
                DeleteClusterResponse result;
                try {
                    result = mskClient.deleteCluster((DeleteClusterRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteClusterRequest.Builder builder = DeleteClusterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.CLUSTER_ARN))) {
                String arn = exchange.getIn().getHeader(MSK2Constants.CLUSTER_ARN, String.class);
                builder.clusterArn(arn);
            } else {
                throw new IllegalArgumentException("Cluster ARN must be specified");
            }
            DeleteClusterResponse result;
            try {
                result = mskClient.deleteCluster(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeCluster(KafkaClient mskClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeClusterRequest) {
                DescribeClusterResponse result;
                try {
                    result = mskClient.describeCluster((DescribeClusterRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeClusterRequest.Builder builder = DescribeClusterRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSK2Constants.CLUSTER_ARN))) {
                String arn = exchange.getIn().getHeader(MSK2Constants.CLUSTER_ARN, String.class);
                builder.clusterArn(arn);
            } else {
                throw new IllegalArgumentException("Cluster ARN must be specified");
            }
            DescribeClusterResponse result;
            try {
                result = mskClient.describeCluster(builder.build());
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
            producerHealthCheck = new MSK2ProducerHealthCheck(getEndpoint(), id);
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
