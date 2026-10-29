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
package org.apache.camel.component.aws2.sts.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.sts.STS2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.DeleteRoleRequest;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StsAssumeRoleIT extends Aws2StsBase {

    private static final String ROLE_NAME = "camel-sts-assume-role-it";

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private IamClient iamClient;
    private String roleArn;

    @BeforeAll
    public void createRole() {
        // a role the account of the test may assume
        String account;
        try (StsClient stsClient = AWSSDKClientUtils.newSTSClient()) {
            account = stsClient.getCallerIdentity().account();
        }
        String trustPolicy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                             + "\"Principal\":{\"AWS\":\"arn:aws:iam::" + account + ":root\"},"
                             + "\"Action\":\"sts:AssumeRole\"}]}";
        iamClient = AWSSDKClientUtils.newIAMClient();
        roleArn = iamClient.createRole(CreateRoleRequest.builder().roleName(ROLE_NAME)
                .assumeRolePolicyDocument(trustPolicy).build()).role().arn();
    }

    @AfterAll
    public void deleteRole() {
        if (iamClient != null) {
            try {
                iamClient.deleteRole(DeleteRoleRequest.builder().roleName(ROLE_NAME).build());
            } finally {
                iamClient.close();
            }
        }
    }

    @Test
    public void sendIn() throws Exception {
        result.expectedMessageCount(1);

        template.send("direct:assumeRole", exchange -> {
            exchange.getIn().setHeader(STS2Constants.OPERATION, "assumeRole");
            exchange.getIn().setHeader(STS2Constants.ROLE_SESSION_NAME, "user_test");
            exchange.getIn().setHeader(STS2Constants.ROLE_ARN, roleArn);
        });

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(1, result.getExchanges().size());
        AssumeRoleResponse response = result.getExchanges().get(0).getIn().getBody(AssumeRoleResponse.class);
        assertNotNull(response.credentials().accessKeyId());
        assertNotNull(response.assumedRoleUser().assumedRoleId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String awsEndpoint
                        = "aws2-sts://default?operation=assumeRole";
                from("direct:assumeRole").to(awsEndpoint).to("mock:result");
            }
        };
    }
}
