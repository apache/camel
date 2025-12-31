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

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateGroupRequest;
import software.amazon.awssdk.services.iam.model.CreateGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.DeleteGroupRequest;
import software.amazon.awssdk.services.iam.model.DeleteUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsRequest;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupRequest;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;

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
        executeOperation(
                exchange,
                ListAccessKeysRequest.class,
                iamClient::listAccessKeys,
                () -> {
                    ListAccessKeysRequest.Builder builder = ListAccessKeysRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (marker != null) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (maxItems != null) {
                        builder.maxItems(maxItems);
                    }
                    String userName = getOptionalHeader(exchange, IAM2Constants.USERNAME, String.class);
                    if (userName != null) {
                        builder.userName(userName);
                    }
                    return iamClient.listAccessKeys(builder.build());
                },
                "List Access Keys",
                (ListAccessKeysResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (response.marker() != null) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    private void createUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateUserRequest.class,
                iamClient::createUser,
                () -> {
                    String userName = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    return iamClient.createUser(CreateUserRequest.builder().userName(userName).build());
                },
                "Create user",
                (CreateUserResponse response, Message message) -> {
                    if (response.user() != null) {
                        message.setHeader(IAM2Constants.USER_ARN, response.user().arn());
                        message.setHeader(IAM2Constants.USER_ID, response.user().userId());
                    }
                });
    }

    private void deleteUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteUserRequest.class,
                iamClient::deleteUser,
                () -> {
                    String userName = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    return iamClient.deleteUser(DeleteUserRequest.builder().userName(userName).build());
                },
                "Delete user");
    }

    private void getUser(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetUserRequest.class,
                iamClient::getUser,
                () -> {
                    String userName = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    return iamClient.getUser(GetUserRequest.builder().userName(userName).build());
                },
                "Get user",
                (GetUserResponse response, Message message) -> {
                    if (response.user() != null) {
                        message.setHeader(IAM2Constants.USER_ARN, response.user().arn());
                        message.setHeader(IAM2Constants.USER_ID, response.user().userId());
                    }
                });
    }

    private void listUsers(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListUsersRequest.class,
                iamClient::listUsers,
                () -> {
                    ListUsersRequest.Builder builder = ListUsersRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (marker != null) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (maxItems != null) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listUsers(builder.build());
                },
                "List users",
                (ListUsersResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (response.marker() != null) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    private void createAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateAccessKeyRequest.class,
                iamClient::createAccessKey,
                () -> {
                    CreateAccessKeyRequest.Builder builder = CreateAccessKeyRequest.builder();
                    String userName = getOptionalHeader(exchange, IAM2Constants.USERNAME, String.class);
                    if (userName != null) {
                        builder.userName(userName);
                    }
                    return iamClient.createAccessKey(builder.build());
                },
                "Create Access Key");
    }

    private void deleteAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteAccessKeyRequest.class,
                iamClient::deleteAccessKey,
                () -> {
                    String accessKeyId = getRequiredHeader(exchange, IAM2Constants.ACCESS_KEY_ID, String.class,
                            "Key Id must be specified");
                    DeleteAccessKeyRequest.Builder builder = DeleteAccessKeyRequest.builder().accessKeyId(accessKeyId);
                    String userName = getOptionalHeader(exchange, IAM2Constants.USERNAME, String.class);
                    if (userName != null) {
                        builder.userName(userName);
                    }
                    return iamClient.deleteAccessKey(builder.build());
                },
                "Delete Access Key");
    }

    private void updateAccessKey(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                UpdateAccessKeyRequest.class,
                iamClient::updateAccessKey,
                () -> {
                    String accessKeyId = getRequiredHeader(exchange, IAM2Constants.ACCESS_KEY_ID, String.class,
                            "Key Id must be specified");
                    String status = getRequiredHeader(exchange, IAM2Constants.ACCESS_KEY_STATUS, String.class,
                            "Access Key status must be specified");
                    UpdateAccessKeyRequest.Builder builder = UpdateAccessKeyRequest.builder()
                            .accessKeyId(accessKeyId)
                            .status(StatusType.fromValue(status));
                    String userName = getOptionalHeader(exchange, IAM2Constants.USERNAME, String.class);
                    if (userName != null) {
                        builder.userName(userName);
                    }
                    return iamClient.updateAccessKey(builder.build());
                },
                "Update Access Key");
    }

    private void createGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateGroupRequest.class,
                iamClient::createGroup,
                () -> {
                    String groupName = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    CreateGroupRequest.Builder builder = CreateGroupRequest.builder().groupName(groupName);
                    String groupPath = getOptionalHeader(exchange, IAM2Constants.GROUP_PATH, String.class);
                    if (groupPath != null) {
                        builder.path(groupPath);
                    }
                    return iamClient.createGroup(builder.build());
                },
                "Create Group",
                (CreateGroupResponse response, Message message) -> {
                    if (response.group() != null) {
                        message.setHeader(IAM2Constants.GROUP_ARN, response.group().arn());
                        message.setHeader(IAM2Constants.GROUP_ID, response.group().groupId());
                    }
                });
    }

    private void deleteGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteGroupRequest.class,
                iamClient::deleteGroup,
                () -> {
                    String groupName = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    return iamClient.deleteGroup(DeleteGroupRequest.builder().groupName(groupName).build());
                },
                "Delete Group");
    }

    private void listGroups(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListGroupsRequest.class,
                iamClient::listGroups,
                () -> {
                    ListGroupsRequest.Builder builder = ListGroupsRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (marker != null) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (maxItems != null) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listGroups(builder.build());
                },
                "List Groups",
                (ListGroupsResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (response.marker() != null) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    private void addUserToGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                AddUserToGroupRequest.class,
                iamClient::addUserToGroup,
                () -> {
                    String groupName = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    String userName = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    return iamClient.addUserToGroup(AddUserToGroupRequest.builder()
                            .groupName(groupName)
                            .userName(userName)
                            .build());
                },
                "Add User To Group");
    }

    private void removeUserFromGroup(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                RemoveUserFromGroupRequest.class,
                iamClient::removeUserFromGroup,
                () -> {
                    String groupName = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    String userName = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    return iamClient.removeUserFromGroup(RemoveUserFromGroupRequest.builder()
                            .groupName(groupName)
                            .userName(userName)
                            .build());
                },
                "Remove User From Group");
    }

    public static Message getMessageForResponse(final Exchange exchange) {
        return exchange.getMessage();
    }

    /**
     * Executes an IAM operation with POJO request support.
     *
     * @param exchange       the Camel exchange
     * @param requestClass   the expected request class type
     * @param pojoExecutor   function to execute when using POJO request
     * @param headerExecutor supplier to execute when using header-based request
     * @param operationName  name of the operation for logging
     * @param <REQ>          the request type
     * @param <RES>          the response type
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName)
            throws InvalidPayloadException {
        executeOperation(exchange, requestClass, pojoExecutor, headerExecutor, operationName, null);
    }

    /**
     * Executes an IAM operation with POJO request support and optional response post-processing.
     *
     * @param exchange          the Camel exchange
     * @param requestClass      the expected request class type
     * @param pojoExecutor      function to execute when using POJO request
     * @param headerExecutor    supplier to execute when using header-based request
     * @param operationName     name of the operation for logging
     * @param responseProcessor optional consumer to process the response and set headers
     * @param <REQ>             the request type
     * @param <RES>             the response type
     */
    private <REQ, RES> void executeOperation(
            Exchange exchange,
            Class<REQ> requestClass,
            Function<REQ, RES> pojoExecutor,
            Supplier<RES> headerExecutor,
            String operationName,
            BiConsumer<RES, Message> responseProcessor)
            throws InvalidPayloadException {

        RES result;
        if (getConfiguration().isPojoRequest()) {
            Object payload = exchange.getIn().getMandatoryBody();
            if (requestClass.isInstance(payload)) {
                try {
                    result = pojoExecutor.apply(requestClass.cast(payload));
                } catch (AwsServiceException ase) {
                    LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                    throw ase;
                }
            } else {
                throw new IllegalArgumentException(
                        String.format("Expected body of type %s but was %s",
                                requestClass.getName(),
                                payload != null ? payload.getClass().getName() : "null"));
            }
        } else {
            try {
                result = headerExecutor.get();
            } catch (AwsServiceException ase) {
                LOG.trace("{} command returned the error code {}", operationName, ase.awsErrorDetails().errorCode());
                throw ase;
            }
        }
        Message message = getMessageForResponse(exchange);
        message.setBody(result);
        if (responseProcessor != null) {
            responseProcessor.accept(result, message);
        }
    }

    /**
     * Retrieves a required header value or throws an IllegalArgumentException.
     *
     * @param  exchange     the Camel exchange
     * @param  headerName   the header name constant
     * @param  headerType   the expected type
     * @param  errorMessage the error message if header is missing
     * @param  <T>          the header value type
     * @return              the header value
     */
    private <T> T getRequiredHeader(Exchange exchange, String headerName, Class<T> headerType, String errorMessage) {
        T value = exchange.getIn().getHeader(headerName, headerType);
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value;
    }

    /**
     * Retrieves an optional header value.
     *
     * @param  exchange   the Camel exchange
     * @param  headerName the header name constant
     * @param  headerType the expected type
     * @param  <T>        the header value type
     * @return            the header value or null if not present
     */
    private <T> T getOptionalHeader(Exchange exchange, String headerName, Class<T> headerType) {
        return exchange.getIn().getHeader(headerName, headerType);
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
