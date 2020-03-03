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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
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
 * A Producer which sends messages to the Amazon EKS Service
 * <a href="http://aws.amazon.com/eks/">AWS EKS</a>
 */
public class EKS2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(EKS2Producer.class);
    private transient String eksProducerToString;

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
        return (EKS2Endpoint)super.getEndpoint();
    }

    private void listClusters(EksClient eksClient, Exchange exchange) {
        ListClustersRequest.Builder builder = ListClustersRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.MAX_RESULTS))) {
            int maxRes = exchange.getIn().getHeader(EKS2Constants.MAX_RESULTS, Integer.class);
            builder.maxResults(maxRes);
        }
        ListClustersResponse result;
        try {
            result = eksClient.listClusters(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("List Clusters command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createCluster(EksClient eksClient, Exchange exchange) {
        CreateClusterRequest.Builder builder = CreateClusterRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME, String.class);
            builder.name(name);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.ROLE_ARN))) {
            String roleArn = exchange.getIn().getHeader(EKS2Constants.ROLE_ARN, String.class);
            builder.roleArn(roleArn);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.VPC_CONFIG))) {
            VpcConfigRequest vpcConfig = exchange.getIn().getHeader(EKS2Constants.VPC_CONFIG, VpcConfigRequest.class);
            builder.resourcesVpcConfig(vpcConfig);
        }
        CreateClusterResponse result;
        try {
            result = eksClient.createCluster(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Create Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeCluster(EksClient eksClient, Exchange exchange) {
        DescribeClusterRequest.Builder builder = DescribeClusterRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME, String.class);
            builder.name(name);
        } else {
            throw new IllegalArgumentException("Cluster name must be specified");
        }
        DescribeClusterResponse result;
        try {
            result = eksClient.describeCluster(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Describe Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteCluster(EksClient eksClient, Exchange exchange) {
        DeleteClusterRequest.Builder builder = DeleteClusterRequest.builder();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME))) {
            String name = exchange.getIn().getHeader(EKS2Constants.CLUSTER_NAME, String.class);
            builder.name(name);
        } else {
            throw new IllegalArgumentException("Cluster name must be specified");
        }
        DeleteClusterResponse result;
        try {
            result = eksClient.deleteCluster(builder.build());
        } catch (AwsServiceException ase) {
            LOG.trace("Delete Cluster command returned the error code {}", ase.awsErrorDetails().errorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
