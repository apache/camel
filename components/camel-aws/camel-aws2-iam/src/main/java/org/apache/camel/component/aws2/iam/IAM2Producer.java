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
package org.apache.camel.component.aws2.iam;

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
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AddUserToGroupRequest;
import software.amazon.awssdk.services.iam.model.AddUserToGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.CreateGroupRequest;
import software.amazon.awssdk.services.iam.model.CreateGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.DeleteGroupRequest;
import software.amazon.awssdk.services.iam.model.DeleteGroupResponse;
import software.amazon.awssdk.services.iam.model.DeleteUserRequest;
import software.amazon.awssdk.services.iam.model.DeleteUserResponse;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsRequest;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupRequest;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupResponse;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyResponse;

/**
 * A Producer which sends messages to the Amazon IAM Service <a href="http://aws.amazon.com/iam/">AWS IAM</a>
 */
public class IAM2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(IAM2Producer.class);
    public static final String MISSING_GROUP_NAME = "Group Name must be specified";
    public static final String MISSING_USER_NAME = "User Name must be specified";
    private transient String iamProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public IAM2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        IAM2Operations operation = determineOperation(exchange);
        if (operation == null) {
            throw new IllegalArgumentException("Operation must be provided");
        }

        switch (operation) {
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
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private IAM2Operations determineOperation(Exchange exchange) {
        IAM2Operations operation = exchange.getIn().getHeader(IAM2Constants.OPERATION, IAM2Operations.class);
        if (operation == null) {
            operation = getConfiguration().getOperation();
        }
        return operation;
    }

    protected IAM2Configuration getConfiguration() {
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
    public IAM2Endpoint getEndpoint() {
        return (IAM2Endpoint) super.getEndpoint();
    }

    private void listAccessKeys(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListAccessKeysRequest) {
                ListAccessKeysResponse response;
                try {
                    response = iamClient.listAccessKeys((ListAccessKeysRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Access Keys command returned the error code {}", ase.getMessage());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(response);
            }
        } else {
            ListAccessKeysResponse response;
            try {
                response = iamClient.listAccessKeys();
            } catch (AwsServiceException ase) {
                LOG.trace("List Access Keys command returned the error code {}", ase.getMessage());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(response);
        }
    }

    private void createUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateUserRequest) {
                CreateUserResponse result;
                try {
                    result = iamClient.createUser((CreateUserRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create user command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateUserRequest.Builder builder = CreateUserRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            CreateUserResponse result;
            try {
                result = iamClient.createUser(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create user command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteUserRequest) {
                DeleteUserResponse result;
                try {
                    result = iamClient.deleteUser((DeleteUserRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete user command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteUserRequest.Builder builder = DeleteUserRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            DeleteUserResponse result;
            try {
                result = iamClient.deleteUser(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete user command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void getUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof GetUserRequest) {
                GetUserResponse result;
                try {
                    result = iamClient.getUser((GetUserRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("get user command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            GetUserRequest.Builder builder = GetUserRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            GetUserResponse result;
            try {
                result = iamClient.getUser(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("get user command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listUsers(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListUsersRequest) {
                ListUsersResponse result;
                try {
                    result = iamClient.listUsers((ListUsersRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List users command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListUsersResponse result;
            try {
                result = iamClient.listUsers();
            } catch (AwsServiceException ase) {
                LOG.trace("List users command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateAccessKeyRequest) {
                CreateAccessKeyResponse result;
                try {
                    result = iamClient.createAccessKey((CreateAccessKeyRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateAccessKeyRequest.Builder builder = CreateAccessKeyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            }
            CreateAccessKeyResponse result;
            try {
                result = iamClient.createAccessKey(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteAccessKeyRequest) {
                DeleteAccessKeyResponse result;
                try {
                    result = iamClient.deleteAccessKey((DeleteAccessKeyRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteAccessKeyRequest.Builder builder = DeleteAccessKeyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_ID))) {
                String accessKeyId = exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_ID, String.class);
                builder.accessKeyId(accessKeyId);
            } else {
                throw new IllegalArgumentException("Key Id must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            }
            DeleteAccessKeyResponse result;
            try {
                result = iamClient.deleteAccessKey(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void updateAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof UpdateAccessKeyRequest) {
                UpdateAccessKeyResponse result;
                try {
                    result = iamClient.updateAccessKey((UpdateAccessKeyRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Update Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            UpdateAccessKeyRequest.Builder builder = UpdateAccessKeyRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_ID))) {
                String accessKeyId = exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_ID, String.class);
                builder.accessKeyId(accessKeyId);
            } else {
                throw new IllegalArgumentException("Key Id must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_STATUS))) {
                String status = exchange.getIn().getHeader(IAM2Constants.ACCESS_KEY_STATUS, String.class);
                builder.status(StatusType.fromValue(status));
            } else {
                throw new IllegalArgumentException("Access Key status must be specified");
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            }
            UpdateAccessKeyResponse result;
            try {
                result = iamClient.updateAccessKey(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Update Access Key command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void createGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof CreateGroupRequest) {
                CreateGroupResponse result;
                try {
                    result = iamClient.createGroup((CreateGroupRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Create Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            CreateGroupRequest.Builder builder = CreateGroupRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.GROUP_NAME))) {
                String groupName = exchange.getIn().getHeader(IAM2Constants.GROUP_NAME, String.class);
                builder.groupName(groupName);
            } else {
                throw new IllegalArgumentException(MISSING_GROUP_NAME);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.GROUP_PATH))) {
                String groupPath = exchange.getIn().getHeader(IAM2Constants.GROUP_PATH, String.class);
                builder.path(groupPath);
            }
            CreateGroupResponse result;
            try {
                result = iamClient.createGroup(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Create Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void deleteGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof DeleteGroupResponse) {
                DeleteGroupResponse result;
                try {
                    result = iamClient.deleteGroup((DeleteGroupRequest) payload); // TODO this cast is impossible and will produce ClassCastException for non-null value
                } catch (AwsServiceException ase) {
                    LOG.trace("Delete Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            DeleteGroupRequest.Builder builder = DeleteGroupRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.GROUP_NAME))) {
                String groupName = exchange.getIn().getHeader(IAM2Constants.GROUP_NAME, String.class);
                builder.groupName(groupName);
            } else {
                throw new IllegalArgumentException(MISSING_GROUP_NAME);
            }
            DeleteGroupResponse result;
            try {
                result = iamClient.deleteGroup(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Delete Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void listGroups(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof ListGroupsRequest) {
                ListGroupsResponse result;
                try {
                    result = iamClient.listGroups((ListGroupsRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("List Groups command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            ListGroupsResponse result;
            try {
                result = iamClient.listGroups();
            } catch (AwsServiceException ase) {
                LOG.trace("List Groups command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void addUserToGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof AddUserToGroupRequest) {
                AddUserToGroupResponse result;
                try {
                    result = iamClient.addUserToGroup((AddUserToGroupRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Add User To Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            AddUserToGroupRequest.Builder builder = AddUserToGroupRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.GROUP_NAME))) {
                String groupName = exchange.getIn().getHeader(IAM2Constants.GROUP_NAME, String.class);
                builder.groupName(groupName);
            } else {
                throw new IllegalArgumentException(MISSING_GROUP_NAME);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            AddUserToGroupResponse result;
            try {
                result = iamClient.addUserToGroup(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Add User To Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                throw ase;
            }
            Message message = getMessageForResponse(exchange);
            message.setBody(result);
        }
    }

    private void removeUserFromGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (payload instanceof RemoveUserFromGroupRequest) {
                RemoveUserFromGroupResponse result;
                try {
                    result = iamClient.removeUserFromGroup((RemoveUserFromGroupRequest) payload);
                } catch (AwsServiceException ase) {
                    LOG.trace("Remove User From Group command returned the error code {}", ase.awsErrorDetails().errorCode());
                    throw ase;
                }
                Message message = getMessageForResponse(exchange);
                message.setBody(result);
            }
        } else {
            RemoveUserFromGroupRequest.Builder builder = RemoveUserFromGroupRequest.builder();
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.GROUP_NAME))) {
                String groupName = exchange.getIn().getHeader(IAM2Constants.GROUP_NAME, String.class);
                builder.groupName(groupName);
            } else {
                throw new IllegalArgumentException(MISSING_GROUP_NAME);
            }
            if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(IAM2Constants.USERNAME))) {
                String userName = exchange.getIn().getHeader(IAM2Constants.USERNAME, String.class);
                builder.userName(userName);
            } else {
                throw new IllegalArgumentException(MISSING_USER_NAME);
            }
            RemoveUserFromGroupResponse result;
            try {
                result = iamClient.removeUserFromGroup(builder.build());
            } catch (AwsServiceException ase) {
                LOG.trace("Remove User From Group command returned the error code {}", ase.awsErrorDetails().errorCode());
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
            producerHealthCheck = new IAM2ProducerHealthCheck(getEndpoint(), id);
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
