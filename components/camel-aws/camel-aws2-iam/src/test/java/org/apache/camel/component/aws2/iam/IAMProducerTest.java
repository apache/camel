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

import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.AddUserToGroupResponse;
import software.amazon.awssdk.services.iam.model.AttachGroupPolicyResponse;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.AttachUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.CreateGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.DeleteGroupResponse;
import software.amazon.awssdk.services.iam.model.DeleteInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.DeletePolicyResponse;
import software.amazon.awssdk.services.iam.model.DeleteRoleResponse;
import software.amazon.awssdk.services.iam.model.DeleteUserResponse;
import software.amazon.awssdk.services.iam.model.DetachGroupPolicyResponse;
import software.amazon.awssdk.services.iam.model.DetachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.DetachUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListInstanceProfilesResponse;
import software.amazon.awssdk.services.iam.model.ListPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListRolesResponse;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.RemoveRoleFromInstanceProfileResponse;
import software.amazon.awssdk.services.iam.model.RemoveUserFromGroupResponse;
import software.amazon.awssdk.services.iam.model.StatusType;
import software.amazon.awssdk.services.iam.model.UpdateAccessKeyResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IAMProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonIAMClient")
    AmazonIAMClientMock clientMock = new AmazonIAMClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void iamListAccessKeysTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listKeys", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listAccessKeys);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListAccessKeysResponse resultGet = (ListAccessKeysResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.accessKeyMetadata().size());
        assertEquals("1", resultGet.accessKeyMetadata().get(0).accessKeyId());
    }

    @Test
    public void iamCreateUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateUserResponse resultGet = (CreateUserResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.user().userName());
    }

    @Test
    public void iamCreateUserPojoTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUserPojo", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createUser);
                exchange.getIn().setBody(CreateUserRequest.builder().userName("test").build());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateUserResponse resultGet = (CreateUserResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.user().userName());
    }

    @Test
    public void iamDeleteUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteUserResponse resultGet = (DeleteUserResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamListUsersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listUsers", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listUsers);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListUsersResponse resultGet = (ListUsersResponse) exchange.getIn().getBody();
        assertEquals(1, resultGet.users().size());
        assertEquals("test", resultGet.users().get(0).userName());
    }

    @Test
    public void iamCreateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createAccessKey);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateAccessKeyResponse resultGet = (CreateAccessKeyResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.accessKey().accessKeyId());
        assertEquals("testSecret", resultGet.accessKey().secretAccessKey());
    }

    @Test
    public void iamDeleteAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteAccessKey);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_ID, "1");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteAccessKeyResponse resultGet = (DeleteAccessKeyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamGetUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getUser", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.getUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        GetUserResponse resultGet = (GetUserResponse) exchange.getIn().getBody();
        assertEquals("test", resultGet.user().userName());
    }

    @Test
    public void iamUpdateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.updateAccessKey);
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_ID, "1");
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_STATUS, StatusType.INACTIVE.name());
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        UpdateAccessKeyResponse resultGet = (UpdateAccessKeyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamCreateGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createGroup", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.GROUP_PATH, "/test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        CreateGroupResponse resultGet = (CreateGroupResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("Test", resultGet.group().groupName());
        assertEquals("/test", resultGet.group().path());
    }

    public void iamDeleteGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createGroup", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        DeleteGroupResponse resultGet = (DeleteGroupResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    public void iamListGroupsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listGroups", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listGroups);
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        ListGroupsResponse resultGet = (ListGroupsResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.groups().size());
        assertEquals("Test", resultGet.groups().get(0).groupName());
    }

    public void iamAddUserToGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:addUserToGroup", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.addUserToGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "Test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        AddUserToGroupResponse resultGet = (AddUserToGroupResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    public void iamRemoveUserFromGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:removeUserFromGroup", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.removeUserFromGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "Test");
            }
        });

        MockEndpoint.assertIsSatisfied(context);

        RemoveUserFromGroupResponse resultGet = (RemoveUserFromGroupResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    // Role operations tests

    @Test
    public void iamCreateRoleTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createRole", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createRole);
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
                exchange.getIn().setHeader(IAM2Constants.ASSUME_ROLE_POLICY_DOCUMENT,
                        "{\"Version\":\"2012-10-17\",\"Statement\":[]}");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        CreateRoleResponse resultGet = (CreateRoleResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestRole", resultGet.role().roleName());
    }

    @Test
    public void iamDeleteRoleTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteRole", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteRole);
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DeleteRoleResponse resultGet = (DeleteRoleResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamGetRoleTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getRole", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.getRole);
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        GetRoleResponse resultGet = (GetRoleResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestRole", resultGet.role().roleName());
    }

    @Test
    public void iamListRolesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listRoles", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listRoles);
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        ListRolesResponse resultGet = (ListRolesResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.roles().size());
        assertEquals("TestRole", resultGet.roles().get(0).roleName());
    }

    // Policy operations tests

    @Test
    public void iamCreatePolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createPolicy);
                exchange.getIn().setHeader(IAM2Constants.POLICY_NAME, "TestPolicy");
                exchange.getIn().setHeader(IAM2Constants.POLICY_DOCUMENT,
                        "{\"Version\":\"2012-10-17\",\"Statement\":[]}");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        CreatePolicyResponse resultGet = (CreatePolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestPolicy", resultGet.policy().policyName());
    }

    @Test
    public void iamDeletePolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deletePolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deletePolicy);
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DeletePolicyResponse resultGet = (DeletePolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamGetPolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.getPolicy);
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        GetPolicyResponse resultGet = (GetPolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestPolicy", resultGet.policy().policyName());
    }

    @Test
    public void iamListPoliciesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listPolicies", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listPolicies);
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        ListPoliciesResponse resultGet = (ListPoliciesResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.policies().size());
        assertEquals("TestPolicy", resultGet.policies().get(0).policyName());
    }

    // Policy attachment operations tests

    @Test
    public void iamAttachUserPolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:attachUserPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.attachUserPolicy);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        AttachUserPolicyResponse resultGet = (AttachUserPolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamDetachUserPolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detachUserPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.detachUserPolicy);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DetachUserPolicyResponse resultGet = (DetachUserPolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamAttachGroupPolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:attachGroupPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.attachGroupPolicy);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        AttachGroupPolicyResponse resultGet = (AttachGroupPolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamDetachGroupPolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detachGroupPolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.detachGroupPolicy);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DetachGroupPolicyResponse resultGet = (DetachGroupPolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamAttachRolePolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:attachRolePolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.attachRolePolicy);
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        AttachRolePolicyResponse resultGet = (AttachRolePolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamDetachRolePolicyTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:detachRolePolicy", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.detachRolePolicy);
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
                exchange.getIn().setHeader(IAM2Constants.POLICY_ARN, "arn:aws:iam::123456789012:policy/TestPolicy");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DetachRolePolicyResponse resultGet = (DetachRolePolicyResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    // Instance profile operations tests

    @Test
    public void iamCreateInstanceProfileTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createInstanceProfile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createInstanceProfile);
                exchange.getIn().setHeader(IAM2Constants.INSTANCE_PROFILE_NAME, "TestInstanceProfile");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        CreateInstanceProfileResponse resultGet = (CreateInstanceProfileResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestInstanceProfile", resultGet.instanceProfile().instanceProfileName());
    }

    @Test
    public void iamDeleteInstanceProfileTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteInstanceProfile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteInstanceProfile);
                exchange.getIn().setHeader(IAM2Constants.INSTANCE_PROFILE_NAME, "TestInstanceProfile");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        DeleteInstanceProfileResponse resultGet = (DeleteInstanceProfileResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamGetInstanceProfileTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getInstanceProfile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.getInstanceProfile);
                exchange.getIn().setHeader(IAM2Constants.INSTANCE_PROFILE_NAME, "TestInstanceProfile");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        GetInstanceProfileResponse resultGet = (GetInstanceProfileResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("TestInstanceProfile", resultGet.instanceProfile().instanceProfileName());
    }

    @Test
    public void iamListInstanceProfilesTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listInstanceProfiles", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listInstanceProfiles);
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        ListInstanceProfilesResponse resultGet = (ListInstanceProfilesResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.instanceProfiles().size());
        assertEquals("TestInstanceProfile", resultGet.instanceProfiles().get(0).instanceProfileName());
    }

    @Test
    public void iamAddRoleToInstanceProfileTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:addRoleToInstanceProfile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.addRoleToInstanceProfile);
                exchange.getIn().setHeader(IAM2Constants.INSTANCE_PROFILE_NAME, "TestInstanceProfile");
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        AddRoleToInstanceProfileResponse resultGet = (AddRoleToInstanceProfileResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamRemoveRoleFromInstanceProfileTest() throws Exception {
        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:removeRoleFromInstanceProfile", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.removeRoleFromInstanceProfile);
                exchange.getIn().setHeader(IAM2Constants.INSTANCE_PROFILE_NAME, "TestInstanceProfile");
                exchange.getIn().setHeader(IAM2Constants.ROLE_NAME, "TestRole");
            }
        });
        MockEndpoint.assertIsSatisfied(context);
        RemoveRoleFromInstanceProfileResponse resultGet = (RemoveRoleFromInstanceProfileResponse) exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listKeys").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listAccessKeys")
                        .to("mock:result");
                from("direct:createUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createUser")
                        .to("mock:result");
                from("direct:createUserPojo")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createUser&pojoRequest=true")
                        .to("mock:result");
                from("direct:deleteUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteUser")
                        .to("mock:result");
                from("direct:listUsers").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listUsers").to("mock:result");
                from("direct:getUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=getUser").to("mock:result");
                from("direct:createAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createAccessKey")
                        .to("mock:result");
                from("direct:deleteAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteAccessKey")
                        .to("mock:result");
                from("direct:updateAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=updateAccessKey")
                        .to("mock:result");
                from("direct:createGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createGroup")
                        .to("mock:result");
                from("direct:deleteGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteGroup")
                        .to("mock:result");
                from("direct:listGroups").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listGroups")
                        .to("mock:result");
                from("direct:addUserToGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=addUserToGroup")
                        .to("mock:result");
                from("direct:removeUserFromGroup")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=removeUserFromGroup").to("mock:result");
                // Role operations
                from("direct:createRole").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createRole")
                        .to("mock:result");
                from("direct:deleteRole").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteRole")
                        .to("mock:result");
                from("direct:getRole").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=getRole")
                        .to("mock:result");
                from("direct:listRoles").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listRoles")
                        .to("mock:result");
                // Policy operations
                from("direct:createPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createPolicy")
                        .to("mock:result");
                from("direct:deletePolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deletePolicy")
                        .to("mock:result");
                from("direct:getPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=getPolicy")
                        .to("mock:result");
                from("direct:listPolicies").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listPolicies")
                        .to("mock:result");
                // Policy attachment operations
                from("direct:attachUserPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=attachUserPolicy")
                        .to("mock:result");
                from("direct:detachUserPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=detachUserPolicy")
                        .to("mock:result");
                from("direct:attachGroupPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=attachGroupPolicy")
                        .to("mock:result");
                from("direct:detachGroupPolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=detachGroupPolicy")
                        .to("mock:result");
                from("direct:attachRolePolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=attachRolePolicy")
                        .to("mock:result");
                from("direct:detachRolePolicy").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=detachRolePolicy")
                        .to("mock:result");
                // Instance profile operations
                from("direct:createInstanceProfile")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createInstanceProfile").to("mock:result");
                from("direct:deleteInstanceProfile")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteInstanceProfile").to("mock:result");
                from("direct:getInstanceProfile")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=getInstanceProfile").to("mock:result");
                from("direct:listInstanceProfiles")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listInstanceProfiles").to("mock:result");
                from("direct:addRoleToInstanceProfile")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=addRoleToInstanceProfile").to("mock:result");
                from("direct:removeRoleFromInstanceProfile")
                        .to("aws2-iam://test?iamClient=#amazonIAMClient&operation=removeRoleFromInstanceProfile")
                        .to("mock:result");
            }
        };
    }
}
