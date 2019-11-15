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

import com.amazonaws.services.identitymanagement.model.AddUserToGroupResult;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateGroupResult;
import com.amazonaws.services.identitymanagement.model.CreateUserResult;
import com.amazonaws.services.identitymanagement.model.DeleteAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.DeleteGroupResult;
import com.amazonaws.services.identitymanagement.model.DeleteUserResult;
import com.amazonaws.services.identitymanagement.model.GetUserResult;
import com.amazonaws.services.identitymanagement.model.ListAccessKeysResult;
import com.amazonaws.services.identitymanagement.model.ListGroupsResult;
import com.amazonaws.services.identitymanagement.model.ListUsersResult;
import com.amazonaws.services.identitymanagement.model.RemoveUserFromGroupResult;
import com.amazonaws.services.identitymanagement.model.StatusType;
import com.amazonaws.services.identitymanagement.model.UpdateAccessKeyResult;
import org.apache.camel.BindToRegistry;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class IAMProducerTest extends CamelTestSupport {

    @BindToRegistry("amazonIAMClient")
    AmazonIAMClientMock clientMock = new AmazonIAMClientMock();

    @EndpointInject("mock:result")
    private MockEndpoint mock;

    @Test
    public void iamListKeysTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listKeys", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.listAccessKeys);
            }
        });

        assertMockEndpointsSatisfied();

        ListAccessKeysResult resultGet = (ListAccessKeysResult)exchange.getIn().getBody();
        assertEquals(1, resultGet.getAccessKeyMetadata().size());
        assertEquals("1", resultGet.getAccessKeyMetadata().get(0).getAccessKeyId());
    }

    @Test
    public void iamCreateUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.createUser);
                exchange.getIn().setHeader(IAMConstants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateUserResult resultGet = (CreateUserResult)exchange.getIn().getBody();
        assertEquals("test", resultGet.getUser().getUserName());
    }

    @Test
    public void iamDeleteUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.deleteUser);
                exchange.getIn().setHeader(IAMConstants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteUserResult resultGet = (DeleteUserResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }
    
    @Test
    public void iamGetUserTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:getUser", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.getUser);
                exchange.getIn().setHeader(IAMConstants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        GetUserResult resultGet = (GetUserResult)exchange.getIn().getBody();
        assertEquals("test", resultGet.getUser().getUserName());
    }

    @Test
    public void iamListUsersTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listUsers", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.listUsers);
            }
        });

        assertMockEndpointsSatisfied();

        ListUsersResult resultGet = (ListUsersResult)exchange.getIn().getBody();
        assertEquals(1, resultGet.getUsers().size());
        assertEquals("test", resultGet.getUsers().get(0).getUserName());
    }
    
    @Test
    public void iamCreateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.createAccessKey);
                exchange.getIn().setHeader(IAMConstants.USERNAME, "test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateAccessKeyResult resultGet = (CreateAccessKeyResult) exchange.getIn().getBody();
        assertEquals("test", resultGet.getAccessKey().getAccessKeyId());
        assertEquals("testSecret", resultGet.getAccessKey().getSecretAccessKey());
    }
    
    @Test
    public void iamDeleteAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.deleteAccessKey);
                exchange.getIn().setHeader(IAMConstants.USERNAME, "test");
                exchange.getIn().setHeader(IAMConstants.ACCESS_KEY_ID, "1");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteAccessKeyResult resultGet = (DeleteAccessKeyResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }
    
    @Test
    public void iamUpdateAccessKeyTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:updateAccessKey", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.updateAccessKey);
                exchange.getIn().setHeader(IAMConstants.ACCESS_KEY_ID, "1");
                exchange.getIn().setHeader(IAMConstants.ACCESS_KEY_STATUS, StatusType.Inactive.toString());
            }
        });

        assertMockEndpointsSatisfied();

        UpdateAccessKeyResult resultGet = (UpdateAccessKeyResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }
    
    @Test
    public void iamCreateGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:createGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.createGroup);
                exchange.getIn().setHeader(IAMConstants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAMConstants.GROUP_PATH, "/test");
            }
        });

        assertMockEndpointsSatisfied();

        CreateGroupResult resultGet = (CreateGroupResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals("Test", resultGet.getGroup().getGroupName());
        assertEquals("/test", resultGet.getGroup().getPath());
    }
    
    @Test
    public void iamDeleteGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:deleteGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.deleteGroup);
                exchange.getIn().setHeader(IAMConstants.GROUP_NAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        DeleteGroupResult resultGet = (DeleteGroupResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }
    
    public void iamListGroupsTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:listGroups", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.listGroups);
            }
        });

        assertMockEndpointsSatisfied();

        ListGroupsResult resultGet = (ListGroupsResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
        assertEquals(1, resultGet.getGroups().size());
        assertEquals("Test", resultGet.getGroups().get(0).getGroupName());
    }
    
    public void iamAddUserToGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:addUserToGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.addUserToGroup);
                exchange.getIn().setHeader(IAMConstants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAMConstants.USERNAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        AddUserToGroupResult resultGet = (AddUserToGroupResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }
    
    public void iamRemoveUserFromGroupTest() throws Exception {

        mock.expectedMessageCount(1);
        Exchange exchange = template.request("direct:removeUserFromGroup", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(IAMConstants.OPERATION, IAMOperations.removeUserFromGroup);
                exchange.getIn().setHeader(IAMConstants.GROUP_NAME, "Test");
                exchange.getIn().setHeader(IAMConstants.USERNAME, "Test");
            }
        });

        assertMockEndpointsSatisfied();

        RemoveUserFromGroupResult resultGet = (RemoveUserFromGroupResult)exchange.getIn().getBody();
        assertNotNull(resultGet);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:listKeys").to("aws-iam://test?iamClient=#amazonIAMClient&operation=listAccessKeys").to("mock:result");
                from("direct:createUser").to("aws-iam://test?iamClient=#amazonIAMClient&operation=createUser").to("mock:result");
                from("direct:deleteUser").to("aws-iam://test?iamClient=#amazonIAMClient&operation=deleteUser").to("mock:result");
                from("direct:listUsers").to("aws-iam://test?iamClient=#amazonIAMClient&operation=listUsers").to("mock:result");
                from("direct:getUser").to("aws-iam://test?iamClient=#amazonIAMClient&operation=getUser").to("mock:result");
                from("direct:createAccessKey").to("aws-iam://test?iamClient=#amazonIAMClient&operation=createAccessKey").to("mock:result");
                from("direct:deleteAccessKey").to("aws-iam://test?iamClient=#amazonIAMClient&operation=deleteAccessKey").to("mock:result");
                from("direct:updateAccessKey").to("aws-iam://test?iamClient=#amazonIAMClient&operation=updateAccessKey").to("mock:result");
                from("direct:createGroup").to("aws-iam://test?iamClient=#amazonIAMClient&operation=createGroup").to("mock:result");
                from("direct:deleteGroup").to("aws-iam://test?iamClient=#amazonIAMClient&operation=deleteGroup").to("mock:result");
                from("direct:listGroups").to("aws-iam://test?iamClient=#amazonIAMClient&operation=listGroups").to("mock:result");
                from("direct:addUserToGroup").to("aws-iam://test?iamClient=#amazonIAMClient&operation=addUserToGroup").to("mock:result");
                from("direct:removeUserFromGroup").to("aws-iam://test?iamClient=#amazonIAMClient&operation=removeUserFromGroup").to("mock:result");
            }
        };
    }
}
