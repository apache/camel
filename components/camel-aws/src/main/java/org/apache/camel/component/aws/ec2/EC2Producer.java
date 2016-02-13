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

import java.util.Arrays;
import java.util.Collection;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.MonitorInstancesRequest;
import com.amazonaws.services.ec2.model.MonitorInstancesResult;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesResult;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesResult;
import com.amazonaws.services.ec2.model.UnmonitorInstancesRequest;
import com.amazonaws.services.ec2.model.UnmonitorInstancesResult;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
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
    
    private transient String ec2ProducerToString;

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
        case rebootInstances:
            rebootInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case monitorInstances:
            monitorInstances(getEndpoint().getEc2Client(), exchange);
            break;
        case unmonitorInstances:
            unmonitorInstances(getEndpoint().getEc2Client(), exchange);
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
        if (ec2ProducerToString == null) {
            ec2ProducerToString = "EC2Producer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return ec2ProducerToString;
    }

    @Override
    public EC2Endpoint getEndpoint() {
        return (EC2Endpoint) super.getEndpoint();
    }

    private Message getMessageForResponse(final Exchange exchange) {
        if (exchange.getPattern().isOutCapable()) {
            Message out = exchange.getOut();
            out.copyFrom(exchange.getIn());
            return out;
        }
        return exchange.getIn();
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
        String keyName;
        String clientToken;
        Placement placement;
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
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_KEY_PAIR))) {
            keyName = exchange.getIn().getHeader(EC2Constants.INSTANCES_KEY_PAIR, String.class);
            request.withKeyName(keyName);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_CLIENT_TOKEN))) {
            clientToken = exchange.getIn().getHeader(EC2Constants.INSTANCES_CLIENT_TOKEN, String.class);
            request.withClientToken(clientToken);
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_PLACEMENT))) {
            placement = exchange.getIn().getHeader(EC2Constants.INSTANCES_PLACEMENT, Placement.class);
            request.withPlacement(placement);
        }
        RunInstancesResult result;
        try {
            result = ec2Client.runInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Run Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        LOG.trace("Creating and running instances with ami [{}] and instance type {}", ami, instanceType.toString());
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
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
        LOG.trace("Starting instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
        Message message = getMessageForResponse(exchange);
        message.setBody(result);        
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
        LOG.trace("Stopping instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
        Message message = getMessageForResponse(exchange);
        message.setBody(result);        
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
        LOG.trace("Terminating instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
        Message message = getMessageForResponse(exchange);
        message.setBody(result);        
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
        Message message = getMessageForResponse(exchange);
        message.setBody(result);        
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
        Message message = getMessageForResponse(exchange);
        message.setBody(result);        
    }
    
    private void rebootInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        RebootInstancesRequest request = new RebootInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        try {
            LOG.trace("Rebooting instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            ec2Client.rebootInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Reboot Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
    }
    
    private void monitorInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        MonitorInstancesRequest request = new MonitorInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        MonitorInstancesResult result;
        try {
            result = ec2Client.monitorInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Monitor Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        LOG.trace("Start Monitoring instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
        Message message = getMessageForResponse(exchange);
        message.setBody(result); 
    }
    
    private void unmonitorInstances(AmazonEC2Client ec2Client, Exchange exchange) {
        Collection instanceIds;
        UnmonitorInstancesRequest request = new UnmonitorInstancesRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS))) {
            instanceIds = exchange.getIn().getHeader(EC2Constants.INSTANCES_IDS, Collection.class);
            request.withInstanceIds(instanceIds);
        } else {
            throw new IllegalArgumentException("Instances Ids must be specified");
        }
        UnmonitorInstancesResult result;
        try {
            result = ec2Client.unmonitorInstances(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Unmonitor Instances command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        LOG.trace("Stop Monitoring instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
        Message message = getMessageForResponse(exchange);
        message.setBody(result); 
    }
}