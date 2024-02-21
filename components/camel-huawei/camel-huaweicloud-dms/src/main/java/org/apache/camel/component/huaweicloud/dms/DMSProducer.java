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
package org.apache.camel.component.huaweicloud.dms;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.dms.constants.DMSConstants;
import org.apache.camel.component.huaweicloud.dms.constants.DMSOperations;
import org.apache.camel.component.huaweicloud.dms.constants.DMSProperties;
import org.apache.camel.component.huaweicloud.dms.models.ClientConfigurations;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceRequestBody;
import org.apache.camel.component.huaweicloud.dms.models.CreateInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.UpdateInstanceRequestBody;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMSProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(DMSProducer.class);
    private DMSEndpoint endpoint;
    private DmsClient dmsClient;
    private ObjectMapper mapper;

    public DMSProducer(DMSEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.mapper = new ObjectMapper();
    }

    public void process(Exchange exchange) throws Exception {

        ClientConfigurations clientConfigurations = new ClientConfigurations();

        if (dmsClient == null) {
            LOG.debug("Initializing SDK client");
            this.dmsClient = endpoint.initClient();
            LOG.debug("Successfully initialized SDK client");
        }

        updateClientConfigs(exchange, clientConfigurations);

        switch (clientConfigurations.getOperation()) {
            case DMSOperations.CREATE_INSTANCE:
                createInstance(exchange, clientConfigurations);
                break;
            case DMSOperations.DELETE_INSTANCE:
                deleteInstance(exchange, clientConfigurations);
                break;
            case DMSOperations.LIST_INSTANCES:
                listInstances(exchange, clientConfigurations);
                break;
            case DMSOperations.QUERY_INSTANCE:
                queryInstance(exchange, clientConfigurations);
                break;
            case DMSOperations.UPDATE_INSTANCE:
                updateInstance(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    /**
     * Perform create instance operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws JsonProcessingException
     */
    private void createInstance(Exchange exchange, ClientConfigurations clientConfigurations) throws JsonProcessingException {
        CreateInstanceRequestBody body = null;

        // checking if user inputted exchange body containing instance information. Body must be a CreateInstanceRequestBody or a valid JSON String (Advanced users)
        Object exchangeBody = exchange.getMessage().getBody();
        if (exchangeBody instanceof CreateInstanceRequestBody) {
            body = (CreateInstanceRequestBody) exchangeBody;
        } else if (exchangeBody instanceof String) {
            String strBody = (String) exchangeBody;
            try {
                body = mapper.readValue(strBody, CreateInstanceRequestBody.class);
            } catch (JsonProcessingException e) {
                LOG.warn(
                        "String request body must be a valid JSON representation of a CreateInstanceRequestBody. Attempting to create an instance from endpoint parameters");
            }
        }

        // if no CreateInstanceRequestBody was found in the exchange body, then create an instance from the endpoint parameters (basic users)
        if (body == null) {
            if (ObjectHelper.isEmpty(clientConfigurations.getName())) {
                throw new IllegalArgumentException("Name is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getEngine())) {
                throw new IllegalArgumentException("Engine is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getEngineVersion())) {
                throw new IllegalArgumentException("Engine version is mandatory to create an instance");
            }

            if (clientConfigurations.getEngine().equals(DMSConstants.KAFKA)) {
                // check for mandatory Kafka values
                if (ObjectHelper.isEmpty(clientConfigurations.getSpecification())) {
                    throw new IllegalArgumentException("Specification is mandatory to create a Kafka instance");
                }
                if (ObjectHelper.isEmpty(clientConfigurations.getPartitionNum())) {
                    throw new IllegalArgumentException("Partition number is mandatory to create a Kafka instance");
                }
                if (ObjectHelper.isEmpty(clientConfigurations.getKafkaManagerUser())) {
                    throw new IllegalArgumentException("Kafka manager user is mandatory to create a Kafka instance");
                }
                if (ObjectHelper.isEmpty(clientConfigurations.getKafkaManagerPassword())) {
                    throw new IllegalArgumentException("Kafka manager password is mandatory to create a Kafka instance");
                }
            } else if (clientConfigurations.getEngine().equals(DMSConstants.RABBITMQ)) {
                // check for mandatory RabbitMQ values
                if (ObjectHelper.isEmpty(clientConfigurations.getAccessUser())) {
                    throw new IllegalArgumentException("Access user is mandatory to create a RabbitMQ instance");
                }
                if (ObjectHelper.isEmpty(clientConfigurations.getPassword())) {
                    throw new IllegalArgumentException("Password is mandatory to create a RabbitMQ instance");
                }
            } else {
                throw new IllegalArgumentException("Engine must be 'kafka' or 'rabbitmq'");
            }

            if (ObjectHelper.isEmpty(clientConfigurations.getStorageSpace())) {
                throw new IllegalArgumentException("Storage space is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getVpcId())) {
                throw new IllegalArgumentException("VPC ID is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getSecurityGroupId())) {
                throw new IllegalArgumentException("Security group ID is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getSubnetId())) {
                throw new IllegalArgumentException("Subnet ID is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getAvailableZones())) {
                throw new IllegalArgumentException("Available zones is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getProductId())) {
                throw new IllegalArgumentException("Product ID is mandatory to create an instance");
            }
            if (ObjectHelper.isEmpty(clientConfigurations.getStorageSpecCode())) {
                throw new IllegalArgumentException("Storage spec code is mandatory to create an instance");
            }

            // create a new CreateInstanceRequestBody based on the given endpoint parameters
            body = new CreateInstanceRequestBody()
                    .withName(clientConfigurations.getName())
                    .withEngine(clientConfigurations.getEngine())
                    .withEngineVersion(clientConfigurations.getEngineVersion())
                    .withSpecification(clientConfigurations.getSpecification())
                    .withStorageSpace(clientConfigurations.getStorageSpace())
                    .withPartitionNum(clientConfigurations.getPartitionNum())
                    .withAccessUser(clientConfigurations.getAccessUser())
                    .withPassword(clientConfigurations.getPassword())
                    .withVpcId(clientConfigurations.getVpcId())
                    .withSecurityGroupId(clientConfigurations.getSecurityGroupId())
                    .withSubnetId(clientConfigurations.getSubnetId())
                    .withAvailableZones(clientConfigurations.getAvailableZones())
                    .withProductId(clientConfigurations.getProductId())
                    .withKafkaManagerUser(clientConfigurations.getKafkaManagerUser())
                    .withKafkaManagerPassword(clientConfigurations.getKafkaManagerPassword())
                    .withStorageSpecCode(clientConfigurations.getStorageSpecCode());
        }

        CreateInstanceRequest request = new CreateInstanceRequest()
                .withBody(body);
        CreateInstanceResponse response = dmsClient.createInstance(request);
        exchange.getMessage().setBody(mapper.writeValueAsString(response));
    }

    /**
     * Perform delete instance operation
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void deleteInstance(Exchange exchange, ClientConfigurations clientConfigurations) {
        // check for instance id, which is mandatory to delete an instance
        if (ObjectHelper.isEmpty(clientConfigurations.getInstanceId())) {
            throw new IllegalArgumentException("Instance id is mandatory to delete an instance");
        }

        DeleteInstanceRequest request = new DeleteInstanceRequest()
                .withInstanceId(clientConfigurations.getInstanceId());
        dmsClient.deleteInstance(request);
        exchange.setProperty(DMSProperties.INSTANCE_DELETED, true);
    }

    /**
     * Perform list instances operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws JsonProcessingException
     */
    private void listInstances(Exchange exchange, ClientConfigurations clientConfigurations) throws JsonProcessingException {
        ListInstancesRequest request = new ListInstancesRequest()
                .withEngine(clientConfigurations.getEngine());
        ListInstancesResponse response = dmsClient.listInstances(request);
        exchange.getMessage().setBody(mapper.writeValueAsString(response.getInstances()));
    }

    /**
     * Perform query instance operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws JsonProcessingException
     */
    private void queryInstance(Exchange exchange, ClientConfigurations clientConfigurations) throws JsonProcessingException {
        // check for instance id, which is mandatory to query an instance
        if (ObjectHelper.isEmpty(clientConfigurations.getInstanceId())) {
            throw new IllegalArgumentException("Instance id is mandatory to query an instance");
        }

        QueryInstanceRequest request = new QueryInstanceRequest()
                .withInstanceId(clientConfigurations.getInstanceId());
        DmsInstance response = dmsClient.queryInstance(request);
        exchange.getMessage().setBody(mapper.writeValueAsString(response));
    }

    /**
     * Perform update instance operation
     *
     * @param  exchange
     * @param  clientConfigurations
     * @throws JsonProcessingException
     */
    private void updateInstance(Exchange exchange, ClientConfigurations clientConfigurations) throws JsonProcessingException {
        // check for instance id, which is mandatory to update an instance
        if (ObjectHelper.isEmpty(clientConfigurations.getInstanceId())) {
            throw new IllegalArgumentException("Instance id is mandatory to update an instance");
        }

        UpdateInstanceRequestBody body;

        Object exchangeBody = exchange.getIn().getBody();
        if (exchangeBody instanceof UpdateInstanceRequestBody) {
            body = (UpdateInstanceRequestBody) exchangeBody;
        } else if (exchangeBody instanceof String) {
            String strBody = (String) exchangeBody;
            body = mapper.readValue(strBody, UpdateInstanceRequestBody.class);
        } else {
            throw new IllegalArgumentException(
                    "Exchange body must include an UpdateInstanceRequestBody or a valid JSON String representation of it");
        }

        UpdateInstanceRequest request = new UpdateInstanceRequest()
                .withInstanceId(clientConfigurations.getInstanceId())
                .withBody(body);
        dmsClient.updateInstance(request);
        exchange.setProperty(DMSProperties.INSTANCE_UPDATED, true);
    }

    /**
     * Update dynamic client configurations. Some endpoint parameters (operation, user ID, and group ID) can also be
     * passed via exchange properties, so they can be updated between each transaction. Since they can change, we must
     * clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateClientConfigs(Exchange exchange, ClientConfigurations clientConfigurations) {

        // checking for required operation (exchange overrides endpoint operation if both are provided)
        if (ObjectHelper.isEmpty(exchange.getProperty(DMSProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No operation name given. Cannot proceed with DMS operations.");
            }
            throw new IllegalArgumentException("Operation name not found");
        } else {
            clientConfigurations.setOperation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.OPERATION))
                            ? (String) exchange.getProperty(DMSProperties.OPERATION)
                            : endpoint.getOperation());
        }

        // checking for optional values (exchange overrides endpoint value if both are provided)

        // checking for engine
        clientConfigurations.setEngine(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.ENGINE))
                        ? (String) exchange.getProperty(DMSProperties.ENGINE)
                        : endpoint.getEngine());

        // checking for instance id
        clientConfigurations.setInstanceId(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.INSTANCE_ID))
                        ? (String) exchange.getProperty(DMSProperties.INSTANCE_ID)
                        : endpoint.getInstanceId());

        // checking for name
        clientConfigurations.setName(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.NAME))
                        ? (String) exchange.getProperty(DMSProperties.NAME)
                        : endpoint.getName());

        // checking for engine version
        clientConfigurations.setEngineVersion(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.ENGINE_VERSION))
                        ? (String) exchange.getProperty(DMSProperties.ENGINE_VERSION)
                        : endpoint.getEngineVersion());

        // checking for specification
        clientConfigurations.setSpecification(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.SPECIFICATION))
                        ? (String) exchange.getProperty(DMSProperties.SPECIFICATION)
                        : endpoint.getSpecification());

        // checking for storage space
        clientConfigurations.setStorageSpace(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.STORAGE_SPACE))
                        ? (Integer) exchange.getProperty(DMSProperties.STORAGE_SPACE)
                        : endpoint.getStorageSpace());

        // checking for partition number
        clientConfigurations.setPartitionNum(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.PARTITION_NUM))
                        ? (Integer) exchange.getProperty(DMSProperties.PARTITION_NUM)
                        : endpoint.getPartitionNum());

        // checking for access user
        clientConfigurations.setAccessUser(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.ACCESS_USER))
                        ? (String) exchange.getProperty(DMSProperties.ACCESS_USER)
                        : endpoint.getAccessUser());

        // checking for password
        clientConfigurations.setPassword(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.PASSWORD))
                        ? (String) exchange.getProperty(DMSProperties.PASSWORD)
                        : endpoint.getPassword());

        // checking for vpc id
        clientConfigurations.setVpcId(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.VPC_ID))
                        ? (String) exchange.getProperty(DMSProperties.VPC_ID)
                        : endpoint.getVpcId());

        // checking for security group id
        clientConfigurations.setSecurityGroupId(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.SECURITY_GROUP_ID))
                        ? (String) exchange.getProperty(DMSProperties.SECURITY_GROUP_ID)
                        : endpoint.getSecurityGroupId());

        // checking for subnet id
        clientConfigurations.setSubnetId(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.SUBNET_ID))
                        ? (String) exchange.getProperty(DMSProperties.SUBNET_ID)
                        : endpoint.getSubnetId());

        // checking for available zones
        clientConfigurations.setAvailableZones(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.AVAILABLE_ZONES))
                        ? (List<String>) exchange.getProperty(DMSProperties.AVAILABLE_ZONES)
                        : endpoint.getAvailableZones());

        // checking for product id
        clientConfigurations.setProductId(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.PRODUCT_ID))
                        ? (String) exchange.getProperty(DMSProperties.PRODUCT_ID)
                        : endpoint.getProductId());

        // checking for kafka manager username
        clientConfigurations.setKafkaManagerUser(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.KAFKA_MANAGER_USER))
                        ? (String) exchange.getProperty(DMSProperties.KAFKA_MANAGER_USER)
                        : endpoint.getKafkaManagerUser());

        // checking for kafka manager password
        clientConfigurations.setKafkaManagerPassword(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.KAFKA_MANAGER_PASSWORD))
                        ? (String) exchange.getProperty(DMSProperties.KAFKA_MANAGER_PASSWORD)
                        : endpoint.getKafkaManagerPassword());

        // checking for storage spec code
        clientConfigurations.setStorageSpecCode(
                ObjectHelper.isNotEmpty(exchange.getProperty(DMSProperties.STORAGE_SPEC_CODE))
                        ? (String) exchange.getProperty(DMSProperties.STORAGE_SPEC_CODE)
                        : endpoint.getStorageSpecCode());
    }
}
