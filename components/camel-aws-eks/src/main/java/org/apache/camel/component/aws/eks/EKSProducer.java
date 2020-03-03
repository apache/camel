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
package org.apache.camel.component.aws.eks;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.eks.AmazonEKS;
import com.amazonaws.services.eks.model.CreateClusterRequest;
import com.amazonaws.services.eks.model.CreateClusterResult;
import com.amazonaws.services.eks.model.DeleteClusterRequest;
import com.amazonaws.services.eks.model.DeleteClusterResult;
import com.amazonaws.services.eks.model.DescribeClusterRequest;
import com.amazonaws.services.eks.model.DescribeClusterResult;
import com.amazonaws.services.eks.model.ListClustersRequest;
import com.amazonaws.services.eks.model.ListClustersResult;
import com.amazonaws.services.eks.model.VpcConfigRequest;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon EKS Service
 * <a href="http://aws.amazon.com/eks/">AWS EKS</a>
 */
public class EKSProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EKSProducer.class);
    private transient String eksProducerToString;

    public EKSProducer(Endpoint endpoint) {
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

    private EKSOperations determineOperation(Exchange exchange) {
        EKSOperations operation = exchange.getIn().getHeader(EKSConstants.OPERATION, EKSOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected EKSConfiguration getConfiguration() {
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
    public EKSEndpoint getEndpoint() {
        return (EKSEndpoint)super.getEndpoint();
    }

    private void listClusters(AmazonEKS eksClient, Exchange exchange) {
        ListClustersRequest request = new ListClustersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.MAX_RESULTS))) {
            int maxRes = exchange.getIn().getHeader(EKSConstants.MAX_RESULTS, Integer.class);
            request.withMaxResults(maxRes);
        }
        ListClustersResult result;
        try {
            result = eksClient.listClusters(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("List Clusters command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createCluster(AmazonEKS eksClient, Exchange exchange) {
        CreateClusterRequest request = new CreateClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME, String.class);
            request.withName(name);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.ROLE_ARN))) {
            String roleArn = exchange.getIn().getHeader(EKSConstants.ROLE_ARN, String.class);
            request.withRoleArn(roleArn);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.VPC_CONFIG))) {
            VpcConfigRequest vpcConfig = exchange.getIn().getHeader(EKSConstants.VPC_CONFIG, VpcConfigRequest.class);
            request.withResourcesVpcConfig(vpcConfig);
        }
        CreateClusterResult result;
        try {
            result = eksClient.createCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeCluster(AmazonEKS eksClient, Exchange exchange) {
        DescribeClusterRequest request = new DescribeClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME, String.class);
            request.withName(name);
        } else {
            throw new IllegalArgumentException("Cluster name must be specified");
        }
        DescribeClusterResult result;
        try {
            result = eksClient.describeCluster(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Describe Cluster command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteCluster(AmazonEKS eksClient, Exchange exchange) {
        DeleteClusterRequest request = new DeleteClusterRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKSConstants.CLUSTER_NAME, String.class);
            request.withName(name);
        } else {
            throw new IllegalArgumentException("Cluster name must be specified");
        }
        DeleteClusterResult result;
        try {
            result = eksClient.deleteCluster(request);
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
