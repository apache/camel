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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.IamServiceClientConfiguration;
import software.amazon.awssdk.services.iam.model.AccessKey;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.AttachGroupPolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachGroupPolicyResponse;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.AttachUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
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
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.DeleteGroupRequest;
import software.amazon.awssdk.services.iam.model.DeleteGroupResponse;
import software.amazon.awssdk.services.iam.model.DeleteInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.DeleteInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.DeletePolicyRequest;
import software.amazon.awssdk.services.iam.model.DeletePolicyResponse;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleResponse;
import software.amazon.awssdk.services.iam.model.DeleteUserRequest;
import software.amazon.awssdk.services.iam.model.DeleteUserResponse;
import software.amazon.awssdk.services.iam.model.DetachGroupPolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachGroupPolicyResponse;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.DetachUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.DetachUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.GetInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.Group;
import software.amazon.awssdk.services.iam.model.InstanceProfile;
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
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.RemoveRoleFromInstanceProfileRequest;
import software.amazon.awssdk.services.iam.model.RemoveRoleFromInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupRequest;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupResponse;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.User;

public class AmazonIAMClientMock implements IamClient {

    @Override
    public CreateAccessKeyResponse createAccessKey(CreateAccessKeyRequest createAccessKeyRequest) {
        CreateAccessKeyResponse.Builder res = CreateAccessKeyResponse.builder();
        AccessKey.Builder key = AccessKey.builder();
        key.accessKeyId("test");
        key.secretAccessKey("testSecret");
        res.accessKey(key.build());
        return res.build();
    }

    @Override
    public CreateGroupResponse createGroup(CreateGroupRequest createGroupRequest) {
        CreateGroupResponse.Builder result = CreateGroupResponse.builder();
        Group.Builder group = Group.builder();
        group.groupName(createGroupRequest.groupName());
        if (createGroupRequest.path() != null) {
            group.path(createGroupRequest.path());
        }
        group.groupId("TestGroup");
        result.group(group.build());
        return result.build();
    }

    @Override
    public CreateUserResponse createUser(CreateUserRequest createUserRequest) {
        CreateUserResponse.Builder result = CreateUserResponse.builder();
        User.Builder user = User.builder();
        user.userName("test");
        result.user(user.build());
        return result.build();
    }

    @Override
    public DeleteAccessKeyResponse deleteAccessKey(DeleteAccessKeyRequest deleteAccessKeyRequest) {
        DeleteAccessKeyResponse res = DeleteAccessKeyResponse.builder().build();
        return res;
    }

    @Override
    public DeleteGroupResponse deleteGroup(DeleteGroupRequest deleteGroupRequest) {
        DeleteGroupResponse result = DeleteGroupResponse.builder().build();
        return result;
    }

    @Override
    public DeleteUserResponse deleteUser(DeleteUserRequest deleteUserRequest) {
        DeleteUserResponse res = DeleteUserResponse.builder().build();
        return res;
    }

    @Override
    public GetUserResponse getUser(GetUserRequest getUserRequest) {
        GetUserResponse.Builder builder = GetUserResponse.builder();
        User.Builder user = User.builder();
        user.userName("test");
        builder.user(user.build());
        return builder.build();
    }

    @Override
    public ListAccessKeysResponse listAccessKeys() {
        return listAccessKeys(ListAccessKeysRequest.builder().build());
    }

    @Override
    public ListAccessKeysResponse listAccessKeys(ListAccessKeysRequest listAccessKeysRequest) {
        ListAccessKeysResponse.Builder result = ListAccessKeysResponse.builder();
        Collection<AccessKeyMetadata> accessKeyMetadata = new ArrayList<>();
        AccessKeyMetadata.Builder meta = AccessKeyMetadata.builder();
        meta.accessKeyId("1");
        meta.createDate(Instant.now());
        meta.status(StatusType.ACTIVE.name());
        meta.userName("test");
        accessKeyMetadata.add(meta.build());
        result.accessKeyMetadata(accessKeyMetadata);
        result.isTruncated(false);
        return result.build();
    }

    @Override
    public ListGroupsResponse listGroups(ListGroupsRequest listGroupsRequest) {
        Group.Builder group = Group.builder();
        group.groupId("TestGroup");
        group.groupName("Test");
        ListGroupsResponse.Builder res = ListGroupsResponse.builder();
        res.groups(Collections.singleton(group.build()));
        res.isTruncated(false);
        return res.build();
    }

    @Override
    public ListUsersResponse listUsers() {
        return listUsers(ListUsersRequest.builder().build());
    }

    @Override
    public ListUsersResponse listUsers(ListUsersRequest listUsersRequest) {
        ListUsersResponse.Builder res = ListUsersResponse.builder();
        List<User> list = new ArrayList<>();
        User.Builder user = User.builder();
        user.userName("test");
        list.add(user.build());
        res.users(list);
        res.isTruncated(false);
        return res.build();
    }

    @Override
    public RemoveUserFromGroupResponse removeUserFromGroup(RemoveUserFromGroupRequest removeUserFromGroupRequest) {
        RemoveUserFromGroupResponse res = RemoveUserFromGroupResponse.builder().build();
        return res;
    }

    @Override
    public UpdateAccessKeyResponse updateAccessKey(UpdateAccessKeyRequest updateAccessKeyRequest) {
        UpdateAccessKeyResponse result = UpdateAccessKeyResponse.builder().build();
        return result;
    }

    // Role operations

    @Override
    public CreateRoleResponse createRole(CreateRoleRequest createRoleRequest) {
        CreateRoleResponse.Builder result = CreateRoleResponse.builder();
        Role.Builder role = Role.builder();
        role.roleName(createRoleRequest.roleName());
        role.roleId("TestRoleId");
        role.arn("arn:aws:iam::123456789012:role/" + createRoleRequest.roleName());
        if (createRoleRequest.path() != null) {
            role.path(createRoleRequest.path());
        }
        result.role(role.build());
        return result.build();
    }

    @Override
    public DeleteRoleResponse deleteRole(DeleteRoleRequest deleteRoleRequest) {
        return DeleteRoleResponse.builder().build();
    }

    @Override
    public GetRoleResponse getRole(GetRoleRequest getRoleRequest) {
        GetRoleResponse.Builder result = GetRoleResponse.builder();
        Role.Builder role = Role.builder();
        role.roleName(getRoleRequest.roleName());
        role.roleId("TestRoleId");
        role.arn("arn:aws:iam::123456789012:role/" + getRoleRequest.roleName());
        result.role(role.build());
        return result.build();
    }

    @Override
    public ListRolesResponse listRoles(ListRolesRequest listRolesRequest) {
        Role.Builder role = Role.builder();
        role.roleId("TestRoleId");
        role.roleName("TestRole");
        role.arn("arn:aws:iam::123456789012:role/TestRole");
        ListRolesResponse.Builder res = ListRolesResponse.builder();
        res.roles(Collections.singleton(role.build()));
        res.isTruncated(false);
        return res.build();
    }

    // Policy operations

    @Override
    public CreatePolicyResponse createPolicy(CreatePolicyRequest createPolicyRequest) {
        CreatePolicyResponse.Builder result = CreatePolicyResponse.builder();
        Policy.Builder policy = Policy.builder();
        policy.policyName(createPolicyRequest.policyName());
        policy.policyId("TestPolicyId");
        policy.arn("arn:aws:iam::123456789012:policy/" + createPolicyRequest.policyName());
        if (createPolicyRequest.path() != null) {
            policy.path(createPolicyRequest.path());
        }
        result.policy(policy.build());
        return result.build();
    }

    @Override
    public DeletePolicyResponse deletePolicy(DeletePolicyRequest deletePolicyRequest) {
        return DeletePolicyResponse.builder().build();
    }

    @Override
    public GetPolicyResponse getPolicy(GetPolicyRequest getPolicyRequest) {
        GetPolicyResponse.Builder result = GetPolicyResponse.builder();
        Policy.Builder policy = Policy.builder();
        policy.policyName("TestPolicy");
        policy.policyId("TestPolicyId");
        policy.arn(getPolicyRequest.policyArn());
        result.policy(policy.build());
        return result.build();
    }

    @Override
    public ListPoliciesResponse listPolicies(ListPoliciesRequest listPoliciesRequest) {
        Policy.Builder policy = Policy.builder();
        policy.policyId("TestPolicyId");
        policy.policyName("TestPolicy");
        policy.arn("arn:aws:iam::123456789012:policy/TestPolicy");
        ListPoliciesResponse.Builder res = ListPoliciesResponse.builder();
        res.policies(Collections.singleton(policy.build()));
        res.isTruncated(false);
        return res.build();
    }

    // Policy attachment operations

    @Override
    public AttachUserPolicyResponse attachUserPolicy(AttachUserPolicyRequest attachUserPolicyRequest) {
        return AttachUserPolicyResponse.builder().build();
    }

    @Override
    public DetachUserPolicyResponse detachUserPolicy(DetachUserPolicyRequest detachUserPolicyRequest) {
        return DetachUserPolicyResponse.builder().build();
    }

    @Override
    public AttachGroupPolicyResponse attachGroupPolicy(AttachGroupPolicyRequest attachGroupPolicyRequest) {
        return AttachGroupPolicyResponse.builder().build();
    }

    @Override
    public DetachGroupPolicyResponse detachGroupPolicy(DetachGroupPolicyRequest detachGroupPolicyRequest) {
        return DetachGroupPolicyResponse.builder().build();
    }

    @Override
    public AttachRolePolicyResponse attachRolePolicy(AttachRolePolicyRequest attachRolePolicyRequest) {
        return AttachRolePolicyResponse.builder().build();
    }

    @Override
    public DetachRolePolicyResponse detachRolePolicy(DetachRolePolicyRequest detachRolePolicyRequest) {
        return DetachRolePolicyResponse.builder().build();
    }

    // Instance profile operations

    @Override
    public CreateInstanceProfileResponse createInstanceProfile(CreateInstanceProfileRequest createInstanceProfileRequest) {
        CreateInstanceProfileResponse.Builder result = CreateInstanceProfileResponse.builder();
        InstanceProfile.Builder instanceProfile = InstanceProfile.builder();
        instanceProfile.instanceProfileName(createInstanceProfileRequest.instanceProfileName());
        instanceProfile.instanceProfileId("TestInstanceProfileId");
        instanceProfile.arn("arn:aws:iam::123456789012:instance-profile/" + createInstanceProfileRequest.instanceProfileName());
        if (createInstanceProfileRequest.path() != null) {
            instanceProfile.path(createInstanceProfileRequest.path());
        }
        instanceProfile.createDate(Instant.now());
        result.instanceProfile(instanceProfile.build());
        return result.build();
    }

    @Override
    public DeleteInstanceProfileResponse deleteInstanceProfile(DeleteInstanceProfileRequest deleteInstanceProfileRequest) {
        return DeleteInstanceProfileResponse.builder().build();
    }

    @Override
    public GetInstanceProfileResponse getInstanceProfile(GetInstanceProfileRequest getInstanceProfileRequest) {
        GetInstanceProfileResponse.Builder result = GetInstanceProfileResponse.builder();
        InstanceProfile.Builder instanceProfile = InstanceProfile.builder();
        instanceProfile.instanceProfileName(getInstanceProfileRequest.instanceProfileName());
        instanceProfile.instanceProfileId("TestInstanceProfileId");
        instanceProfile.arn("arn:aws:iam::123456789012:instance-profile/" + getInstanceProfileRequest.instanceProfileName());
        instanceProfile.createDate(Instant.now());
        result.instanceProfile(instanceProfile.build());
        return result.build();
    }

    @Override
    public ListInstanceProfilesResponse listInstanceProfiles(ListInstanceProfilesRequest listInstanceProfilesRequest) {
        InstanceProfile.Builder instanceProfile = InstanceProfile.builder();
        instanceProfile.instanceProfileId("TestInstanceProfileId");
        instanceProfile.instanceProfileName("TestInstanceProfile");
        instanceProfile.arn("arn:aws:iam::123456789012:instance-profile/TestInstanceProfile");
        instanceProfile.createDate(Instant.now());
        ListInstanceProfilesResponse.Builder res = ListInstanceProfilesResponse.builder();
        res.instanceProfiles(Collections.singleton(instanceProfile.build()));
        res.isTruncated(false);
        return res.build();
    }

    @Override
    public AddRoleToInstanceProfileResponse addRoleToInstanceProfile(
            AddRoleToInstanceProfileRequest addRoleToInstanceProfileRequest) {
        return AddRoleToInstanceProfileResponse.builder().build();
    }

    @Override
    public RemoveRoleFromInstanceProfileResponse removeRoleFromInstanceProfile(
            RemoveRoleFromInstanceProfileRequest removeRoleFromInstanceProfileRequest) {
        return RemoveRoleFromInstanceProfileResponse.builder().build();
    }

    @Override
    public IamServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
