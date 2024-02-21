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
import software.amazon.awssdk.services.iam.model.Group;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsRequest;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupRequest;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupResponse;
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
        ListAccessKeysResponse.Builder result = ListAccessKeysResponse.builder();
        Collection<AccessKeyMetadata> accessKeyMetadata = new ArrayList<>();
        AccessKeyMetadata.Builder meta = AccessKeyMetadata.builder();
        meta.accessKeyId("1");
        meta.createDate(Instant.now());
        meta.status(StatusType.ACTIVE.name());
        meta.userName("test");
        accessKeyMetadata.add(meta.build());
        result.accessKeyMetadata(accessKeyMetadata);
        return result.build();
    }

    @Override
    public ListGroupsResponse listGroups(ListGroupsRequest listGroupsRequest) {
        Group.Builder group = Group.builder();
        group.groupId("TestGroup");
        group.groupName("Test");
        ListGroupsResponse.Builder res = ListGroupsResponse.builder();
        res.groups(Collections.singleton(group.build()));
        return res.build();
    }

    @Override
    public ListUsersResponse listUsers() {
        ListUsersResponse.Builder res = ListUsersResponse.builder();
        List<User> list = new ArrayList<>();
        User.Builder user = User.builder();
        user.userName("test");
        list.add(user.build());
        res.users(list);
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
