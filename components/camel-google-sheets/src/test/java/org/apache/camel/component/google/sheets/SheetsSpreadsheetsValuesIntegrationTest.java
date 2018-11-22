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
package org.apache.camel.component.google.sheets;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.SheetsSpreadsheetsValuesApiMethod;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values} APIs.
 */
public class SheetsSpreadsheetsValuesIntegrationTest extends AbstractGoogleSheetsTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SheetsSpreadsheetsValuesIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleSheetsApiCollection.getCollection().getApiName(SheetsSpreadsheetsValuesApiMethod.class).getName();

    @Test
    public void testGet() throws Exception {
        Spreadsheet testSheet = getSpreadsheet();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleSheets.spreadsheetId", testSheet.getSpreadsheetId());
        // parameter type is String
        headers.put("CamelGoogleSheets.range", TEST_SHEET + "!A1:B2");

        final ValueRange result = requestBodyAndHeaders("direct://GET", null, headers);

        assertNotNull("get result is null", result);
        assertEquals(TEST_SHEET + "!A1:B2", result.getRange());
        assertNull("expected empty value range but found entries", result.getValues());

        LOG.debug("get: " + result);
    }

    @Test
    public void testUpdate() throws Exception {
        Spreadsheet testSheet = getSpreadsheet();

        List<List<Object>> data = Arrays.asList(
                Arrays.asList("A1", "B1"),
                Arrays.asList("A2", "B2")
        );
        ValueRange values = new ValueRange();
        values.setValues(data);

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleSheets.spreadsheetId", testSheet.getSpreadsheetId());
        // parameter type is String
        headers.put("CamelGoogleSheets.range", TEST_SHEET + "!A1:B2");
        // parameter type is com.google.api.services.sheets.v4.model.ValueRange
        headers.put("CamelGoogleSheets.values", values);

        // parameter type is String
        headers.put("CamelGoogleSheets.valueInputOption", "USER_ENTERED");

        final UpdateValuesResponse result = requestBodyAndHeaders("direct://UPDATE", null, headers);

        assertNotNull("update result is null", result);
        assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
        assertEquals(TEST_SHEET + "!A1:B2", result.getUpdatedRange());
        assertEquals(Integer.valueOf(2), result.getUpdatedRows());
        assertEquals(Integer.valueOf(4), result.getUpdatedCells());

        LOG.debug("update: " + result);
    }

    @Test
    public void testAppend() throws Exception {
        Spreadsheet testSheet = getSpreadsheet();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleSheets.spreadsheetId", testSheet.getSpreadsheetId());
        // parameter type is String
        headers.put("CamelGoogleSheets.range", TEST_SHEET + "!A10");
        // parameter type is com.google.api.services.sheets.v4.model.ValueRange
        headers.put("CamelGoogleSheets.values", new ValueRange().setValues(Collections.singletonList(Arrays.asList("A10", "B10", "C10"))));

        // parameter type is String
        headers.put("CamelGoogleSheets.valueInputOption", "USER_ENTERED");

        final AppendValuesResponse result = requestBodyAndHeaders("direct://APPEND", null, headers);

        assertNotNull("append result is null", result);
        assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
        assertEquals(TEST_SHEET + "!A10:C10", result.getUpdates().getUpdatedRange());
        assertEquals(Integer.valueOf(1), result.getUpdates().getUpdatedRows());
        assertEquals(Integer.valueOf(3), result.getUpdates().getUpdatedCells());

        LOG.debug("append: " + result);
    }

    @Test
    public void testClear() throws Exception {
        Spreadsheet testSheet = getSpreadsheetWithTestData();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleSheets.spreadsheetId", testSheet.getSpreadsheetId());
        // parameter type is String
        headers.put("CamelGoogleSheets.range", TEST_SHEET + "!A1:B2");
        // parameter type is com.google.api.services.sheets.v4.model.ClearValuesRequest
        headers.put("CamelGoogleSheets.clearValuesRequest", new ClearValuesRequest());

        final ClearValuesResponse result = requestBodyAndHeaders("direct://CLEAR", null, headers);

        assertNotNull("clear result is null", result);
        assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
        assertEquals(TEST_SHEET + "!A1:B2", result.getClearedRange());

        LOG.debug("clear: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for append
                from("direct://APPEND")
                        .to("google-sheets://" + PATH_PREFIX + "/append");

                // test route for clear
                from("direct://CLEAR")
                        .to("google-sheets://" + PATH_PREFIX + "/clear");

                // test route for get
                from("direct://GET")
                        .to("google-sheets://" + PATH_PREFIX + "/get");

                // test route for update
                from("direct://UPDATE")
                        .to("google-sheets://" + PATH_PREFIX + "/update");
            }
        };
    }
}
