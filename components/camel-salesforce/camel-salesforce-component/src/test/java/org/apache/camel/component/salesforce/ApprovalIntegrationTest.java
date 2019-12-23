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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult;
import org.apache.camel.component.salesforce.api.dto.approval.Approvals;
import org.apache.camel.component.salesforce.api.dto.approval.Approvals.Info;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameters;

@RunWith(ParallelParameterized.class)
public class ApprovalIntegrationTest extends AbstractApprovalIntegrationTest {

    private final String format;

    public ApprovalIntegrationTest(final String format) {
        super(5);
        this.format = format;
    }

    @Test
    public void shouldSubmitAndFetchApprovals() {
        final ApprovalResult approvalResult = template.requestBody(String.format("salesforce:approval?"//
                                                                                 + "format=%s"//
                                                                                 + "&approvalActionType=Submit"//
                                                                                 + "&approvalContextId=%s"//
                                                                                 + "&approvalNextApproverIds=%s"//
                                                                                 + "&approvalComments=Integration test"//
                                                                                 + "&approvalProcessDefinitionNameOrId=Test_Account_Process", format, accountIds.get(0), userId),
                                                                   NOT_USED, ApprovalResult.class);

        assertNotNull("Approval should have resulted in value", approvalResult);

        assertEquals("There should be one Account waiting approval", 1, approvalResult.size());

        assertEquals("Instance status of the item in approval result should be `Pending`", "Pending", approvalResult.iterator().next().getInstanceStatus());

        // as it stands on 18.11.2016. the GET method on
        // /vXX.X/process/approvals/ with Accept other than
        // `application/json` results in HTTP status 500, so only JSON is
        // supported
        final Approvals approvals = template.requestBody("salesforce:approvals", NOT_USED, Approvals.class);

        assertNotNull("Approvals should be fetched", approvals);

        final List<Info> accountApprovals = approvals.approvalsFor("Account");
        assertEquals("There should be one Account waiting approval", 1, accountApprovals.size());
    }

    @Test
    public void shouldSubmitBulkApprovals() {
        final List<ApprovalRequest> approvalRequests = accountIds.stream().map(id -> {
            final ApprovalRequest request = new ApprovalRequest();
            request.setContextId(id);
            request.setComments("Approval for " + id);
            request.setActionType(Action.Submit);

            return request;
        }).collect(Collectors.toList());

        final ApprovalResult approvalResult = template.requestBody(String.format("salesforce:approval?"//
                                                                                 + "format=%s"//
                                                                                 + "&approvalActionType=Submit"//
                                                                                 + "&approvalNextApproverIds=%s"//
                                                                                 + "&approvalProcessDefinitionNameOrId=Test_Account_Process", format, userId),
                                                                   approvalRequests, ApprovalResult.class);

        assertEquals("Should have same number of approval results as requests", approvalRequests.size(), approvalResult.size());
    }

    @Parameters(name = "format = {0}")
    public static Iterable<String> formats() {
        return Arrays.asList("JSON", "XML");
    }

}
