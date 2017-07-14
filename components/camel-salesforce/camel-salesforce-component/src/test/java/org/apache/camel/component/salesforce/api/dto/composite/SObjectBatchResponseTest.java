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
package org.apache.camel.component.salesforce.api.dto.composite;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thoughtworks.xstream.XStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SObjectBatchResponseTest {

    static void assertResponse(final SObjectBatchResponse response) {
        assertNotNull("Response should be parsed", response);

        assertFalse("It should not have errors", response.hasErrors());

        final List<SObjectBatchResult> results = response.getResults();
        assertEquals("It should contain 2 results", 2, results.size());

        final SObjectBatchResult firstResult = results.get(0);
        assertEquals("First result should have status code of 204", 204, firstResult.getStatusCode());
        assertNull("First result contain no data", firstResult.getResult());

        final SObjectBatchResult secondResult = results.get(1);
        assertEquals("Second result should have status code of 200", 200, secondResult.getStatusCode());

        @SuppressWarnings("unchecked")
        final Map<String, Object> secondResultMap = (Map<String, Object>) secondResult.getResult();
        @SuppressWarnings("unchecked")
        final Map<String, String> attributes = (Map<String, String>) secondResultMap.get("attributes");
        assertEquals("Second result data should have attribute type set to `Account`", "Account",
            attributes.get("type"));
        assertEquals("Second result data should have attribute url set as expected",
            "/services/data/v34.0/sobjects/Account/001D000000K0fXOIAZ", attributes.get("url"));

        assertEquals("Second result data should have `NewName` set as expected", "NewName",
            secondResultMap.get("Name"));
        assertEquals("Second result data should have `BillingPostalCode` set as expected", "94105",
            secondResultMap.get("BillingPostalCode"));
        assertEquals("Second result data should have `Id` set as expected", "001D000000K0fXOIAZ",
            secondResultMap.get("Id"));
    }

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

        final ObjectMapper mapper = new ObjectMapper();

        final SObjectBatchResponse response = mapper.readerFor(SObjectBatchResponse.class).readValue(json);

        assertResponse(response);
    }

    @Test
    public void shouldDeserializeFromXml() {
        final String xml = "<batchResults>\n"//
            + "    <hasErrors>false</hasErrors>\n"//
            + "    <results>\n"//
            + "        <batchResult>\n"//
            + "            <statusCode>204</statusCode>\n"//
            + "            <result/>\n"//
            + "        </batchResult>\n"//
            + "        <batchResult>\n"//
            + "            <statusCode>200</statusCode>\n"//
            + "            <result>\n"//
            + "                <Account type=\"Account\" url=\"/services/data/v34.0/sobjects/Account/001D000000K0fXOIAZ\">\n"//
            + "                    <Id>001D000000K0fXOIAZ</Id>\n"//
            + "                    <Name>NewName</Name>\n"//
            + "                    <BillingPostalCode>94105</BillingPostalCode>"//
            + "                </Account>\n"//
            + "            </result>\n"//
            + "        </batchResult>\n"//
            + "    </results>\n"//
            + "</batchResults>";

        final XStream xStream = new XStream();
        xStream.processAnnotations(new Class[] {SObjectBatchResponse.class});

        final SObjectBatchResponse response = (SObjectBatchResponse) xStream.fromXML(xml);

        assertNotNull("Response should be parsed", response);

        assertFalse("It should not have errors", response.hasErrors());

        final List<SObjectBatchResult> results = response.getResults();
        assertEquals("It should contain 2 results", 2, results.size());

        final SObjectBatchResult firstResult = results.get(0);
        assertEquals("First result should have status code of 204", 204, firstResult.getStatusCode());
        assertTrue("First result contain no data", ((Map) firstResult.getResult()).isEmpty());

        final SObjectBatchResult secondResult = results.get(1);
        assertEquals("Second result should have status code of 200", 200, secondResult.getStatusCode());

        @SuppressWarnings("unchecked")
        final Map<String, Object> secondResultMap = (Map<String, Object>) secondResult.getResult();
        @SuppressWarnings("unchecked")
        final Map<String, Object> account = (Map<String, Object>) secondResultMap.get("Account");

        @SuppressWarnings("unchecked")
        final Map<String, String> attributes = (Map<String, String>) account.get("attributes");
        assertEquals("Second result data should have attribute type set to `Account`", "Account",
            attributes.get("type"));
        assertEquals("Second result data should have attribute url set as expected",
            "/services/data/v34.0/sobjects/Account/001D000000K0fXOIAZ", attributes.get("url"));

        assertEquals("Second result data should have `NewName` set as expected", "NewName", account.get("Name"));
        assertEquals("Second result data should have `BillingPostalCode` set as expected", "94105",
            account.get("BillingPostalCode"));
        assertEquals("Second result data should have `Id` set as expected", "001D000000K0fXOIAZ", account.get("Id"));
    }

}
