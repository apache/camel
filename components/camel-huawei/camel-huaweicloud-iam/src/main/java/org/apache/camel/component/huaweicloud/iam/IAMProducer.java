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

import com.google.gson.Gson;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResponse;
import com.huaweicloud.sdk.iam.v3.model.ShowUserRequest;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResponse;
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
    private ClientConfigurations clientConfigurations;
    private IamClient iamClient;
    private Gson gson;

    public IAMProducer(IAMEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.clientConfigurations = new ClientConfigurations();
        this.iamClient = this.endpoint.initClient();
        this.gson = new Gson();
    }

    public void process(Exchange exchange) throws Exception {
        updateClientConfigs(exchange);

        switch (clientConfigurations.getOperation()) {
            case IAMOperations.LIST_USERS:
                listUsers(exchange);
                break;
            case IAMOperations.GET_USER:
                getUser(exchange);
                break;
            case IAMOperations.GET_GROUP_USERS:
                getGroupUsers(exchange);
                break;
            case IAMOperations.LIST_GROUPS:
                listGroups(exchange);
                break;
            default:
                throw new UnsupportedOperationException(
                        String.format("%s is not a supported operation", clientConfigurations.getOperation()));
        }
    }

    private void listUsers(Exchange exchange) {
        KeystoneListUsersRequest request = new KeystoneListUsersRequest();
        KeystoneListUsersResponse response = iamClient.keystoneListUsers(request);
        exchange.getMessage().setBody(gson.toJson(response.getUsers()));
    }

    private void getUser(Exchange exchange) {
        if (ObjectHelper.isEmpty(clientConfigurations.getUserId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No user id given");
            }
            throw new IllegalArgumentException("User id is mandatory to get user");
        }

        ShowUserRequest request = new ShowUserRequest()
                .withUserId(clientConfigurations.getUserId());
        ShowUserResponse response = iamClient.showUser(request);
        exchange.getMessage().setBody(gson.toJson(response.getUser()));
    }

    private void getGroupUsers(Exchange exchange) {
        if (ObjectHelper.isEmpty(clientConfigurations.getGroupId())) {
            if (LOG.isErrorEnabled()) {
                LOG.error("No group id given");
            }
            throw new IllegalArgumentException("Group id is mandatory to get group users");
        }

        KeystoneListUsersForGroupByAdminRequest request = new KeystoneListUsersForGroupByAdminRequest()
                .withGroupId(clientConfigurations.getGroupId());
        KeystoneListUsersForGroupByAdminResponse response = iamClient.keystoneListUsersForGroupByAdmin(request);
        exchange.getMessage().setBody(gson.toJson(response.getUsers()));
    }

    private void listGroups(Exchange exchange) {
        KeystoneListGroupsRequest request = new KeystoneListGroupsRequest();
        KeystoneListGroupsResponse response = iamClient.keystoneListGroups(request);
        exchange.getMessage().setBody(gson.toJson(response.getGroups()));
    }

    private void updateClientConfigs(Exchange exchange) {
        resetDynamicConfigs();

        // checking for required operation
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

        // checking for optional userId
        if (ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.USER_ID))
                || ObjectHelper.isNotEmpty(endpoint.getUserId())) {
            clientConfigurations.setUserId(
                    ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.USER_ID))
                            ? (String) exchange.getProperty(IAMProperties.USER_ID)
                            : endpoint.getUserId());
        }

        // checking for optional groupId
        if (ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.GROUP_ID))
                || ObjectHelper.isNotEmpty(endpoint.getGroupId())) {
            clientConfigurations.setGroupId(
                    ObjectHelper.isNotEmpty(exchange.getProperty(IAMProperties.GROUP_ID))
                            ? (String) exchange.getProperty(IAMProperties.GROUP_ID)
                            : endpoint.getGroupId());
        }
    }

    private void resetDynamicConfigs() {
        clientConfigurations.setOperation(null);
        clientConfigurations.setUserId(null);
        clientConfigurations.setGroupId(null);
    }
}
