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
package org.apache.camel.component.aws.ecs;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.CreateClusterRequest;
import com.amazonaws.services.ecs.model.CreateClusterResult;
import com.amazonaws.services.ecs.model.DeleteClusterRequest;
import com.amazonaws.services.ecs.model.DeleteClusterResult;
import com.amazonaws.services.ecs.model.DescribeClustersRequest;
import com.amazonaws.services.ecs.model.DescribeClustersResult;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon ECS Service
 * <a href="http://aws.amazon.com/ecs/">AWS ECS</a>
 */
public class ECSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(ECSProducer.class);

    private transient String ecsProducerToString;

    public ECSProducer(Endpoint endpoint) {
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

    private ECSOperations determineOperation(Exchange exchange) {
        ECSOperations operation = exchange.getIn().getHeader(ECSConstants.OPERATION, ECSOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected ECSConfiguration getConfiguration() {
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
    public ECSEndpoint getEndpoint() {
        return (ECSEndpoint)super.getEndpoint();
    }

    private void listClusters(AmazonECS ecsClient, Exchange exchange) {
        ListClustersRequest request = new ListClustersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECSConstants.MAX_RESULTS))) {
            int maxRes = exchange.getIn().getHeader(ECSConstants.MAX_RESULTS, Integer.class);
            request.withMaxResults(maxRes);
        }
        ListClustersResult result;
        try {
            result = ecsClient.listClusters();
        } catch (AmazonServiceException ase) {
            LOG.trace("List Clusters command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createCluster(AmazonECS ecsClient, Exchange exchange) {
        CreateClusterRequest request = new CreateClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME, String.class);
            request.withClusterName(name);
        }
        CreateClusterResult result;
        try {
            result = ecsClient.createCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeCluster(AmazonECS ecsClient, Exchange exchange) {
        DescribeClustersRequest request = new DescribeClustersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME))) {
            String clusterName = exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME, String.class);
            request.withClusters(clusterName);
        }
        DescribeClustersResult result;
        try {
            result = ecsClient.describeClusters(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Describe Clusters command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteCluster(AmazonECS ecsClient, Exchange exchange) {
        DeleteClusterRequest request = new DeleteClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(ECSConstants.CLUSTER_NAME, String.class);
            request.withCluster(name);
        } else {
            throw new IllegalArgumentException("Cluster name must be specified");
        }
        DeleteClusterResult result;
        try {
            result = ecsClient.deleteCluster(request);
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
