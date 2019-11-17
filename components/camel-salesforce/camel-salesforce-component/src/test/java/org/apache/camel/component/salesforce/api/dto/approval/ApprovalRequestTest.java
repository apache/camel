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
package org.apache.camel.component.salesforce.api.dto.approval;

import java.util.Collections;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.XStreamUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ApprovalRequestTest {

    private final ApprovalRequest sampleRequest;

    public ApprovalRequestTest() {
        sampleRequest = new ApprovalRequest();

        sampleRequest.setActionType(Action.Submit);
        sampleRequest.setContextId("001D000000I8mIm");
        sampleRequest.addNextApproverId("005D00000015rY9");
        sampleRequest.setComments("this is a test");
        sampleRequest.setContextActorId("005D00000015rZy");
        sampleRequest.setProcessDefinitionNameOrId("PTO_Request_Process");
        sampleRequest.setSkipEntryCriteria(true);

    }

    @Test
    public void shouldApplyValuesFromTemplate() {
        final ApprovalRequest request = new ApprovalRequest();
        request.setActionType(Action.Approve);
        request.setComments("Comment");
        request.setContextId("Context Id");

        final ApprovalRequest template = new ApprovalRequest();
        template.setActionType(Action.Reject);
        template.setComments("Template comment");
        template.setNextApproverIds(Collections.singletonList("Next approver "));

        final ApprovalRequest combined = request.applyTemplate(template);

        assertThat("Combined approval request should be a new instance", combined, both(not(sameInstance(request))).and(not(sameInstance(template))));

        assertEquals("Action type should not be overwritten", request.getActionType(), combined.getActionType());
        assertEquals("Comment should not be overwritten", request.getComments(), combined.getComments());
        assertEquals("Context id should not be overwritten", request.getContextId(), combined.getContextId());
        assertEquals("Next approver id should be taken from template", template.getNextApproverIds(), combined.getNextApproverIds());
    }

    @Test
    public void shouldSerializeAsJson() throws JsonProcessingException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final String json = mapper.writerFor(ApprovalRequest.class).writeValueAsString(sampleRequest);

        assertEquals("ApprovalRequest should serialize as JSON from Salesforce examples",
                     "{\"actionType\":\"Submit\",\"contextActorId\":\"005D00000015rZy\",\"contextId\":\"001D000000I8mIm\""
                                                                                          + ",\"comments\":\"this is a test\",\"nextApproverIds\":[\"005D00000015rY9\"],"
                                                                                          + "\"processDefinitionNameOrId\":\"PTO_Request_Process\",\"skipEntryCriteria\":true}",
                     json);
    }

    @Test
    public void shouldSerializeAsXml() {
        final XStream xStream = XStreamUtils.createXStream(ApprovalRequest.class);

        final String xml = xStream.toXML(sampleRequest);

        assertEquals("ApprovalRequest should serialize as XML", "<requests>"//
                                                                + "<actionType>Submit</actionType>"//
                                                                + "<contextActorId>005D00000015rZy</contextActorId>"//
                                                                + "<contextId>001D000000I8mIm</contextId>"//
                                                                + "<comments>this is a test</comments>"//
                                                                + "<nextApproverIds>005D00000015rY9</nextApproverIds>"//
                                                                + "<processDefinitionNameOrId>PTO_Request_Process</processDefinitionNameOrId>"//
                                                                + "<skipEntryCriteria>true</skipEntryCriteria>"//
                                                                + "</requests>",
                     xml);
    }

    @Test
    public void shouldTolerateNullTemplates() {
        final ApprovalRequest request = new ApprovalRequest();

        final ApprovalRequest appliedTo = request.applyTemplate(null);

        assertThat("For null templates applyTemplate should return same object", appliedTo, sameInstance(request));
    }

}
