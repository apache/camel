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
package org.apache.camel.component.aws2.mq;

import java.util.List;

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
import software.amazon.awssdk.services.mq.MqClient;
import software.amazon.awssdk.services.mq.model.ConfigurationId;
import software.amazon.awssdk.services.mq.model.CreateBrokerRequest;
import software.amazon.awssdk.services.mq.model.CreateBrokerResponse;
import software.amazon.awssdk.services.mq.model.DeleteBrokerRequest;
import software.amazon.awssdk.services.mq.model.DeleteBrokerResponse;
import software.amazon.awssdk.services.mq.model.DeploymentMode;
import software.amazon.awssdk.services.mq.model.DescribeBrokerRequest;
import software.amazon.awssdk.services.mq.model.DescribeBrokerResponse;
import software.amazon.awssdk.services.mq.model.EngineType;
import software.amazon.awssdk.services.mq.model.ListBrokersRequest;
import software.amazon.awssdk.services.mq.model.ListBrokersResponse;
import software.amazon.awssdk.services.mq.model.RebootBrokerRequest;
import software.amazon.awssdk.services.mq.model.RebootBrokerResponse;
import software.amazon.awssdk.services.mq.model.UpdateBrokerRequest;
import software.amazon.awssdk.services.mq.model.UpdateBrokerResponse;
import software.amazon.awssdk.services.mq.model.User;

/**
 * A Producer which sends messages to the Amazon MQ Service <a href="http://aws.amazon.com/mq/">AWS MQ</a>
 */
public class MQ2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MQ2Producer.class);

    private transient String mqProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public MQ2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listBrokers:
                listBrokers(getEndpoint().getAmazonMqClient(), exchange);
                break;
            case createBroker:
                createBroker(getEndpoint().getAmazonMqClient(), exchange);
                break;
            case deleteBroker:
                deleteBroker(getEndpoint().getAmazonMqClient(), exchange);
                break;
            case rebootBroker:
                rebootBroker(getEndpoint().getAmazonMqClient(), exchange);
                break;
            case updateBroker:
                updateBroker(getEndpoint().getAmazonMqClient(), exchange);
                break;
            case describeBroker:
                describeBroker(getEndpoint().getAmazonMqClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private MQ2Operations determineOperation(Exchange exchange) {
        MQ2Operations operation = exchange.getIn().getHeader(MQ2Constants.OPERATION, MQ2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected MQ2Configuration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (mqProducerToString == null) {
            mqProducerToString = "MQProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return mqProducerToString;
    }

    @Override
    public MQ2Endpoint getEndpoint() {
        return (MQ2Endpoint) super.getEndpoint();
    }

    private void listBrokers(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListBrokersRequest) {
                ListBrokersResponse result;
                try {
                    result = mqClient.listBrokers((ListBrokersRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Brokers command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListBrokersRequest.Builder builder = ListBrokersRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.MAX_RESULTS))) {
                int maxResults = exchange.getIn().getHeader(MQ2Constants.MAX_RESULTS, Integer.class);
                builder.maxResults(maxResults);
            }
            ListBrokersResponse result;
            try {
                result = mqClient.listBrokers(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("List Brokers command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void createBroker(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        String brokerName;
        String brokerEngine;
        String brokerEngineVersion;
        String deploymentMode;
        String instanceType;
        Boolean publiclyAccessible;
        List<User> users;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateBrokerRequest) {
                CreateBrokerResponse result;
                try {
                    result = mqClient.createBroker((CreateBrokerRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateBrokerRequest.Builder builder = CreateBrokerRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_NAME))) {
                brokerName = exchange.getIn().getHeader(MQ2Constants.BROKER_NAME, String.class);
                builder.brokerName(brokerName);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ENGINE))) {
                brokerEngine = exchange.getIn().getHeader(MQ2Constants.BROKER_ENGINE, String.class);
                builder.engineType(EngineType.fromValue(brokerEngine));
            } else {
                throw new IllegalArgumentException("A broker engine must be specified, it can be ACTIVEMQ or RABBITMQ");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ENGINE_VERSION))) {
                brokerEngineVersion = exchange.getIn().getHeader(MQ2Constants.BROKER_ENGINE_VERSION, String.class);
                builder.engineVersion(brokerEngineVersion);
            } else {
                throw new IllegalArgumentException("Broker Engine Version must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_DEPLOYMENT_MODE))) {
                deploymentMode = exchange.getIn().getHeader(MQ2Constants.BROKER_DEPLOYMENT_MODE, String.class);
                builder.deploymentMode(DeploymentMode.fromValue(deploymentMode));
            } else {
                throw new IllegalArgumentException("Deployment Mode must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_INSTANCE_TYPE))) {
                instanceType = exchange.getIn().getHeader(MQ2Constants.BROKER_INSTANCE_TYPE, String.class);
                builder.hostInstanceType(instanceType);
            } else {
                throw new IllegalArgumentException("Instance Type must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_USERS))) {
                users = exchange.getIn().getHeader(MQ2Constants.BROKER_USERS, List.class);
                builder.users(users);
            } else {
                throw new IllegalArgumentException("A Users list must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_PUBLICLY_ACCESSIBLE))) {
                publiclyAccessible = exchange.getIn().getHeader(MQ2Constants.BROKER_PUBLICLY_ACCESSIBLE, Boolean.class);
                builder.publiclyAccessible(publiclyAccessible);
            } else {
                builder.publiclyAccessible(false);
            }
            CreateBrokerResponse result;
            try {
                result = mqClient.createBroker(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteBroker(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        String brokerId;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteBrokerRequest) {
                DeleteBrokerResponse result;
                try {
                    result = mqClient.deleteBroker((DeleteBrokerRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteBrokerRequest.Builder builder = DeleteBrokerRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ID))) {
                brokerId = exchange.getIn().getHeader(MQ2Constants.BROKER_ID, String.class);
                builder.brokerId(brokerId);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            DeleteBrokerResponse result;
            try {
                result = mqClient.deleteBroker(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void rebootBroker(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        String brokerId;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RebootBrokerRequest) {
                RebootBrokerResponse result;
                try {
                    result = mqClient.rebootBroker((RebootBrokerRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Reboot Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            RebootBrokerRequest.Builder builder = RebootBrokerRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ID))) {
                brokerId = exchange.getIn().getHeader(MQ2Constants.BROKER_ID, String.class);
                builder.brokerId(brokerId);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            RebootBrokerResponse result;
            try {
                result = mqClient.rebootBroker(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Reboot Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateBroker(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        String brokerId;
        ConfigurationId configurationId;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateBrokerRequest) {
                UpdateBrokerResponse result;
                try {
                    result = mqClient.updateBroker((UpdateBrokerRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateBrokerRequest.Builder builder = UpdateBrokerRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ID))) {
                brokerId = exchange.getIn().getHeader(MQ2Constants.BROKER_ID, String.class);
                builder.brokerId(brokerId);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.CONFIGURATION_ID))) {
                configurationId = exchange.getIn().getHeader(MQ2Constants.CONFIGURATION_ID, ConfigurationId.class);
                builder.configuration(configurationId);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            UpdateBrokerResponse result;
            try {
                result = mqClient.updateBroker(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void describeBroker(MqClient mqClient, Exchange exchange) throws InvalidPayloadException {
        String brokerId;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DescribeBrokerRequest) {
                DescribeBrokerResponse result;
                try {
                    result = mqClient.describeBroker((DescribeBrokerRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Reboot Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DescribeBrokerRequest.Builder builder = DescribeBrokerRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQ2Constants.BROKER_ID))) {
                brokerId = exchange.getIn().getHeader(MQ2Constants.BROKER_ID, String.class);
                builder.brokerId(brokerId);
            } else {
                throw new IllegalArgumentException("Broker Name must be specified");
            }
            DescribeBrokerResponse result;
            try {
                result = mqClient.describeBroker(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Reboot Broker command returned the error code {}", ase.awsErrorDetails().errorCode());
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
            producerHealthCheck = new MQ2ProducerHealthCheck(getEndpoint(), id);
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
