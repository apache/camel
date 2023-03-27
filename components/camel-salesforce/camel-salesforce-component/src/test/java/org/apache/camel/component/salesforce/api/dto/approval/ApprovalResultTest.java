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

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.dto.approval.ApprovalResult.Result;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ApprovalResultTest {

    @Test
    public void shouldDeserializeFromJson() throws IOException {
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

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final ApprovalResult results = mapper.readerFor(ApprovalResult.class).readValue(json);

        assertResponseReadCorrectly(results);
    }

    private static void assertResponseReadCorrectly(final ApprovalResult results) {
        final Iterator<Result> resultsIterator = results.iterator();
        assertTrue(resultsIterator.hasNext(), "Should deserialize one approval result result");

        final ApprovalResult.Result result = resultsIterator.next();

        assertThat("Should deserialize actorIds", result.getActorIds(), hasItems("0050Y000000u5NOQAY"));
        assertEquals("0010Y000005BYrZQAW", result.getEntityId(), "Should deserialize entityId");
        assertEquals("04g0Y000000PL53QAG", result.getInstanceId(), "Should deserialize instanceId");
        assertEquals("Pending", result.getInstanceStatus(), "Should deserialize instanceStatus");
        assertThat("Should deserialize newWorkitemIds", result.getNewWorkitemIds(), hasItems("04i0Y000000L0fkQAC"));
        assertTrue(result.isSuccess(), "Should deserialize success");

        assertFalse(resultsIterator.hasNext(), "Should be no more results");
    }
}
