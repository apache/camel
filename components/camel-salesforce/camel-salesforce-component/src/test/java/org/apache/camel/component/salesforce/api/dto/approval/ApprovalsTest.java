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
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.dto.approval.Approvals.Info;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.Test;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class ApprovalsTest {

    @Test
    public void shouldDeserialize() throws JsonProcessingException, IOException {
        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final Object read = mapper.readerFor(Approvals.class).readValue("{\n" + //
                                                                        "  \"approvals\" : {\n" + //
                                                                        "   \"Account\" : [ {\n" + //
                                                                        "     \"description\" : null,\n" + //
                                                                        "     \"id\" : \"04aD00000008Py9\",\n" + //
                                                                        "     \"name\" : \"Account Approval Process\",\n" + //
                                                                        "     \"object\" : \"Account\",\n" + //
                                                                        "     \"sortOrder\" : 1\n" + //
                                                                        "   } ]\n" + //
                                                                        "  }\n" + //
                                                                        "}");

        assertThat("Should deserialize Approvals", read, instanceOf(Approvals.class));

        final Approvals approvals = (Approvals)read;

        final Map<String, List<Info>> approvalsMap = approvals.getApprovals();
        assertEquals("Deserialized approvals should have one entry", 1, approvalsMap.size());

        final List<Info> accountApprovals = approvalsMap.get("Account");
        assertNotNull("Deserialized approvals should contain list of `Account` type approvals", accountApprovals);

        assertEquals("There should be one approval of `Account` type", 1, accountApprovals.size());

        final Info accountInfo = accountApprovals.get(0);

        assertNull("Deserialized `Account` approval should have null description", accountInfo.getDescription());
        assertEquals("Deserialized `Account` approval should have defined id", "04aD00000008Py9", accountInfo.getId());
        assertEquals("Deserialized `Account` approval should have defined name", "Account Approval Process", accountInfo.getName());
        assertEquals("Deserialized `Account` approval should have defined object", "Account", accountInfo.getObject());
        assertEquals("Deserialized `Account` approval should have defined sortOrder", 1, accountInfo.getSortOrder());
    }
}
