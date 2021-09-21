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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.util.Arrays;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SObjectTreeResponseTest {

    @Test
    public void shouldDeserializeJsonFromSalesforceExample() throws Exception {
        final String json = "{\n"//
                            + "    \"hasErrors\" : false,\n"//
                            + "    \"results\" : [{\n"//
                            + "     \"referenceId\" : \"ref1\",\n"//
                            + "     \"id\" : \"001D000000K0fXOIAZ\"\n"//
                            + "     },{\n"//
                            + "     \"referenceId\" : \"ref4\",\n"//
                            + "     \"id\" : \"001D000000K0fXPIAZ\"\n"//
                            + "     },{\n"//
                            + "     \"referenceId\" : \"ref2\",\n"//
                            + "     \"id\" : \"003D000000QV9n2IAD\"\n"//
                            + "     },{\n"//
                            + "     \"referenceId\" : \"ref3\",\n"//
                            + "     \"id\" : \"003D000000QV9n3IAD\"\n"//
                            + "     }]\n"//
                            + "}";

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final ObjectReader reader = mapper.readerFor(SObjectTreeResponse.class);
        final SObjectTreeResponse response = reader.readValue(json);

        assertNotNull(response, "Response should be parsed");

        assertFalse(response.hasErrors(), "`hasErrors` flag should be false");

        assertEquals(4, response.getResults().size(), "Should read 4 references");
        assertThat("4 references should be read as expected", response.getResults(),
                hasItems(new ReferenceId("ref1", "001D000000K0fXOIAZ", Collections.emptyList()), //
                        new ReferenceId("ref4", "001D000000K0fXPIAZ", Collections.emptyList()), //
                        new ReferenceId("ref2", "003D000000QV9n2IAD", Collections.emptyList()), //
                        new ReferenceId("ref3", "003D000000QV9n3IAD", Collections.emptyList())));
    }

    @Test
    public void shouldDeserializeJsonFromSalesforceFailureExample() throws Exception {
        final String json = "{\n"//
                            + "   \"hasErrors\" : true,\n"//
                            + "   \"results\" : [{\n"//
                            + "     \"referenceId\" : \"ref2\",\n"//
                            + "     \"errors\" : [{\n"//
                            + "       \"statusCode\" : \"INVALID_EMAIL_ADDRESS\",\n"//
                            + "       \"message\" : \"Email: invalid email address: 123\",\n"//
                            + "       \"fields\" : [ \"Email\" ]\n"//
                            + "       }]\n"//
                            + "     }]\n"//
                            + "}";

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final ObjectReader reader = mapper.readerFor(SObjectTreeResponse.class);
        final SObjectTreeResponse response = reader.readValue(json);

        assertNotNull(response, "Response should be parsed");

        assertTrue(response.hasErrors(), "`hasErrors` flag should be true");

        assertEquals(1, response.getResults().size(), "Should read one reference");
        assertThat("The reference should be read as expected", response.getResults(),
                hasItems(new ReferenceId(
                        "ref2", null, Arrays.asList(new RestError(
                                "INVALID_EMAIL_ADDRESS", "Email: invalid email address: 123", Arrays.asList("Email"))))));
    }
}
