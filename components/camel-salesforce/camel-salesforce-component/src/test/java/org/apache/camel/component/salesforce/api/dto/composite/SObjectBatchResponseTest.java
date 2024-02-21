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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SObjectBatchResponseTest {

    @Test
    public void shouldDeserializeFromJson() throws IOException {

        final String json = "{\n"//
                            + "   \"hasErrors\" : false,\n"//
                            + "   \"results\" : [{\n"//
                            + "      \"statusCode\" : 204,\n"//
                            + "      \"result\" : null\n"//
                            + "      },{\n"//
                            + "      \"statusCode\" : 200,\n"//
                            + "      \"result\": {\n"//
                            + "         \"attributes\" : {\n"//
                            + "            \"type\" : \"Account\",\n"//
                            + "            \"url\" : \"/services/data/v34.0/sobjects/Account/001D000000K0fXOIAZ\"\n"//
                            + "         },\n"//
                            + "         \"Name\" : \"NewName\",\n"//
                            + "         \"BillingPostalCode\" : \"94105\",\n"//
                            + "         \"Id\" : \"001D000000K0fXOIAZ\"\n"//
                            + "      }\n"//
                            + "   }]\n"//
                            + "}";

        final ObjectMapper mapper = JsonUtils.createObjectMapper();

        final SObjectBatchResponse response = mapper.readerFor(SObjectBatchResponse.class).readValue(json);

        assertResponse(response);
    }

    static void assertResponse(final SObjectBatchResponse response) {
        assertNotNull(response, "Response should be parsed");

        assertFalse(response.hasErrors(), "It should not have errors");

        final List<SObjectBatchResult> results = response.getResults();
        assertEquals(2, results.size(), "It should contain 2 results");

        final SObjectBatchResult firstResult = results.get(0);
        assertEquals(204, firstResult.getStatusCode(), "First result should have status code of 204");
        assertNull(firstResult.getResult(), "First result contain no data");

        final SObjectBatchResult secondResult = results.get(1);
        assertEquals(200, secondResult.getStatusCode(), "Second result should have status code of 200");

        @SuppressWarnings("unchecked")
        final Map<String, Object> secondResultMap = (Map<String, Object>) secondResult.getResult();
        @SuppressWarnings("unchecked")
        final Map<String, String> attributes = (Map<String, String>) secondResultMap.get("attributes");
        assertEquals("Account", attributes.get("type"), "Second result data should have attribute type set to `Account`");
        assertEquals("/services/data/v34.0/sobjects/Account/001D000000K0fXOIAZ", attributes.get("url"),
                "Second result data should have attribute url set as expected");

        assertEquals("NewName", secondResultMap.get("Name"), "Second result data should have `NewName` set as expected");
        assertEquals("94105", secondResultMap.get("BillingPostalCode"),
                "Second result data should have `BillingPostalCode` set as expected");
        assertEquals("001D000000K0fXOIAZ", secondResultMap.get("Id"), "Second result data should have `Id` set as expected");
    }

}
