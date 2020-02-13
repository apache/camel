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
package org.apache.camel.component.aws.iam;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupRequest;
import com.amazonaws.services.identitymanagement.model.AddUserToGroupResult;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateGroupRequest;
import com.amazonaws.services.identitymanagement.model.CreateGroupResult;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.DeleteGroupRequest;
import com.amazonaws.services.identitymanagement.model.DeleteGroupResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserRequest;
import com.amazonaws.services.identitymanagement.model.DeleteUserResult;
import com.amazonaws.services.identitymanagement.model.GetUserRequest;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.ListGroupsResult;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupRequest;
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupResult;
import com.amazonaws.services.identitymanagement.model.StatusType;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer which sends messages to the Amazon IAM Service
 * <a href="http://aws.amazon.com/iam/">AWS IAM</a>
 */
public class IAMProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(IAMProducer.class);
    private transient String iamProducerToString;

    public IAMProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        switch (determineOperation(exchange)) {
            case listAccessKeys:
                listAccessKeys(getEndpoint().getIamClient(), exchange);
                break;
            case createAccessKey:
                createAccessKey(getEndpoint().getIamClient(), exchange);
                break;
            case deleteAccessKey:
                deleteAccessKey(getEndpoint().getIamClient(), exchange);
                break;
            case updateAccessKey:
                updateAccessKey(getEndpoint().getIamClient(), exchange);
                break;
            case createUser:
                createUser(getEndpoint().getIamClient(), exchange);
                break;
            case deleteUser:
                deleteUser(getEndpoint().getIamClient(), exchange);
                break;
            case getUser:
                getUser(getEndpoint().getIamClient(), exchange);
                break;
            case listUsers:
                listUsers(getEndpoint().getIamClient(), exchange);
                break;
            case createGroup:
                createGroup(getEndpoint().getIamClient(), exchange);
                break;
            case deleteGroup:
                deleteGroup(getEndpoint().getIamClient(), exchange);
                break;
            case listGroups:
                listGroups(getEndpoint().getIamClient(), exchange);
                break;
            case addUserToGroup:
                addUserToGroup(getEndpoint().getIamClient(), exchange);
                break;
            case removeUserFromGroup:
                removeUserFromGroup(getEndpoint().getIamClient(), exchange);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation");
        }
    }

    private IAMOperations determineOperation(Exchange exchange) {
        IAMOperations operation = exchange.getIn().getHeader(IAMConstants.OPERATION, IAMOperations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected IAMConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public String toString() {
        if (iamProducerToString == null) {
            iamProducerToString = "IAMProducer[" + URISupport.sanitizeUri(getEndpoint().getEndpointUri()) + "]";
        }
        return iamProducerToString;
    }

    @Override
    public IAMEndpoint getEndpoint() {
        return (IAMEndpoint)super.getEndpoint();
    }

    private void listAccessKeys(AmazonIdentityManagement iamClient, Exchange exchange) {
        ListAccessKeysResult result;
        try {
            result = iamClient.listAccessKeys();
        } catch (AmazonServiceException ase) {
            LOG.trace("List Access Keys command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        CreateUserRequest request = new CreateUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        } else {
            throw new IllegalArgumentException("User Name must be specified");
        }
        CreateUserResult result;
        try {
            result = iamClient.createUser(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        DeleteUserRequest request = new DeleteUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        } else {
            throw new IllegalArgumentException("User Name must be specified");
        }
        DeleteUserResult result;
        try {
            result = iamClient.deleteUser(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void getUser(AmazonIdentityManagement iamClient, Exchange exchange) {
        GetUserRequest request = new GetUserRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        } else {
            throw new IllegalArgumentException("User Name must be specified");
        }
        GetUserResult result;
        try {
            result = iamClient.getUser(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("get user command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listUsers(AmazonIdentityManagement iamClient, Exchange exchange) {
        ListUsersResult result;
        try {
            result = iamClient.listUsers();
        } catch (AmazonServiceException ase) {
            LOG.trace("List users command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        CreateAccessKeyRequest request = new CreateAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        CreateAccessKeyResult result;
        try {
            result = iamClient.createAccessKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        DeleteAccessKeyRequest request = new DeleteAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID))) {
            String accessKeyId = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID, String.class);
            request.withAccessKeyId(accessKeyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        DeleteAccessKeyResult result;
        try {
            result = iamClient.deleteAccessKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void updateAccessKey(AmazonIdentityManagement iamClient, Exchange exchange) {
        UpdateAccessKeyRequest request = new UpdateAccessKeyRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID))) {
            String accessKeyId = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_ID, String.class);
            request.withAccessKeyId(accessKeyId);
        } else {
            throw new IllegalArgumentException("Key Id must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_STATUS))) {
            String status = exchange.getIn().getHeader(IAMConstants.ACCESS_KEY_STATUS, String.class);
            request.withStatus(StatusType.fromValue(status));
        } else {
            throw new IllegalArgumentException("Access Key status must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        }
        UpdateAccessKeyResult result;
        try {
            result = iamClient.updateAccessKey(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Update Access Key command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void createGroup(AmazonIdentityManagement iamClient, Exchange exchange) {
        CreateGroupRequest request = new CreateGroupRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.GROUP_NAME))) {
            String groupName = exchange.getIn().getHeader(IAMConstants.GROUP_NAME, String.class);
            request.withGroupName(groupName);
        } else {
            throw new IllegalArgumentException("Group Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.GROUP_PATH))) {
            String groupPath = exchange.getIn().getHeader(IAMConstants.GROUP_PATH, String.class);
            request.withPath(groupPath);
        }
        CreateGroupResult result;
        try {
            result = iamClient.createGroup(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Create Group command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void deleteGroup(AmazonIdentityManagement iamClient, Exchange exchange) {
        DeleteGroupRequest request = new DeleteGroupRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.GROUP_NAME))) {
            String groupName = exchange.getIn().getHeader(IAMConstants.GROUP_NAME, String.class);
            request.withGroupName(groupName);
        } else {
            throw new IllegalArgumentException("Group Name must be specified");
        }
        DeleteGroupResult result;
        try {
            result = iamClient.deleteGroup(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Delete Group command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void listGroups(AmazonIdentityManagement iamClient, Exchange exchange) {
        ListGroupsResult result;
        try {
            result = iamClient.listGroups();
        } catch (AmazonServiceException ase) {
            LOG.trace("List Groups command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void addUserToGroup(AmazonIdentityManagement iamClient, Exchange exchange) {
        AddUserToGroupRequest request = new AddUserToGroupRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.GROUP_NAME))) {
            String groupName = exchange.getIn().getHeader(IAMConstants.GROUP_NAME, String.class);
            request.withGroupName(groupName);
        } else {
            throw new IllegalArgumentException("Group Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        } else {
            throw new IllegalArgumentException("User Name must be specified");
        }
        AddUserToGroupResult result;
        try {
            result = iamClient.addUserToGroup(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Add User To Group command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    private void removeUserFromGroup(AmazonIdentityManagement iamClient, Exchange exchange) {
        RemoveUserFromGroupRequest request = new RemoveUserFromGroupRequest();
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.GROUP_NAME))) {
            String groupName = exchange.getIn().getHeader(IAMConstants.GROUP_NAME, String.class);
            request.withGroupName(groupName);
        } else {
            throw new IllegalArgumentException("Group Name must be specified");
        }
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAMConstants.USERNAME))) {
            String userName = exchange.getIn().getHeader(IAMConstants.USERNAME, String.class);
            request.withUserName(userName);
        } else {
            throw new IllegalArgumentException("User Name must be specified");
        }
        RemoveUserFromGroupResult result;
        try {
            result = iamClient.removeUserFromGroup(request);
        } catch (AmazonServiceException ase) {
            LOG.trace("Remove User From Group command returned the error code {}", ase.getErrorCode());
            throw ase;
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }
}
