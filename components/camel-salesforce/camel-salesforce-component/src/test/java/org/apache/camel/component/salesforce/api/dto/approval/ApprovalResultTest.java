/**
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

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;

import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult.Result;
import org.junit.Test;

import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class ApprovalResultTest {

    private static void assertResponseReadCorrectly(final ApprovalResult results) {
        final Iterator<Result> resultsIterator = results.iterator();
        assertTrue("Should deserialize one approval result result", resultsIterator.hasNext());

        final ApprovalResult.Result result = resultsIterator.next();

        assertThat("Should deserialize actorIds", result.getActorIds(), hasItems("0050Y000000u5NOQAY"));
        assertEquals("Should deserialize entityId", "0010Y000005BYrZQAW", result.getEntityId());
        assertEquals("Should deserialize instanceId", "04g0Y000000PL53QAG", result.getInstanceId());
        assertEquals("Should deserialize instanceStatus", "Pending", result.getInstanceStatus());
        assertThat("Should deserialize newWorkitemIds", result.getNewWorkitemIds(), hasItems("04i0Y000000L0fkQAC"));
        assertTrue("Should deserialize success", result.isSuccess());

        assertFalse("Should be no more results", resultsIterator.hasNext());
    }

    @Test
    public void shouldDeserializeFromJson() throws JsonProcessingException, IOException {
        final String json = "["//
            + "{"//
            + "\"actorIds\":[\"0050Y000000u5NOQAY\"],"//
            + "\"entityId\":\"0010Y000005BYrZQAW\","//
            + "\"errors\":null,"//
            + "\"instanceId\":\"04g0Y000000PL53QAG\","//
            + "\"instanceStatus\":\"Pending\","//
            + "\"newWorkitemIds\":[\"04i0Y000000L0fkQAC\"],"//
            + "\"success\":true"//
            + "}"//
            + "]";

        final ObjectMapper mapper = new ObjectMapper();

        final ApprovalResult results = mapper.readerFor(ApprovalResult.class).readValue(json);

        assertResponseReadCorrectly(results);
    }

    @Test
    public void shouldDeserializeFromXml() throws InstantiationException, IllegalAccessException {
        final ApprovalResult results = new ApprovalResult();

        final XStream xStream = new XStream();
        xStream.processAnnotations(ApprovalResult.class);

        xStream.fromXML("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"//
            + "<ProcessApprovalResult>"//
            + "<ProcessApprovalResult>"//
            + "<actorIds>0050Y000000u5NOQAY</actorIds>"//
            + "<entityId>0010Y000005BYrZQAW</entityId>"//
            + "<instanceId>04g0Y000000PL53QAG</instanceId>"//
            + "<instanceStatus>Pending</instanceStatus>"//
            + "<newWorkitemIds>04i0Y000000L0fkQAC</newWorkitemIds>"//
            + "<success>true</success>"//
            + "</ProcessApprovalResult>"//
            + "</ProcessApprovalResult>", results);

        assertResponseReadCorrectly(results);
    }
}
