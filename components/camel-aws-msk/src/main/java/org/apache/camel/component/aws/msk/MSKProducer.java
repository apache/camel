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
package org.apache.camel.component.aws.msk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.kafka.AWSKafka;
import com.amazonaws.services.kafka.model.BrokerNodeGroupInfo;
import com.amazonaws.services.kafka.model.CreateClusterRequest;
import com.amazonaws.services.kafka.model.CreateClusterResult;
import com.amazonaws.services.kafka.model.DeleteClusterRequest;
import com.amazonaws.services.kafka.model.DeleteClusterResult;
import com.amazonaws.services.kafka.model.DescribeClusterRequest;
import com.amazonaws.services.kafka.model.DescribeClusterResult;
import com.amazonaws.services.kafka.model.ListClustersRequest;
import com.amazonaws.services.kafka.model.ListClustersResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon MSK Service
 * <a href="http://aws.amazon.com/msk/">AWS MSK</a>
 */
public class MSKProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MSKProducer.class);

    private transient String mskProducerToString;

    public MSKProducer(Endpoint endpoint) {
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

    private MSKOperations determineOperation(Exchange exchange) {
        MSKOperations operation = exchange.getIn().getHeader(MSKConstants.OPERATION, MSKOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected MSKConfiguration getConfiguration() {
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
    public MSKEndpoint getEndpoint() {
        return (MSKEndpoint)super.getEndpoint();
    }

    private void listClusters(AWSKafka mskClient, Exchange exchange) {
        ListClustersRequest request = new ListClustersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTERS_FILTER))) {
            String filter = exchange.getIn().getHeader(MSKConstants.CLUSTERS_FILTER, String.class);
            request.withClusterNameFilter(filter);
        }
        ListClustersResult result;
        try {
            result = mskClient.listClusters(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("List Clusters command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createCluster(AWSKafka mskClient, Exchange exchange) {
        CreateClusterRequest request = new CreateClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(MSKConstants.CLUSTER_NAME, String.class);
            request.withClusterName(name);
        } else {
            throw new IllegalArgumentException("Cluster Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTER_KAFKA_VERSION))) {
            String version = exchange.getIn().getHeader(MSKConstants.CLUSTER_KAFKA_VERSION, String.class);
            request.withKafkaVersion(version);
        } else {
            throw new IllegalArgumentException("Kafka Version must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.BROKER_NODES_NUMBER))) {
            Integer nodesNumber = exchange.getIn().getHeader(MSKConstants.BROKER_NODES_NUMBER, Integer.class);
            request.withNumberOfBrokerNodes(nodesNumber);
        } else {
            throw new IllegalArgumentException("Kafka Version must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.BROKER_NODES_GROUP_INFO))) {
            BrokerNodeGroupInfo brokerNodesGroupInfo = exchange.getIn().getHeader(MSKConstants.BROKER_NODES_GROUP_INFO, BrokerNodeGroupInfo.class);
            request.withBrokerNodeGroupInfo(brokerNodesGroupInfo);
        } else {
            throw new IllegalArgumentException("BrokerNodeGroupInfo must be specified");
        }
        CreateClusterResult result;
        try {
            result = mskClient.createCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteCluster(AWSKafka mskClient, Exchange exchange) {
        DeleteClusterRequest request = new DeleteClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTER_ARN))) {
            String arn = exchange.getIn().getHeader(MSKConstants.CLUSTER_ARN, String.class);
            request.withClusterArn(arn);
        } else {
            throw new IllegalArgumentException("Cluster ARN must be specified");
        }
        DeleteClusterResult result;
        try {
            result = mskClient.deleteCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeCluster(AWSKafka mskClient, Exchange exchange) {
        DescribeClusterRequest request = new DescribeClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MSKConstants.CLUSTER_ARN))) {
            String arn = exchange.getIn().getHeader(MSKConstants.CLUSTER_ARN, String.class);
            request.withClusterArn(arn);
        } else {
            throw new IllegalArgumentException("Cluster ARN must be specified");
        }
        DescribeClusterResult result;
        try {
            result = mskClient.describeCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
