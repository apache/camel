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
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.AddUserToGroupRequest;
import software.amazon.awssdk.services.iam.model.AttachGroupPolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateGroupRequest;
import software.amazon.awssdk.services.iam.model.CreateGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.DeleteGroupRequest;
import software.amazon.awssdk.services.iam.model.DeleteInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DeleteUserRequest;
import software.amazon.awssdk.services.iam.model.DetachGroupPolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.GetInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsRequest;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListInstanceProfilesRequest;
import software.amazon.awssdk.services.iam.model.ListInstanceProfilesResponse;
import software.amazon.awssdk.services.iam.model.ListPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListRolesRequest;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.RemoveRoleFromInstanceProfileRequest;
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
    public static final String MISSING_ROLE_NAME = "Role Name must be specified";
    public static final String MISSING_POLICY_ARN = "Policy ARN must be specified";
    public static final String MISSING_POLICY_NAME = "Policy Name must be specified";
    public static final String MISSING_POLICY_DOCUMENT = "Policy Document must be specified";
    public static final String MISSING_ASSUME_ROLE_POLICY_DOCUMENT = "Assume Role Policy Document must be specified";
    public static final String MISSING_INSTANCE_PROFILE_NAME = "Instance Profile Name must be specified";
    private transient String iamProducerToString;
    private HealthCheck producerHealthCheck;
    private WritableHealthCheckRepository healthCheckRepository;

    public IAM2Producer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        IAM2Operations operation = determineOperation(exchange);
        if (ObjectHelper.isEmpty(operation)) {
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
            // Role operations
            case createRole:
                createRole(getEndpoint().getIamClient(), exchange);
                break;
            case deleteRole:
                deleteRole(getEndpoint().getIamClient(), exchange);
                break;
            case getRole:
                getRole(getEndpoint().getIamClient(), exchange);
                break;
            case listRoles:
                listRoles(getEndpoint().getIamClient(), exchange);
                break;
            // Policy operations
            case createPolicy:
                createPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case deletePolicy:
                deletePolicy(getEndpoint().getIamClient(), exchange);
                break;
            case getPolicy:
                getPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case listPolicies:
                listPolicies(getEndpoint().getIamClient(), exchange);
                break;
            // Policy attachment operations
            case attachUserPolicy:
                attachUserPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case detachUserPolicy:
                detachUserPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case attachGroupPolicy:
                attachGroupPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case detachGroupPolicy:
                detachGroupPolicy(getEndpoint().getIamClient(), exchange);
                break;
            case attachRolePolicy:
                attachRolePolicy(getEndpoint().getIamClient(), exchange);
                break;
            case detachRolePolicy:
                detachRolePolicy(getEndpoint().getIamClient(), exchange);
                break;
            // Instance profile operations
            case createInstanceProfile:
                createInstanceProfile(getEndpoint().getIamClient(), exchange);
                break;
            case deleteInstanceProfile:
                deleteInstanceProfile(getEndpoint().getIamClient(), exchange);
                break;
            case getInstanceProfile:
                getInstanceProfile(getEndpoint().getIamClient(), exchange);
                break;
            case listInstanceProfiles:
                listInstanceProfiles(getEndpoint().getIamClient(), exchange);
                break;
            case addRoleToInstanceProfile:
                addRoleToInstanceProfile(getEndpoint().getIamClient(), exchange);
                break;
            case removeRoleFromInstanceProfile:
                removeRoleFromInstanceProfile(getEndpoint().getIamClient(), exchange);
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
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    String userName = getOptionalHeader(exchange, IAM2Constants.USERNAME, String.class);
                    if (ObjectHelper.isNotEmpty(userName)) {
                        builder.userName(userName);
                    }
                    return iamClient.listAccessKeys(builder.build());
                },
                "List Access Keys",
                (ListAccessKeysResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
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
                    if (ObjectHelper.isNotEmpty(response.user())) {
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
                    if (ObjectHelper.isNotEmpty(response.user())) {
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
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listUsers(builder.build());
                },
                "List users",
                (ListUsersResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
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
                    if (ObjectHelper.isNotEmpty(userName)) {
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
                    if (ObjectHelper.isNotEmpty(userName)) {
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
                    if (ObjectHelper.isNotEmpty(userName)) {
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
                    if (ObjectHelper.isNotEmpty(groupPath)) {
                        builder.path(groupPath);
                    }
                    return iamClient.createGroup(builder.build());
                },
                "Create Group",
                (CreateGroupResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.group())) {
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
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listGroups(builder.build());
                },
                "List Groups",
                (ListGroupsResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
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

    // Role operations

    private void createRole(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateRoleRequest.class,
                iamClient::createRole,
                () -> {
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    String assumeRolePolicyDocument = getRequiredHeader(exchange,
                            IAM2Constants.ASSUME_ROLE_POLICY_DOCUMENT, String.class, MISSING_ASSUME_ROLE_POLICY_DOCUMENT);
                    CreateRoleRequest.Builder builder = CreateRoleRequest.builder()
                            .roleName(roleName)
                            .assumeRolePolicyDocument(assumeRolePolicyDocument);
                    String rolePath = getOptionalHeader(exchange, IAM2Constants.ROLE_PATH, String.class);
                    if (ObjectHelper.isNotEmpty(rolePath)) {
                        builder.path(rolePath);
                    }
                    String description = getOptionalHeader(exchange, IAM2Constants.ROLE_DESCRIPTION, String.class);
                    if (ObjectHelper.isNotEmpty(description)) {
                        builder.description(description);
                    }
                    return iamClient.createRole(builder.build());
                },
                "Create Role",
                (CreateRoleResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.role())) {
                        message.setHeader(IAM2Constants.ROLE_ARN, response.role().arn());
                        message.setHeader(IAM2Constants.ROLE_ID, response.role().roleId());
                    }
                });
    }

    private void deleteRole(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteRoleRequest.class,
                iamClient::deleteRole,
                () -> {
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    return iamClient.deleteRole(DeleteRoleRequest.builder().roleName(roleName).build());
                },
                "Delete Role");
    }

    private void getRole(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetRoleRequest.class,
                iamClient::getRole,
                () -> {
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    return iamClient.getRole(GetRoleRequest.builder().roleName(roleName).build());
                },
                "Get Role",
                (GetRoleResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.role())) {
                        message.setHeader(IAM2Constants.ROLE_ARN, response.role().arn());
                        message.setHeader(IAM2Constants.ROLE_ID, response.role().roleId());
                    }
                });
    }

    private void listRoles(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListRolesRequest.class,
                iamClient::listRoles,
                () -> {
                    ListRolesRequest.Builder builder = ListRolesRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listRoles(builder.build());
                },
                "List Roles",
                (ListRolesResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    // Policy operations

    private void createPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreatePolicyRequest.class,
                iamClient::createPolicy,
                () -> {
                    String policyName
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_NAME, String.class, MISSING_POLICY_NAME);
                    String policyDocument
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_DOCUMENT, String.class, MISSING_POLICY_DOCUMENT);
                    CreatePolicyRequest.Builder builder = CreatePolicyRequest.builder()
                            .policyName(policyName)
                            .policyDocument(policyDocument);
                    String policyPath = getOptionalHeader(exchange, IAM2Constants.POLICY_PATH, String.class);
                    if (ObjectHelper.isNotEmpty(policyPath)) {
                        builder.path(policyPath);
                    }
                    String description = getOptionalHeader(exchange, IAM2Constants.POLICY_DESCRIPTION, String.class);
                    if (ObjectHelper.isNotEmpty(description)) {
                        builder.description(description);
                    }
                    return iamClient.createPolicy(builder.build());
                },
                "Create Policy",
                (CreatePolicyResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.policy())) {
                        message.setHeader(IAM2Constants.POLICY_ARN, response.policy().arn());
                        message.setHeader(IAM2Constants.POLICY_ID, response.policy().policyId());
                    }
                });
    }

    private void deletePolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeletePolicyRequest.class,
                iamClient::deletePolicy,
                () -> {
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.deletePolicy(DeletePolicyRequest.builder().policyArn(policyArn).build());
                },
                "Delete Policy");
    }

    private void getPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetPolicyRequest.class,
                iamClient::getPolicy,
                () -> {
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.getPolicy(GetPolicyRequest.builder().policyArn(policyArn).build());
                },
                "Get Policy",
                (GetPolicyResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.policy())) {
                        message.setHeader(IAM2Constants.POLICY_ARN, response.policy().arn());
                        message.setHeader(IAM2Constants.POLICY_ID, response.policy().policyId());
                    }
                });
    }

    private void listPolicies(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListPoliciesRequest.class,
                iamClient::listPolicies,
                () -> {
                    ListPoliciesRequest.Builder builder = ListPoliciesRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listPolicies(builder.build());
                },
                "List Policies",
                (ListPoliciesResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    // Policy attachment operations

    private void attachUserPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                AttachUserPolicyRequest.class,
                iamClient::attachUserPolicy,
                () -> {
                    String userName
                            = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.attachUserPolicy(AttachUserPolicyRequest.builder()
                            .userName(userName)
                            .policyArn(policyArn)
                            .build());
                },
                "Attach User Policy");
    }

    private void detachUserPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DetachUserPolicyRequest.class,
                iamClient::detachUserPolicy,
                () -> {
                    String userName
                            = getRequiredHeader(exchange, IAM2Constants.USERNAME, String.class, MISSING_USER_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.detachUserPolicy(DetachUserPolicyRequest.builder()
                            .userName(userName)
                            .policyArn(policyArn)
                            .build());
                },
                "Detach User Policy");
    }

    private void attachGroupPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                AttachGroupPolicyRequest.class,
                iamClient::attachGroupPolicy,
                () -> {
                    String groupName
                            = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.attachGroupPolicy(AttachGroupPolicyRequest.builder()
                            .groupName(groupName)
                            .policyArn(policyArn)
                            .build());
                },
                "Attach Group Policy");
    }

    private void detachGroupPolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DetachGroupPolicyRequest.class,
                iamClient::detachGroupPolicy,
                () -> {
                    String groupName
                            = getRequiredHeader(exchange, IAM2Constants.GROUP_NAME, String.class, MISSING_GROUP_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.detachGroupPolicy(DetachGroupPolicyRequest.builder()
                            .groupName(groupName)
                            .policyArn(policyArn)
                            .build());
                },
                "Detach Group Policy");
    }

    private void attachRolePolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                AttachRolePolicyRequest.class,
                iamClient::attachRolePolicy,
                () -> {
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.attachRolePolicy(AttachRolePolicyRequest.builder()
                            .roleName(roleName)
                            .policyArn(policyArn)
                            .build());
                },
                "Attach Role Policy");
    }

    private void detachRolePolicy(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DetachRolePolicyRequest.class,
                iamClient::detachRolePolicy,
                () -> {
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    String policyArn
                            = getRequiredHeader(exchange, IAM2Constants.POLICY_ARN, String.class, MISSING_POLICY_ARN);
                    return iamClient.detachRolePolicy(DetachRolePolicyRequest.builder()
                            .roleName(roleName)
                            .policyArn(policyArn)
                            .build());
                },
                "Detach Role Policy");
    }

    // Instance profile operations

    private void createInstanceProfile(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                CreateInstanceProfileRequest.class,
                iamClient::createInstanceProfile,
                () -> {
                    String instanceProfileName = getRequiredHeader(exchange,
                            IAM2Constants.INSTANCE_PROFILE_NAME, String.class, MISSING_INSTANCE_PROFILE_NAME);
                    CreateInstanceProfileRequest.Builder builder
                            = CreateInstanceProfileRequest.builder().instanceProfileName(instanceProfileName);
                    String path = getOptionalHeader(exchange, IAM2Constants.INSTANCE_PROFILE_PATH, String.class);
                    if (ObjectHelper.isNotEmpty(path)) {
                        builder.path(path);
                    }
                    return iamClient.createInstanceProfile(builder.build());
                },
                "Create Instance Profile",
                (CreateInstanceProfileResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.instanceProfile())) {
                        message.setHeader(IAM2Constants.INSTANCE_PROFILE_ARN, response.instanceProfile().arn());
                        message.setHeader(IAM2Constants.INSTANCE_PROFILE_ID, response.instanceProfile().instanceProfileId());
                    }
                });
    }

    private void deleteInstanceProfile(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                DeleteInstanceProfileRequest.class,
                iamClient::deleteInstanceProfile,
                () -> {
                    String instanceProfileName = getRequiredHeader(exchange,
                            IAM2Constants.INSTANCE_PROFILE_NAME, String.class, MISSING_INSTANCE_PROFILE_NAME);
                    return iamClient.deleteInstanceProfile(
                            DeleteInstanceProfileRequest.builder().instanceProfileName(instanceProfileName).build());
                },
                "Delete Instance Profile");
    }

    private void getInstanceProfile(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                GetInstanceProfileRequest.class,
                iamClient::getInstanceProfile,
                () -> {
                    String instanceProfileName = getRequiredHeader(exchange,
                            IAM2Constants.INSTANCE_PROFILE_NAME, String.class, MISSING_INSTANCE_PROFILE_NAME);
                    return iamClient.getInstanceProfile(
                            GetInstanceProfileRequest.builder().instanceProfileName(instanceProfileName).build());
                },
                "Get Instance Profile",
                (GetInstanceProfileResponse response, Message message) -> {
                    if (ObjectHelper.isNotEmpty(response.instanceProfile())) {
                        message.setHeader(IAM2Constants.INSTANCE_PROFILE_ARN, response.instanceProfile().arn());
                        message.setHeader(IAM2Constants.INSTANCE_PROFILE_ID, response.instanceProfile().instanceProfileId());
                    }
                });
    }

    private void listInstanceProfiles(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                ListInstanceProfilesRequest.class,
                iamClient::listInstanceProfiles,
                () -> {
                    ListInstanceProfilesRequest.Builder builder = ListInstanceProfilesRequest.builder();
                    String marker = getOptionalHeader(exchange, IAM2Constants.MARKER, String.class);
                    if (ObjectHelper.isNotEmpty(marker)) {
                        builder.marker(marker);
                    }
                    Integer maxItems = getOptionalHeader(exchange, IAM2Constants.MAX_ITEMS, Integer.class);
                    if (ObjectHelper.isNotEmpty(maxItems)) {
                        builder.maxItems(maxItems);
                    }
                    return iamClient.listInstanceProfiles(builder.build());
                },
                "List Instance Profiles",
                (ListInstanceProfilesResponse response, Message message) -> {
                    message.setHeader(IAM2Constants.IS_TRUNCATED, response.isTruncated());
                    if (ObjectHelper.isNotEmpty(response.marker())) {
                        message.setHeader(IAM2Constants.NEXT_MARKER, response.marker());
                    }
                });
    }

    private void addRoleToInstanceProfile(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                AddRoleToInstanceProfileRequest.class,
                iamClient::addRoleToInstanceProfile,
                () -> {
                    String instanceProfileName = getRequiredHeader(exchange,
                            IAM2Constants.INSTANCE_PROFILE_NAME, String.class, MISSING_INSTANCE_PROFILE_NAME);
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    return iamClient.addRoleToInstanceProfile(AddRoleToInstanceProfileRequest.builder()
                            .instanceProfileName(instanceProfileName)
                            .roleName(roleName)
                            .build());
                },
                "Add Role To Instance Profile");
    }

    private void removeRoleFromInstanceProfile(IamClient iamClient, Exchange exchange) throws InvalidPayloadException {
        executeOperation(
                exchange,
                RemoveRoleFromInstanceProfileRequest.class,
                iamClient::removeRoleFromInstanceProfile,
                () -> {
                    String instanceProfileName = getRequiredHeader(exchange,
                            IAM2Constants.INSTANCE_PROFILE_NAME, String.class, MISSING_INSTANCE_PROFILE_NAME);
                    String roleName
                            = getRequiredHeader(exchange, IAM2Constants.ROLE_NAME, String.class, MISSING_ROLE_NAME);
                    return iamClient.removeRoleFromInstanceProfile(RemoveRoleFromInstanceProfileRequest.builder()
                            .instanceProfileName(instanceProfileName)
                            .roleName(roleName)
                            .build());
                },
                "Remove Role From Instance Profile");
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
                                ObjectHelper.isNotEmpty(payload) ? payload.getClass().getName() : "null"));
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
        if (ObjectHelper.isNotEmpty(responseProcessor)) {
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

        if (ObjectHelper.isNotEmpty(healthCheckRepository)) {
            String id = getEndpoint().getId();
            producerHealthCheck = new IAM2ProducerHealthCheck(getEndpoint(), id);
            producerHealthCheck.setEnabled(getEndpoint().getComponent().isHealthCheckProducerEnabled());
            healthCheckRepository.addHealthCheck(producerHealthCheck);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isNotEmpty(healthCheckRepository) && ObjectHelper.isNotEmpty(producerHealthCheck)) {
            healthCheckRepository.removeHealthCheck(producerHealthCheck);
            producerHealthCheck = null;
        }
    }

}
