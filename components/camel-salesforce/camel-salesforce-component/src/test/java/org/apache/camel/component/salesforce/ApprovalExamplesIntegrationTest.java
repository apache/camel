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
package org.apache.camel.component.salesforce;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult;
import org.junit.Test;

public class ApprovalExamplesIntegrationTest extends AbstractApprovalIntegrationTest {

    public ApprovalExamplesIntegrationTest() {
        super(3);
    }

    @Test
    public void example1() {
        // tag::example1Usage
        final Map<String, String> body = new HashMap<>();
        body.put("contextId", accountIds.iterator().next());
        body.put("nextApproverIds", userId);

        final ApprovalResult result = template.requestBody("direct:example1", body, ApprovalResult.class);
        // end::example1Usage

        assertNotNull("Result should be received", result);
    }

    @Test
    public void example2() {
        // tag::example2Usage
        final Map<String, String> body = new HashMap<>();
        body.put("contextId", accountIds.iterator().next());
        body.put("nextApproverIds", userId);

        final ApprovalResult result = template.requestBody("direct:example2", body, ApprovalResult.class);
        // end::example2Usage

        assertNotNull("Result should be received", result);
    }

    @BindToRegistry("approvalTemplate")
    public ApprovalRequest approvalReq() throws Exception {

        final ApprovalRequest approvalTemplate = new ApprovalRequest();
        approvalTemplate.setActionType(ApprovalRequest.Action.Submit);
        approvalTemplate.setComments("Sample approval template");
        approvalTemplate.setProcessDefinitionNameOrId("Test_Account_Process");
        approvalTemplate.setSkipEntryCriteria(true);

        return approvalTemplate;
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // tag::example1Route[]
                from("direct:example1")//
                    .setHeader("approval.ContextId", simple("${body['contextId']}")).setHeader("approval.NextApproverIds", simple("${body['nextApproverIds']}"))
                    .to("salesforce:approval?"//
                        + "approvalActionType=Submit"//
                        + "&approvalComments=this is a test"//
                        + "&approvalProcessDefinitionNameOrId=Test_Account_Process"//
                        + "&approvalSkipEntryCriteria=true");
                // end::example1Route[]

                // tag::example2Route[]
                from("direct:example2")//
                    .setHeader("approval.ContextId", simple("${body['contextId']}")).setHeader("approval.NextApproverIds", simple("${body['nextApproverIds']}"))
                    .to("salesforce:approval?approval=#approvalTemplate");
                // end::example2Route[]
            }
        };
    }

}
