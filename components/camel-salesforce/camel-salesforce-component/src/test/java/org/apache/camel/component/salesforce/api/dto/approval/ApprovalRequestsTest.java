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

import java.util.Arrays;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalRequest.Action;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.api.utils.XStreamUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApprovalRequestsTest {

    private final ApprovalRequests requests;

    public ApprovalRequestsTest() {
        final String actorId = "005D00000015rZy";

        final ApprovalRequest request1 = new ApprovalRequest();
        request1.setActionType(Action.Submit);
        request1.setContextId("001D000000I8mIm");
        request1.setNextApproverIds("005D00000015rY9");
        request1.setComments("this is a test 1");
        request1.setContextActorId(actorId);
        request1.setProcessDefinitionNameOrId("PTO_Request_Process");
        request1.setSkipEntryCriteria(true);

        final ApprovalRequest request2 = new ApprovalRequest();
        request2.setActionType(Action.Submit);
        request2.setContextId("001D000000I8dIm");
        request2.setNextApproverIds("005D00000015xY9");
        request2.setComments("this is a test 2");
        request2.setContextActorId(actorId);
        request2.setProcessDefinitionNameOrId("PTO_Request_Process");
        request2.setSkipEntryCriteria(true);

        requests = new ApprovalRequests(Arrays.asList(request1, request2));
    }

    @Test
    public void shouldSerializeAsJson() throws JsonProcessingException {
        final String json = "{\"requests\":["//
                            + "{"//
                            + "\"actionType\":\"Submit\","//
                            + "\"contextActorId\":\"005D00000015rZy\","//
                            + "\"contextId\":\"001D000000I8mIm\","//
                            + "\"comments\":\"this is a test 1\","//
                            + "\"nextApproverIds\":[\"005D00000015rY9\"],"//
                            + "\"processDefinitionNameOrId\":\"PTO_Request_Process\","//
                            + "\"skipEntryCriteria\":true"//
                            + "},{"//
                            + "\"actionType\":\"Submit\","//
                            + "\"contextActorId\":\"005D00000015rZy\","//
                            + "\"contextId\":\"001D000000I8dIm\","//
                            + "\"comments\":\"this is a test 2\","//
                            + "\"nextApproverIds\":[\"005D00000015xY9\"],"//
                            + "\"processDefinitionNameOrId\":\"PTO_Request_Process\","//
                            + "\"skipEntryCriteria\":true"//
                            + "}"//
                            + "]}";

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final String serialized = mapper.writerFor(ApprovalRequests.class).writeValueAsString(requests);

        assertEquals("Approval requests should serialize as JSON", json, serialized);
    }

    @Test
    public void shouldSerializeAsXml() {
        final String xml = "<ProcessApprovalRequest>"//
                           + "<requests>"//
                           + "<actionType>Submit</actionType>"//
                           + "<contextActorId>005D00000015rZy</contextActorId>"//
                           + "<contextId>001D000000I8mIm</contextId>"//
                           + "<comments>this is a test 1</comments>"//
                           + "<nextApproverIds>005D00000015rY9</nextApproverIds>"//
                           + "<processDefinitionNameOrId>PTO_Request_Process</processDefinitionNameOrId>"//
                           + "<skipEntryCriteria>true</skipEntryCriteria>"//
                           + "</requests>"//
                           + "<requests>"//
                           + "<actionType>Submit</actionType>"//
                           + "<contextActorId>005D00000015rZy</contextActorId>"//
                           + "<contextId>001D000000I8dIm</contextId>"//
                           + "<comments>this is a test 2</comments>"//
                           + "<nextApproverIds>005D00000015xY9</nextApproverIds>"//
                           + "<processDefinitionNameOrId>PTO_Request_Process</processDefinitionNameOrId>"//
                           + "<skipEntryCriteria>true</skipEntryCriteria>"//
                           + "</requests>"//
                           + "</ProcessApprovalRequest>";

        final XStream xStream = XStreamUtils.createXStream(ApprovalRequests.class);

        final String serialized = xStream.toXML(requests);

        assertEquals("Approval requests should serialize as XML", xml, serialized);
    }
}
