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

import java.util.ArrayList;
import java.util.List;

import com.huaweicloud.sdk.core.HcClient;
import com.huaweicloud.sdk.iam.v3.IamClient;
import com.huaweicloud.sdk.iam.v3.model.KeystoneGroupResult;
import com.huaweicloud.sdk.iam.v3.model.KeystoneGroupResultWithLinksSelf;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListGroupsResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersForGroupByAdminResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneListUsersResult;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupRequest;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUpdateGroupResponse;
import com.huaweicloud.sdk.iam.v3.model.KeystoneUserResult;
import com.huaweicloud.sdk.iam.v3.model.Links;
import com.huaweicloud.sdk.iam.v3.model.ShowUserRequest;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResponse;
import com.huaweicloud.sdk.iam.v3.model.ShowUserResult;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserRequest;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserResponse;
import com.huaweicloud.sdk.iam.v3.model.UpdateUserResult;

public class IAMMockClient extends IamClient {
    public IAMMockClient(HcClient hcClient) {
        super(null);
    }

    @Override
    public KeystoneListUsersResponse keystoneListUsers(KeystoneListUsersRequest request) {
        Links links = new Links()
                .withSelf("sample_link");
        List<KeystoneListUsersResult> users = new ArrayList<>();
        KeystoneListUsersResult user1 = new KeystoneListUsersResult().withName("User 1").withDomainId("123");
        KeystoneListUsersResult user2 = new KeystoneListUsersResult().withName("User 2").withDomainId("456");
        users.add(user1);
        users.add(user2);
        KeystoneListUsersResponse response = new KeystoneListUsersResponse()
                .withLinks(links)
                .withUsers(users);
        return response;
    }

    @Override
    public ShowUserResponse showUser(ShowUserRequest request) {
        ShowUserResult user = new ShowUserResult()
                .withName("User 15")
                .withDomainId("138")
                .withEmail("user15@email.com");
        ShowUserResponse response = new ShowUserResponse()
                .withUser(user);
        return response;
    }

    @Override
    public KeystoneListUsersForGroupByAdminResponse keystoneListUsersForGroupByAdmin(
            KeystoneListUsersForGroupByAdminRequest request) {
        List<KeystoneUserResult> users = new ArrayList<>();
        KeystoneUserResult user9 = new KeystoneUserResult().withName("User 9").withId("abc");
        KeystoneUserResult user10 = new KeystoneUserResult().withName("User 10").withId("def").withDescription("Employee");
        users.add(user9);
        users.add(user10);
        KeystoneListUsersForGroupByAdminResponse response = new KeystoneListUsersForGroupByAdminResponse()
                .withUsers(users);
        return response;
    }

    @Override
    public KeystoneListGroupsResponse keystoneListGroups(KeystoneListGroupsRequest request) {
        List<KeystoneGroupResult> groups = new ArrayList<>();
        KeystoneGroupResult group1
                = new KeystoneGroupResult().withName("Group 1").withId("group1_id").withDescription("First group");
        KeystoneGroupResult group2
                = new KeystoneGroupResult().withName("Group 2").withId("group2_id").withDescription("Second group");
        groups.add(group1);
        groups.add(group2);
        KeystoneListGroupsResponse response = new KeystoneListGroupsResponse()
                .withGroups(groups);
        return response;
    }

    @Override
    public UpdateUserResponse updateUser(UpdateUserRequest request) {
        UpdateUserResult user = new UpdateUserResult().withName("User 19").withDescription("First");
        UpdateUserResponse response = new UpdateUserResponse()
                .withUser(user);
        return response;
    }

    @Override
    public KeystoneUpdateGroupResponse keystoneUpdateGroup(KeystoneUpdateGroupRequest request) {
        KeystoneGroupResultWithLinksSelf group = new KeystoneGroupResultWithLinksSelf()
                .withDescription("Group description")
                .withDomainId("123")
                .withName("Group 43");
        KeystoneUpdateGroupResponse response = new KeystoneUpdateGroupResponse()
                .withGroup(group);
        return response;
    }
}
