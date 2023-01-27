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
package org.apache.camel.component.huaweicloud.iam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupOption;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupRequestBody;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupResponse;
import com.huaweicloud.sdk.iam.v3.model.ShowUserRequest;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResponse;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserOption;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserRequest;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserRequestBody;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserResponse;
import org.apache.camel.Exchange;
import org.apache.camel.component.huaweicloud.iam.constants.IAMOperations;
import org.apache.camel.component.huaweicloud.iam.constants.IAMProperties;
import org.apache.camel.component.huaweicloud.iam.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IAMProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(IAMProducer.class);
    private IAMEndpoint endpoint;
    private IamClient iamClient;
    private Gson gson;

    public IAMProducer(IAMEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.gson = new Gson();
    }

    public void process(Exchange exchange) throws Exception {

        ClientConfigurations clientConfigurations = new ClientConfigurations();

        if (this.iamClient == null) {
            LOG.info("Initializing SDK client");
            this.iamClient = endpoint.initClient();
            LOG.info("IAM client initialized");
        }

        updateClientConfigs(exchange, clientConfigurations);

        switch (clientConfigurations.getOperation()) {
            case IAMOperations.LIST_USERS:
                listUsers(exchange);
                break;
            case IAMOperations.GET_USER:
                getUser(exchange, clientConfigurations);
                break;
            case IAMOperations.UPDATE_USER:
                updateUser(exchange, clientConfigurations);
                break;
            case IAMOperations.LIST_GROUPS:
                listGroups(exchange);
                break;
            case IAMOperations.GET_GROUP_USERS:
                getGroupUsers(exchange, clientConfigurations);
                break;
            case IAMOperations.UPDATE_GROUP:
                updateGroup(exchange, clientConfigurations);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    /**
     * Perform list users operation
     *
     * @param exchange
     */
    private void listUsers(Exchange exchange) {
        // invoke list users method and map return object to exchange body
        KeystoneListUsersRequest request = new KeystoneListUsersRequest();
        KeystoneListUsersResponse response = iamClient.keystoneListUsers(request);
        exchange.getMessage().setBody(gson.toJson(response.getUsers()));
    }

    /**
     * Perform get user operation
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void getUser(Exchange exchange, ClientConfigurations clientConfigurations) {
        // check for user id, which is mandatory to get user
        if (ObjectHelper.isEmpty(clientConfigurations.getUserId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No user id given");
            }
            throw new IllegalArgumentException("User id is mandatory to get user");
        }

        // invoke get user method and map return object to exchange body
        ShowUserRequest request = new ShowUserRequest()
                .withUserId(clientConfigurations.getUserId());
        ShowUserResponse response = iamClient.showUser(request);
        exchange.getMessage().setBody(gson.toJson(response.getUser()));
    }

    /**
     * Perform update user operation
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateUser(Exchange exchange, ClientConfigurations clientConfigurations) {
        // checking for valid exchange body containing user information. Body must be an UpdateUserOption object or a JSON string
        Object body = exchange.getMessage().getBody();
        UpdateUserOption userOption;
        if (body instanceof UpdateUserOption) {
            userOption = (UpdateUserOption) body;
        } else if (body instanceof String) {
            String strBody = (String) body;
            try {
                userOption = new ObjectMapper().readValue(strBody, UpdateUserOption.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("String request body must be a valid JSON");
            }
        } else {
            throw new IllegalArgumentException(
                    "Exchange body is mandatory and should be a valid JSON string or UpdateUserOption object");
        }

        // check for user id, which is mandatory to update user
        if (ObjectHelper.isEmpty(clientConfigurations.getUserId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No user id given");
            }
            throw new IllegalArgumentException("User id is mandatory to update user");
        }

        // invoke update user method and map return object to exchange body
        UpdateUserRequestBody requestBody = new UpdateUserRequestBody()
                .withUser(userOption);
        UpdateUserRequest request = new UpdateUserRequest()
                .withBody(requestBody)
                .withUserId(clientConfigurations.getUserId());
        UpdateUserResponse response = iamClient.updateUser(request);
        exchange.getMessage().setBody(gson.toJson(response.getUser()));
    }

    /**
     * Perform list groups operation
     *
     * @param exchange
     */
    private void listGroups(Exchange exchange) {
        // invoke list groups method and map return object to exchange body
        KeystoneListGroupsRequest request = new KeystoneListGroupsRequest();
        KeystoneListGroupsResponse response = iamClient.keystoneListGroups(request);
        exchange.getMessage().setBody(gson.toJson(response.getGroups()));
    }

    /**
     * Perform get group users operation
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void getGroupUsers(Exchange exchange, ClientConfigurations clientConfigurations) {
        // check for group id, which is mandatory for getting group users
        if (ObjectHelper.isEmpty(clientConfigurations.getGroupId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No group id given");
            }
            throw new IllegalArgumentException("Group id is mandatory to get group users");
        }

        // invoke list group users method and map return object to exchange body
        KeystoneListUsersForGroupByAdminRequest request = new KeystoneListUsersForGroupByAdminRequest()
                .withGroupId(clientConfigurations.getGroupId());
        KeystoneListUsersForGroupByAdminResponse response = iamClient.keystoneListUsersForGroupByAdmin(request);
        exchange.getMessage().setBody(gson.toJson(response.getUsers()));
    }

    /**
     * Perform update group operation
     *
     * @param exchange
     * @param clientConfigurations
     */
    private void updateGroup(Exchange exchange, ClientConfigurations clientConfigurations) {
        // checking for valid exchange body containing group information. Body must be an KeystoneUpdateGroupOption object or a JSON string
        Object body = exchange.getMessage().getBody();
        KeystoneUpdateGroupOption groupOption;
        if (body instanceof KeystoneUpdateGroupOption) {
            groupOption = (KeystoneUpdateGroupOption) body;
        } else if (body instanceof String) {
            String strBody = (String) body;
            try {
                groupOption = new ObjectMapper().readValue(strBody, KeystoneUpdateGroupOption.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("String request body must be a valid JSON with the proper keys");
            }
        } else {
            throw new IllegalArgumentException(
                    "Exchange body is mandatory and should be a valid JSON string or KeystoneUpdateGroupOption object");
        }

        // check for group id, which is mandatory to update a group
        if (ObjectHelper.isEmpty(clientConfigurations.getGroupId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No group id given");
            }
            throw new IllegalArgumentException("Group id is mandatory to update group");
        }

        // invoke update group method and map return object to exchange body
        KeystoneUpdateGroupRequestBody requestBody = new KeystoneUpdateGroupRequestBody()
                .withGroup(groupOption);
        KeystoneUpdateGroupRequest request = new KeystoneUpdateGroupRequest()
                .withBody(requestBody)
                .withGroupId(clientConfigurations.getGroupId());
        KeystoneUpdateGroupResponse response = iamClient.keystoneUpdateGroup(request);
        exchange.getMessage().setBody(gson.toJson(response.getGroup()));
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
        if (ObjectHelper.isEmpty(exchange.getProperty(IAMProperties.OPERATION))
                && ObjectHelper.isEmpty(endpoint.getOperation())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No operation name given. Cannot proceed with IAM operations.");
            }
            throw new IllegalArgumentException("Operation name not found");
        } else {
            clientConfigurations.setOperation(
                    ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.OPERATION))
                            ? (String) exchange.getProperty(IAMProperties.OPERATION)
                            : endpoint.getOperation());
        }

        // checking for optional userId (exchange overrides endpoint userId if both are provided)
        if (ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.USER_ID))
                || ObjectHelper.isNotEmpty(endpoint.getUserId())) {
            clientConfigurations.setUserId(
                    ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.USER_ID))
                            ? (String) exchange.getProperty(IAMProperties.USER_ID)
                            : endpoint.getUserId());
        }

        // checking for optional groupId (exchange overrides endpoint groupId if both are provided)
        if (ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.GROUP_ID))
                || ObjectHelper.isNotEmpty(endpoint.getGroupId())) {
            clientConfigurations.setGroupId(
                    ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.GROUP_ID))
                            ? (String) exchange.getProperty(IAMProperties.GROUP_ID)
                            : endpoint.getGroupId());
        }
    }
}
