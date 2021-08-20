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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.dms.constants.DMSOperations;
import org.apache.camel.component.huaweicloud.dms.constants.DMSProperties;
import org.apache.camel.component.huaweicloud.dms.models.ClientConfigurations;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceRequest;
import org.apache.camel.component.huaweicloud.dms.models.DeleteInstanceResponse;
import org.apache.camel.component.huaweicloud.dms.models.DmsInstance;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesRequest;
import org.apache.camel.component.huaweicloud.dms.models.ListInstancesResponse;
import org.apache.camel.component.huaweicloud.dms.models.QueryInstanceRequest;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DMSProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(DMSProducer.class);
    private DMSEndpoint endpoint;
    private ClientConfigurations clientConfigurations;
    private DmsClient dmsClient;
    private ObjectMapper mapper;

    public DMSProducer(DMSEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.clientConfigurations = new ClientConfigurations();
        this.dmsClient = this.endpoint.initClient();
        this.mapper = new ObjectMapper();
    }

    public void process(Exchange exchange) throws Exception {
        updateClientConfigs(exchange);

        switch (clientConfigurations.getOperation()) {
            case DMSOperations.DELETE_INSTANCE:
                deleteInstance(exchange);
                break;
            case DMSOperations.LIST_INSTANCES:
                listInstances(exchange);
                break;
            case DMSOperations.QUERY_INSTANCE:
                queryInstance(exchange);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    /**
     * Perform delete instance operation
     *
     * @param exchange
     */
    private void deleteInstance(Exchange exchange) {
        // check for instance id, which is mandatory to delete an instance
        if (ObjectHelper.isEmpty(clientConfigurations.getInstanceId())) {
            throw new IllegalArgumentException("Instance id is mandatory to delete an instance");
        }

        DeleteInstanceRequest request = new DeleteInstanceRequest()
                .withInstanceId(clientConfigurations.getInstanceId());
        DeleteInstanceResponse response = dmsClient.deleteInstance(request);
        exchange.setProperty(DMSProperties.INSTANCE_DELETED, true);
    }

    /**
     * Perform list instances operation
     *
     * @param exchange
     */
    private void listInstances(Exchange exchange) throws JsonProcessingException {
        ListInstancesRequest request = new ListInstancesRequest()
                .withEngine(clientConfigurations.getEngine());
        ListInstancesResponse response = dmsClient.listInstances(request);
        exchange.getMessage().setBody(mapper.writeValueAsString(response.getInstances()));
    }

    /**
     * Perform query instance operation
     *
     * @param exchange
     */
    private void queryInstance(Exchange exchange) throws JsonProcessingException {
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
     * Update dynamic client configurations. Some endpoint parameters (operation, user ID, and group ID) can also be
     * passed via exchange properties, so they can be updated between each transaction. Since they can change, we must
     * clear the previous transaction and update these parameters with their new values
     *
     * @param exchange
     */
    private void updateClientConfigs(Exchange exchange) {
        resetDynamicConfigs();

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
    }

    /**
     * Set all dynamic configurations to null
     */
    private void resetDynamicConfigs() {
        clientConfigurations.setOperation(null);
        clientConfigurations.setEngine(null);
        clientConfigurations.setInstanceId(null);
    }
}
