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
package org.apache.camel.component.aws2.ec2;

import java.util.Arrays;
import java.util.Collection;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.WritableHealthCheckRepository;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.CreateTagsResponse;
import software.amazon.awssdk.services.ec2.model.DeleteTagsRequest;
import software.amazon.awssdk.services.ec2.model.DeleteTagsResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstanceStatusResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.MonitorInstancesRequest;
import software.amazon.awssdk.services.ec2.model.MonitorInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Placement;
import software.amazon.awssdk.services.ec2.model.RebootInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesMonitoringEnabled;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StartInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesRequest;
import software.amazon.awssdk.services.ec2.model.UnmonitorInstancesResponse;

/**
 * A Producer which sends messages to the Amazon EC2 Service <a href="http://aws.amazon.com/ec2/">AWS EC2</a>
 */
public class AWS2EC2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AWS2EC2Producer.class);
    public static final String MISSING_INSTANCES_MESSAGE = "Instances Ids must be specified";

    private AWS2EC2ProducerHealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;
    private transient String ec2ProducerToString;

    public AWS2EC2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
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
            case createTags:
                createTags(getEndpoint().getEc2Client(), exchange);
                break;
            case deleteTags:
                deleteTags(getEndpoint().getEc2Client(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private AWS2EC2Operations determineOperation(Exchange exchange) {
        AWS2EC2Operations operation = exchange.getIn().getHeader(AWS2EC2Constants.OPERATION, AWS2EC2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected AWS2EC2Configuration getConfiguration() {
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
    public AWS2EC2Endpoint getEndpoint() {
        return (AWS2EC2Endpoint) super.getEndpoint();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void createAndRunInstance(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        String ami;
        InstanceType instanceType;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RunInstancesRequest) {
                RunInstancesResponse result;
                try {
                    result = ec2Client.runInstances((RunInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Run Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Creating and running instances requests performing");
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            RunInstancesRequest.Builder builder = RunInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.IMAGE_ID))) {
                ami = exchange.getIn().getHeader(AWS2EC2Constants.IMAGE_ID, String.class);
                builder.imageId(ami);
            } else {
                throw new IllegalArgumentException("AMI must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_TYPE))) {
                instanceType = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_TYPE, InstanceType.class);
                builder.instanceType(instanceType.toString());
            } else {
                throw new IllegalArgumentException("Instance Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT))) {
                int minCount = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MIN_COUNT, Integer.class);
                builder.minCount(minCount);
            } else {
                throw new IllegalArgumentException("Min instances count must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT))) {
                int maxCount = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MAX_COUNT, Integer.class);
                builder.maxCount(maxCount);
            } else {
                throw new IllegalArgumentException("Max instances count must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MONITORING))) {
                boolean monitoring = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_MONITORING, Boolean.class);
                RunInstancesMonitoringEnabled.Builder monitoringEnabled = RunInstancesMonitoringEnabled.builder();
                monitoringEnabled.enabled(monitoring);
                builder.monitoring(monitoringEnabled.build());
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_KERNEL_ID))) {
                String kernelId = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_KERNEL_ID, String.class);
                builder.kernelId(kernelId);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_EBS_OPTIMIZED))) {
                boolean ebsOptimized = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_EBS_OPTIMIZED, Boolean.class);
                builder.ebsOptimized(ebsOptimized);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_SECURITY_GROUPS))) {
                Collection securityGroups
                        = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCE_SECURITY_GROUPS, Collection.class);
                builder.securityGroups(securityGroups);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR))) {
                String keyName = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_KEY_PAIR, String.class);
                builder.keyName(keyName);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_CLIENT_TOKEN))) {
                String clientToken = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_CLIENT_TOKEN, String.class);
                builder.clientToken(clientToken);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_PLACEMENT))) {
                Placement placement = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_PLACEMENT, Placement.class);
                builder.placement(placement);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.SUBNET_ID))) {
                String subnetId = exchange.getIn().getHeader(AWS2EC2Constants.SUBNET_ID, String.class);
                builder.subnetId(subnetId);
            }
            RunInstancesResponse result;
            try {
                result = ec2Client.runInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Run Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            LOG.trace("Creating and running instances with ami [{}] and instance type {}", ami, instanceType);
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void startInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StartInstancesRequest) {
                StartInstancesResponse result;
                try {
                    result = ec2Client.startInstances((StartInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Start Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Starting instances with Ids [{}] ", ((StartInstancesRequest) payload).instanceIds());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StartInstancesRequest.Builder builder = StartInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            StartInstancesResponse result;
            try {
                result = ec2Client.startInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Start Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Starting instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void stopInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof StopInstancesRequest) {
                StopInstancesResponse result;
                try {
                    result = ec2Client.stopInstances((StopInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Stop Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Stopping instances with Ids [{}] ", ((StopInstancesRequest) payload).instanceIds());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            StopInstancesRequest.Builder builder = StopInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            StopInstancesResponse result;
            try {
                result = ec2Client.stopInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Stop Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Stopping instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void terminateInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof TerminateInstancesRequest) {
                TerminateInstancesResponse result;
                try {
                    result = ec2Client.terminateInstances((TerminateInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Terminate Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Terminating instances with Ids [{}] ", ((TerminateInstancesRequest) payload).instanceIds());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            TerminateInstancesRequest.Builder builder = TerminateInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            TerminateInstancesResponse result;
            try {
                result = ec2Client.terminateInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Terminate Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Terminating instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void describeInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeInstancesRequest) {
                DescribeInstancesResponse result;
                try {
                    result = ec2Client.describeInstances((DescribeInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeInstancesRequest.Builder builder = DescribeInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            }
            DescribeInstancesResponse result;
            try {
                result = ec2Client.describeInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void describeInstancesStatus(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeInstanceStatusRequest) {
                DescribeInstanceStatusResponse result;
                try {
                    result = ec2Client.describeInstanceStatus((DescribeInstanceStatusRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Describe Instances Status command returned the error code {}",
                            ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeInstanceStatusRequest.Builder builder = DescribeInstanceStatusRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            }
            DescribeInstanceStatusResponse result;
            try {
                result = ec2Client.describeInstanceStatus(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Describe Instances Status command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void rebootInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RebootInstancesRequest) {
                try {
                    LOG.trace("Rebooting instances with Ids [{}] ", ((RebootInstancesRequest) payload).instanceIds());
                    ec2Client.rebootInstances((RebootInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Reboot Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            }
        } else {
            RebootInstancesRequest.Builder builder = RebootInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            try {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Rebooting instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
                }

                ec2Client.rebootInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Reboot Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void monitorInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof MonitorInstancesRequest) {
                MonitorInstancesResponse result;
                try {
                    result = ec2Client.monitorInstances((MonitorInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Monitor Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Start Monitoring instances with Ids [{}] ", ((MonitorInstancesRequest) payload).instanceIds());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            MonitorInstancesRequest.Builder builder = MonitorInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            MonitorInstancesResponse result;
            try {
                result = ec2Client.monitorInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Monitor Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Start Monitoring instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void unmonitorInstances(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UnmonitorInstancesRequest) {
                UnmonitorInstancesResponse result;
                try {
                    result = ec2Client.unmonitorInstances((UnmonitorInstancesRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Unmonitor Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Stop Monitoring instances with Ids [{}] ", ((UnmonitorInstancesRequest) payload).instanceIds());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UnmonitorInstancesRequest.Builder builder = UnmonitorInstancesRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.instanceIds(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            UnmonitorInstancesResponse result;
            try {
                result = ec2Client.unmonitorInstances(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Unmonitor Instances command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Stop Monitoring instances with Ids [{}] ", Arrays.toString(instanceIds.toArray()));
            }

            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void createTags(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateTagsRequest) {
                CreateTagsResponse result;
                try {
                    result = ec2Client.createTags((CreateTagsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create tags command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Created tags [{}] ", ((CreateTagsRequest) payload).tags());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Collection<Tag> tags;
            CreateTagsRequest.Builder builder = CreateTagsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.resources(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_TAGS))) {
                tags = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_TAGS, Collection.class);
                builder.tags(tags);
            } else {
                throw new IllegalArgumentException("Tags must be specified");
            }
            CreateTagsResponse result;
            try {
                result = ec2Client.createTags(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create tags command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Created tags [{}] on resources with Ids [{}] ", Arrays.toString(tags.toArray()),
                        Arrays.toString(instanceIds.toArray()));
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void deleteTags(Ec2Client ec2Client, Exchange exchange) throws InvalidPayloadException {
        Collection<String> instanceIds;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteTagsRequest) {
                DeleteTagsResponse result;
                try {
                    result = ec2Client.deleteTags((DeleteTagsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete tags command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                LOG.trace("Delete tags [{}]  ", ((DeleteTagsRequest) payload).tags());
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            Collection<Tag> tags;
            DeleteTagsRequest.Builder builder = DeleteTagsRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS))) {
                instanceIds = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_IDS, Collection.class);
                builder.resources(instanceIds);
            } else {
                throw new IllegalArgumentException(MISSING_INSTANCES_MESSAGE);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_TAGS))) {
                tags = exchange.getIn().getHeader(AWS2EC2Constants.INSTANCES_TAGS, Collection.class);
                builder.tags(tags);
            } else {
                throw new IllegalArgumentException("Tags must be specified");
            }
            DeleteTagsResponse result;
            try {
                result = ec2Client.deleteTags(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete tags command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Delete tags [{}] on resources with Ids [{}] ", Arrays.toString(tags.toArray()),
                        Arrays.toString(instanceIds.toArray()));
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
            producerHealthCheck = new AWS2EC2ProducerHealthCheck(getEndpoint(), id);
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
