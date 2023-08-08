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
package org.apache.camel.component.google.sheets;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.internal.SheetsSpreadsheetsValuesApiMethod;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values} APIs.
 */
public class SheetsSpreadsheetsValuesIT {

    private static final Logger LOG = LoggerFactory.getLogger(SheetsSpreadsheetsValuesIT.class);
    private static final String PATH_PREFIX
            = GoogleSheetsApiCollection.getCollection().getApiName(SheetsSpreadsheetsValuesApiMethod.class).getName();

    public static class GetTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();

        @Test
        public void test() throws Exception {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "range", TEST_RANGE);

            final ValueRange result = requestBodyAndHeaders("direct://GET", null, headers);

            assertNotNull(result, "get result is null");
            assertEquals(TEST_RANGE, result.getRange());
            assertTrue(ObjectHelper.isEmpty(result.getValues()), "expected empty value range but found entries");

            LOG.debug("get: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent("{" + "\"range\": \"" + TEST_RANGE + "\"," + "\"majorDimension\": \"ROWS\"" + "}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://GET")
                            .to("google-sheets://" + PATH_PREFIX + "/get");
                }
            };
        }
    }

    public static class UpdateTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();
        private String range = "TEST_SHEET!A1:B2";
        private List<List<Object>> data = Arrays.asList(
                Arrays.asList("A1", "B1"),
                Arrays.asList("A2", "B2"));

        @Test
        public void test() throws Exception {

            ValueRange values = new ValueRange();
            values.setValues(data);

            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "range", range);
            // parameter type is com.google.api.services.sheets.v4.model.ValueRange
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "values", values);

            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption", "USER_ENTERED");

            final UpdateValuesResponse result = requestBodyAndHeaders("direct://UPDATE", null, headers);

            assertNotNull(result, "update result is null");
            assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
            assertEquals(range, result.getUpdatedRange());
            assertEquals(data.size(), result.getUpdatedRows());
            assertEquals(data.size() * data.get(0).size(), result.getUpdatedCells());

            LOG.debug("update: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent("{" + "\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\","
                                        + "\"updatedRange\": \"" + range + "\"," + "\"updatedRows\": "
                                        + data.size() + "," + "\"updatedColumns\": "
                                        + Optional.ofNullable(data.get(0)).map(Collection::size).orElse(0) + ","
                                        + "\"updatedCells\": "
                                        + data.size()
                                          * Optional.ofNullable(data.get(0)).map(Collection::size).orElse(0)
                                        + "}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://UPDATE")
                            .to("google-sheets://" + PATH_PREFIX + "/update");
                }
            };
        }
    }

    public static class AppendTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();
        private List<List<Object>> data = Collections.singletonList(Arrays.asList("A10", "B10", "C10"));
        private String range = TEST_SHEET + "!A10";
        private String updateRange = TEST_SHEET + "!" + data.get(0).get(0) + ":" + data.get(0).get(data.get(0).size() - 1);

        @Test
        public void test() throws Exception {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "range", range);
            // parameter type is com.google.api.services.sheets.v4.model.ValueRange
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "values", new ValueRange().setValues(data));

            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption", "USER_ENTERED");

            final AppendValuesResponse result = requestBodyAndHeaders("direct://APPEND", null, headers);

            assertNotNull(result, "append result is null");
            assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
            assertEquals(updateRange, result.getUpdates().getUpdatedRange());
            assertEquals(data.size(), result.getUpdates().getUpdatedRows());
            assertEquals(data.get(0).size(), result.getUpdates().getUpdatedCells());

            LOG.debug("append: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent(
                                    "{" + "\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\"," + "\"updates\":" + "{"
                                        + "\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\","
                                        + "\"updatedRange\": \"" + updateRange + "\"," + "\"updatedRows\": "
                                        + data.size() + "," + "\"updatedColumns\": "
                                        + Optional.ofNullable(data.get(0)).map(Collection::size).orElse(0) + ","
                                        + "\"updatedCells\": "
                                        + data.size()
                                          * Optional.ofNullable(data.get(0)).map(Collection::size).orElse(0)
                                        + "}" + "}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://APPEND")
                            .to("google-sheets://" + PATH_PREFIX + "/append");
                }
            };
        }
    }

    public static class ClearTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();

        @Test
        public void test() throws Exception {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "range", TEST_RANGE);
            // parameter type is com.google.api.services.sheets.v4.model.ClearValuesRequest
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "clearValuesRequest", new ClearValuesRequest());

            final ClearValuesResponse result = requestBodyAndHeaders("direct://CLEAR", null, headers);

            assertNotNull(result, "clear result is null");
            assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());
            assertEquals(TEST_RANGE, result.getClearedRange());

            LOG.debug("clear: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse().setContent(
                            "{" + "\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\"," + "\"clearedRange\": \""
                                                              + TEST_RANGE + "\"" + "}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://CLEAR")
                            .to("google-sheets://" + PATH_PREFIX + "/clear");
                }
            };
        }
    }
}
