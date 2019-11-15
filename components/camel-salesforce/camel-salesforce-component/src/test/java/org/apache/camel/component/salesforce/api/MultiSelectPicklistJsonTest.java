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
package org.apache.camel.component.salesforce.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.dto.generated.MSPTest;
import org.apache.camel.component.salesforce.dto.generated.StringMSPTest;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MultiSelectPicklistJsonTest {

    private static final String TEST_JSON = "{\"attributes\":{\"referenceId\":null,\"type\":\"MSPTest\",\"url\":null},\"MspField\":\"Value1;Value2;Value3\"}";
    private static final String TEST_NO_NULL_JSON = "{\"attributes\":{\"referenceId\":null,\"type\":\"MSPTest\",\"url\":null}}";
    private static final String TEST_NULL_JSON = "{\"attributes\":{\"referenceId\":null,\"type\":\"MSPTest\",\"url\":null},\"MspField\":null}";

    private static ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    @Test
    public void testMarshal() throws Exception {
        final MSPTest mspTest = new MSPTest();
        mspTest.setMspField(MSPTest.MSPEnum.values());

        String json = objectMapper.writeValueAsString(mspTest);
        assertEquals(TEST_JSON, json);

        // test null
        mspTest.setMspField(null);
        mspTest.getFieldsToNull().add("MspField");

        json = objectMapper.writeValueAsString(mspTest);
        assertEquals(TEST_NULL_JSON, json);
    }

    @Test
    public void testMarshalDoesntSerializeNulls() throws Exception {
        final MSPTest mspTest = new MSPTest();
        mspTest.setMspField(MSPTest.MSPEnum.values());
        // setting no null, but not including in fieldsToNull
        mspTest.setMspField(null);

        String json = objectMapper.writeValueAsString(mspTest);
        assertEquals(TEST_NO_NULL_JSON, json);
    }

    @Test
    public void testUnmarshal() throws Exception {
        MSPTest mspTest = objectMapper.readValue(TEST_JSON, MSPTest.class);
        assertArrayEquals(MSPTest.MSPEnum.values(), mspTest.getMspField());

        // test null
        mspTest = objectMapper.readValue(TEST_NULL_JSON, MSPTest.class);
        assertNull(mspTest.getMspField());
    }

    @Test
    public void testMarshalString() throws Exception {
        final StringMSPTest mspTest = new StringMSPTest();
        String[] stringList = new String[] {"Value1", "Value2", "Value3"};
        mspTest.setMspField(stringList);

        String json = objectMapper.writeValueAsString(mspTest);
        assertEquals(TEST_JSON, json);

        // test null
        mspTest.setMspField(null);
        mspTest.getFieldsToNull().add("MspField");

        json = objectMapper.writeValueAsString(mspTest);
        assertEquals(TEST_NULL_JSON, json);
    }

    @Test
    public void testUnmarshalString() throws Exception {
        StringMSPTest mspTest = objectMapper.readValue(TEST_JSON, StringMSPTest.class);
        String[] stringList = new String[] {"Value1", "Value2", "Value3"};
        assertArrayEquals(stringList, mspTest.getMspField());

        // test null
        mspTest = objectMapper.readValue(TEST_NULL_JSON, StringMSPTest.class);
        assertNull(mspTest.getMspField());
    }
}
