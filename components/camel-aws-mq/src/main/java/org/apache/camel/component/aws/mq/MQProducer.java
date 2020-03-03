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
package org.apache.camel.component.aws.mq;

import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.mq.AmazonMQ;
import com.amazonaws.services.mq.model.ConfigurationId;
import com.amazonaws.services.mq.model.CreateBrokerRequest;
import com.amazonaws.services.mq.model.CreateBrokerResult;
import com.amazonaws.services.mq.model.DeleteBrokerRequest;
import com.amazonaws.services.mq.model.DeleteBrokerResult;
import com.amazonaws.services.mq.model.DeploymentMode;
import com.amazonaws.services.mq.model.DescribeBrokerRequest;
import com.amazonaws.services.mq.model.DescribeBrokerResult;
import com.amazonaws.services.mq.model.EngineType;
import com.amazonaws.services.mq.model.ListBrokersRequest;
import com.amazonaws.services.mq.model.ListBrokersResult;
import com.amazonaws.services.mq.model.RebootBrokerRequest;
import com.amazonaws.services.mq.model.RebootBrokerResult;
import com.amazonaws.services.mq.model.UpdateBrokerRequest;
import com.amazonaws.services.mq.model.UpdateBrokerResult;
import com.amazonaws.services.mq.model.User;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon MQ Service
 * <a href="http://aws.amazon.com/mq/">AWS MQ</a>
 */
public class MQProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(MQProducer.class);

    private transient String mqProducerToString;

    public MQProducer(Endpoint endpoint) {
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

    private MQOperations determineOperation(Exchange exchange) {
        MQOperations operation = exchange.getIn().getHeader(MQConstants.OPERATION, MQOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected MQConfiguration getConfiguration() {
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
    public MQEndpoint getEndpoint() {
        return (MQEndpoint)super.getEndpoint();
    }

    private void listBrokers(AmazonMQ mqClient, Exchange exchange) {
        ListBrokersRequest request = new ListBrokersRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.MAX_RESULTS))) {
            int maxResults = exchange.getIn().getHeader(MQConstants.MAX_RESULTS, Integer.class);
            request.withMaxResults(maxResults);
        }
        ListBrokersResult result;
        try {
            result = mqClient.listBrokers(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("List Brokers command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createBroker(AmazonMQ mqClient, Exchange exchange) {
        String brokerName;
        String brokerEngine;
        String brokerEngineVersion;
        String deploymentMode;
        String instanceType;
        Boolean publiclyAccessible;
        List<User> users;
        CreateBrokerRequest request = new CreateBrokerRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_NAME))) {
            brokerName = exchange.getIn().getHeader(MQConstants.BROKER_NAME, String.class);
            request.withBrokerName(brokerName);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ENGINE))) {
            brokerEngine = exchange.getIn().getHeader(MQConstants.BROKER_ENGINE, String.class);
            request.withEngineType(EngineType.fromValue(brokerEngine));
        } else {
            request.withEngineType(EngineType.ACTIVEMQ.name());
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ENGINE_VERSION))) {
            brokerEngineVersion = exchange.getIn().getHeader(MQConstants.BROKER_ENGINE_VERSION, String.class);
            request.withEngineVersion(brokerEngineVersion);
        } else {
            throw new IllegalArgumentException("Broker Engine Version must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_DEPLOYMENT_MODE))) {
            deploymentMode = exchange.getIn().getHeader(MQConstants.BROKER_DEPLOYMENT_MODE, String.class);
            request.withDeploymentMode(DeploymentMode.fromValue(deploymentMode));
        } else {
            throw new IllegalArgumentException("Deployment Mode must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_INSTANCE_TYPE))) {
            instanceType = exchange.getIn().getHeader(MQConstants.BROKER_INSTANCE_TYPE, String.class);
            request.withHostInstanceType(instanceType);
        } else {
            throw new IllegalArgumentException("Instance Type must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_USERS))) {
            users = exchange.getIn().getHeader(MQConstants.BROKER_USERS, List.class);
            request.withUsers(users);
        } else {
            throw new IllegalArgumentException("A Users list must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_PUBLICLY_ACCESSIBLE))) {
            publiclyAccessible = exchange.getIn().getHeader(MQConstants.BROKER_PUBLICLY_ACCESSIBLE, Boolean.class);
            request.withPubliclyAccessible(publiclyAccessible);
        } else {
            request.withPubliclyAccessible(false);
        }
        CreateBrokerResult result;
        try {
            result = mqClient.createBroker(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Broker command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteBroker(AmazonMQ mqClient, Exchange exchange) {
        String brokerId;
        DeleteBrokerRequest request = new DeleteBrokerRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ID))) {
            brokerId = exchange.getIn().getHeader(MQConstants.BROKER_ID, String.class);
            request.withBrokerId(brokerId);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        DeleteBrokerResult result;
        try {
            result = mqClient.deleteBroker(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete Broker command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void rebootBroker(AmazonMQ mqClient, Exchange exchange) {
        String brokerId;
        RebootBrokerRequest request = new RebootBrokerRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ID))) {
            brokerId = exchange.getIn().getHeader(MQConstants.BROKER_ID, String.class);
            request.withBrokerId(brokerId);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        RebootBrokerResult result;
        try {
            result = mqClient.rebootBroker(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Reboot Broker command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void updateBroker(AmazonMQ mqClient, Exchange exchange) {
        String brokerId;
        ConfigurationId configurationId;
        UpdateBrokerRequest request = new UpdateBrokerRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ID))) {
            brokerId = exchange.getIn().getHeader(MQConstants.BROKER_ID, String.class);
            request.withBrokerId(brokerId);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.CONFIGURATION_ID))) {
            configurationId = exchange.getIn().getHeader(MQConstants.CONFIGURATION_ID, ConfigurationId.class);
            request.withConfiguration(configurationId);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        UpdateBrokerResult result;
        try {
            result = mqClient.updateBroker(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Update Broker command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void describeBroker(AmazonMQ mqClient, Exchange exchange) {
        String brokerId;
        DescribeBrokerRequest request = new DescribeBrokerRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(MQConstants.BROKER_ID))) {
            brokerId = exchange.getIn().getHeader(MQConstants.BROKER_ID, String.class);
            request.withBrokerId(brokerId);
        } else {
            throw new IllegalArgumentException("Broker Name must be specified");
        }
        DescribeBrokerResult result;
        try {
            result = mqClient.describeBroker(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Reboot Broker command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
