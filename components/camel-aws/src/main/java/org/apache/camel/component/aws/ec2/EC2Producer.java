/**
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
package org.apache.camel.component.aws.ec2;

import java.util.Collection;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.opsworks.model.StartInstanceRequest;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon EC2 Service
 * <a href="http://aws.amazon.com/ec2/">AWS EC2</a>
 */
public class EC2Producer extends DefaultProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger(EC2Producer.class);
    
    public EC2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
        case createAndRunInstances:
            createAndRunInstance(getEndpoint().getEc2Client(), exchange);
            break;
        case startInstances:
            startInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case stopInstances:
            stopInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case terminateInstances:
            terminateInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case describeInstances:
            describeInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case describeInstancesStatus:
            describeInstancesStatus(getEndpoint().getEc2Client(), exchange);
            break;
        default:
            throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private EC2Operations determineOperation(Exchange exchange) {
        EC2Operations operation = exchange.getIn().getHeader(EC2Constants.OPERATION, EC2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected EC2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        return "EC2Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
    }

    @Override
    public EC2Endpoint getEndpoint() {
        return (EC2Endpoint) super.getEndpoint();
    }
    
    private void createAndRunInstance(AmazonEC2Client ec2Client, Exchange exchange) {
        String ami;
        InstanceType instanceType;
        int minCount;
        int maxCount;
        boolean monitoring;
        String kernelId;
        boolean ebsOptimized;
        Collection securityGroups;
        RunInstancesRequest request = new RunInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.IMAGE_ID))) {
            ami = exchange.getIn().getHeader(EC2Constants.IMAGE_ID, String.class);
            request.withImageId(ami);
        } else {
            throw new IllegalArgumentException("AMI must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_TYPE))) {
            instanceType = exchange.getIn().getHeader(EC2Constants.INSTANCE_TYPE, InstanceType.class);
            request.withInstanceType(instanceType.toString());
        } else {
            throw new IllegalArgumentException("Instance Type must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_MIN_COUNT))) {
            minCount = exchange.getIn().getHeader(EC2Constants.INSTANCE_MIN_COUNT, Integer.class);
            request.withMinCount(minCount);
        } else {
            throw new IllegalArgumentException("Min instances count must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_MAX_COUNT))) {
            maxCount = exchange.getIn().getHeader(EC2Constants.INSTANCE_MAX_COUNT, Integer.class);
            request.withMaxCount(maxCount);
        } else {
            throw new IllegalArgumentException("Max instances count must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_MONITORING))) {
            monitoring = exchange.getIn().getHeader(EC2Constants.INSTANCE_MONITORING, Boolean.class);
            request.withMonitoring(monitoring);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_KERNEL_ID))) {
            kernelId = exchange.getIn().getHeader(EC2Constants.INSTANCE_KERNEL_ID, String.class);
            request.withKernelId(kernelId);
        }       
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_EBS_OPTIMIZED))) {
            ebsOptimized = exchange.getIn().getHeader(EC2Constants.INSTANCE_EBS_OPTIMIZED, Boolean.class);
            request.withEbsOptimized(ebsOptimized);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCE_SECURITY_GROUPS))) {
            securityGroups = exchange.getIn().getHeader(EC2Constants.INSTANCE_SECURITY_GROUPS, Collection.class);
            request.withSecurityGroups(securityGroups);
        }
        RunInstancesResult result;
        try {
            result = ec2Client.runInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Run Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);
    }
    
    private void startInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        StartInstancesRequest request = new StartInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        StartInstancesResult result;
        try {
            result = ec2Client.startInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Start Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);        
    }
    
    private void stopInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        StopInstancesRequest request = new StopInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        StopInstancesResult result;
        try {
            result = ec2Client.stopInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Stop Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);        
    }
    
    private void terminateInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        TerminateInstancesRequest request = new TerminateInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        TerminateInstancesResult result;
        try {
            result = ec2Client.terminateInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Terminate Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);        
    }
    
    private void describeInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } 
        DescribeInstancesResult result;
        try {
            result = ec2Client.describeInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Describe Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);        
    }
    
    private void describeInstancesStatus(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } 
        DescribeInstanceStatusResult result;
        try {
            result = ec2Client.describeInstanceStatus(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Describe Instances Status command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        exchange.getIn().setBody(result);        
    }
}