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

import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateSpreadsheetPropertiesRequest;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiCollection;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.internal.SheetsSpreadsheetsApiMethod;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link com.google.api.services.sheets.v4.Sheets.Spreadsheets} APIs.
 */
public class SheetsSpreadsheetsIT {

    private static final Logger LOG = LoggerFactory.getLogger(SheetsSpreadsheetsIT.class);
    private static final String PATH_PREFIX
            = GoogleSheetsApiCollection.getCollection().getApiName(SheetsSpreadsheetsApiMethod.class).getName();

    public static class CreateTest extends AbstractGoogleSheetsTestSupport {
        private String title = "camel-sheets-" + new SecureRandom().nextInt(Integer.MAX_VALUE);

        @Test
        public void test() {
            Spreadsheet sheetToCreate = new Spreadsheet();
            SpreadsheetProperties sheetProperties = new SpreadsheetProperties();
            sheetProperties.setTitle(title);

            sheetToCreate.setProperties(sheetProperties);

            final Spreadsheet result = requestBody("direct://CREATE", sheetToCreate);

            assertNotNull(result, "create result is null");
            assertEquals(title, result.getProperties().getTitle());

            LOG.debug("create: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse().setContent("{\"properties\":{\"title\":\"" + title + "\"}}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://CREATE")
                            .to("google-sheets://" + PATH_PREFIX + "/create?inBody=content");
                }
            };
        }
    }

    public static class GetTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();

        @Test
        public void test() throws Exception {
            final Spreadsheet result = requestBody("direct://GET", testSheet.getSpreadsheetId());

            assertNotNull(result, "get result is null");
            assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());

            LOG.debug("get: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(new MockLowLevelHttpResponse().setContent(testSheet.toPrettyString()));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://GET")
                            .to("google-sheets://" + PATH_PREFIX + "/get?inBody=spreadsheetId");
                }
            };
        }
    }

    public static class BatchUpdateTest extends AbstractGoogleSheetsTestSupport {
        private Spreadsheet testSheet = getSpreadsheet();
        private String updateTitle = "updated-" + testSheet.getProperties().getTitle();

        @Test
        public void test() {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
            // parameter type is com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
            headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "batchUpdateSpreadsheetRequest",
                    new BatchUpdateSpreadsheetRequest()
                            .setIncludeSpreadsheetInResponse(true)
                            .setRequests(Collections
                                    .singletonList(new Request()
                                            .setUpdateSpreadsheetProperties(new UpdateSpreadsheetPropertiesRequest()
                                                    .setProperties(new SpreadsheetProperties().setTitle(updateTitle))
                                                    .setFields("title")))));

            final BatchUpdateSpreadsheetResponse result = requestBodyAndHeaders("direct://BATCHUPDATE", null, headers);

            assertNotNull(result, "batchUpdate result is null");
            assertEquals(updateTitle, result.getUpdatedSpreadsheet().getProperties().getTitle());

            LOG.debug("batchUpdate: {}", result);
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent("{\"spreadsheetId\":\"" + testSheet.getSpreadsheetId()
                                        + "\",\"updatedSpreadsheet\":{\"properties\":{\"title\":\"" + updateTitle
                                        + "\"},\"spreadsheetId\":\"" + testSheet.getSpreadsheetId() + "\"}}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct://BATCHUPDATE")
                            .to("google-sheets://" + PATH_PREFIX + "/batchUpdate");
                }
            };
        }
    }

}
