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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.model.AddUserToGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.CreateGroupResponse;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.DeleteAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.DeleteGroupResponse;
import software.amazon.awssdk.services.iam.model.DeleteUserResponse;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.ListGroupsResponse;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
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
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listAccessKeys);
            }
        });

        assertMockEndpointsSatisfied();

        ListAccessKeysResponse resultGet = (ListAccessKeysResponse)exchange.getIn().getBody();
        assertEquals(1, resultGet.accessKeyMetadata().size());
        assertEquals("1", resultGet.accessKeyMetadata().get(0).accessKeyId());
    }

    @Test
    public void iamCreateUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateUserResponse resultGet = (CreateUserResponse)exchange.getIn().getBody();
        assertEquals("test", resultGet.user().userName());
    }

    @Test
    public void iamDeleteUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteUserResponse resultGet = (DeleteUserResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamListUsersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listUsers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listUsers);
            }
        });

        assertMockEndpointsSatisfied();

        ListUsersResponse resultGet = (ListUsersResponse)exchange.getIn().getBody();
        assertEquals(1, resultGet.users().size());
        assertEquals("test", resultGet.users().get(0).userName());
    }

    @Test
    public void iamCreateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createAccessKey);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateAccessKeyResponse resultGet = (CreateAccessKeyResponse)exchange.getIn().getBody();
        assertEquals("test", resultGet.accessKey().accessKeyId());
        assertEquals("testSecret", resultGet.accessKey().secretAccessKey());
    }

    @Test
    public void iamDeleteAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteAccessKey);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_ID, "1");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteAccessKeyResponse resultGet = (DeleteAccessKeyResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamGetUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.getUser);
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        GetUserResponse resultGet = (GetUserResponse)exchange.getIn().getBody();
        assertEquals("test", resultGet.user().userName());
    }

    @Test
    public void iamUpdateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.updateAccessKey);
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_ID, "1");
                exchange.getIn().setHeader(IAM2Constants.ACCESS_KEY_STATUS, StatusType.INACTIVE.name());
            }
        });

        assertMockEndpointsSatisfied();

        UpdateAccessKeyResponse resultGet = (UpdateAccessKeyResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Test
    public void iamCreateGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.createGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.GROUP_PATH, "/test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateGroupResponse resultGet = (CreateGroupResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("Test", resultGet.group().groupName());
        assertEquals("/test", resultGet.group().path());
    }

    public void iamDeleteGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.deleteGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteGroupResponse resultGet = (DeleteGroupResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    public void iamListGroupsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listGroups", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.listGroups);
            }
        });

        assertMockEndpointsSatisfied();

        ListGroupsResponse resultGet = (ListGroupsResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.groups().size());
        assertEquals("Test", resultGet.groups().get(0).groupName());
    }

    public void iamAddUserToGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:addUserToGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.addUserToGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        AddUserToGroupResponse resultGet = (AddUserToGroupResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    public void iamRemoveUserFromGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:removeUserFromGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAM2Constants.OPERATION, IAM2Operations.removeUserFromGroup);
                exchange.getIn().setHeader(IAM2Constants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAM2Constants.USERNAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        RemoveUserFromGroupResponse resultGet = (RemoveUserFromGroupResponse)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listKeys").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listAccessKeys").to("mock:result");
                from("direct:createUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createUser").to("mock:result");
                from("direct:deleteUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteUser").to("mock:result");
                from("direct:listUsers").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listUsers").to("mock:result");
                from("direct:getUser").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=getUser").to("mock:result");
                from("direct:createAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createAccessKey").to("mock:result");
                from("direct:deleteAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteAccessKey").to("mock:result");
                from("direct:updateAccessKey").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=updateAccessKey").to("mock:result");
                from("direct:createGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=createGroup").to("mock:result");
                from("direct:deleteGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=deleteGroup").to("mock:result");
                from("direct:listGroups").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=listGroups").to("mock:result");
                from("direct:addUserToGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=addUserToGroup").to("mock:result");
                from("direct:removeUserFromGroup").to("aws2-iam://test?iamClient=#amazonIAMClient&operation=removeUserFromGroup").to("mock:result");
            }
        };
    }
}
