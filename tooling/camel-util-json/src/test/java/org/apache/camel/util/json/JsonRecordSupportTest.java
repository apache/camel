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
package org.apache.camel.util.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JsonRecordSupportTest {

    record FlatRecord(String name, int count, boolean active) {
    }

    record RowRecord(String id, long value) {
    }

    record NestedRecord(String label, RowRecord row, List<RowRecord> rows) {
    }

    record NullableRecord(String required, String optional) {
    }

    record MapRecord(String id, Map<String, String> details) {
    }

    enum Status {
        ACTIVE,
        INACTIVE
    }

    record EnumRecord(String id, Status status) {
    }

    @Test
    void flatRecordConvertsAllFields() {
        JsonObject json = JsonRecordSupport.toJsonObject(new FlatRecord("foo", 3, true));

        assertEquals("foo", json.getString("name"));
        assertEquals(3, json.getInteger("count"));
        assertEquals(true, json.getBoolean("active"));
    }

    @Test
    void nestedRecordAndListOfRecordsConvertRecursively() {
        RowRecord row = new RowRecord("r1", 42L);
        List<RowRecord> rows = List.of(new RowRecord("r1", 1L), new RowRecord("r2", 2L));
        NestedRecord nested = new NestedRecord("top", row, rows);

        JsonObject json = JsonRecordSupport.toJsonObject(nested);

        assertEquals("top", json.getString("label"));
        JsonObject rowJson = json.getJsonObject("row");
        assertEquals("r1", rowJson.getString("id"));
        assertEquals(42L, rowJson.getLong("value"));

        JsonArray rowsJson = json.getJsonArray("rows");
        assertEquals(2, rowsJson.size());
        assertEquals("r2", ((JsonObject) rowsJson.get(1)).getString("id"));
    }

    @Test
    void nullComponentIsOmittedFromOutput() {
        JsonObject json = JsonRecordSupport.toJsonObject(new NullableRecord("present", null));

        assertEquals("present", json.getString("required"));
        assertFalse(json.containsKey("optional"));
        assertNull(json.get("optional"));
    }

    @Test
    void mapFieldConvertsToJsonObject() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("cause", "timeout");
        details.put("retryable", "true");

        JsonObject json = JsonRecordSupport.toJsonObject(new MapRecord("check1", details));

        JsonObject detailsJson = json.getJsonObject("details");
        assertEquals("timeout", detailsJson.getString("cause"));
        assertEquals("true", detailsJson.getString("retryable"));
    }

    @Test
    void enumFieldConvertsToItsName() {
        JsonObject json = JsonRecordSupport.toJsonObject(new EnumRecord("check1", Status.ACTIVE));

        assertEquals("check1", json.getString("id"));
        assertEquals("ACTIVE", json.getString("status"));
    }
}
