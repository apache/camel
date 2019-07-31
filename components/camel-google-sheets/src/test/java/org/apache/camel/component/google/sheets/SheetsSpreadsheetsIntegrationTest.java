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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.google.sheets.server.GoogleSheetsApiTestServerAssert.assertThatGoogleApi;

/**
 * Test class for {@link com.google.api.services.sheets.v4.Sheets.Spreadsheets} APIs.
 */
public class SheetsSpreadsheetsIntegrationTest extends AbstractGoogleSheetsTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SheetsSpreadsheetsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleSheetsApiCollection.getCollection().getApiName(SheetsSpreadsheetsApiMethod.class).getName();

    @Test
    public void testCreate() throws Exception {
        String title = "camel-sheets-" + new Random().nextInt(Integer.MAX_VALUE);
        Spreadsheet sheetToCreate = new Spreadsheet();
        SpreadsheetProperties sheetProperties = new SpreadsheetProperties();
        sheetProperties.setTitle(title);

        sheetToCreate.setProperties(sheetProperties);

        assertThatGoogleApi(getGoogleApiTestServer())
                .createSpreadsheetRequest()
                .hasTitle(title)
                .andReturnRandomSpreadsheet();

        final Spreadsheet result = requestBody("direct://CREATE", sheetToCreate);

        assertNotNull("create result is null", result);
        assertEquals(title, result.getProperties().getTitle());

        LOG.debug("create: " + result);
    }

    @Test
    public void testGet() throws Exception {
        assertThatGoogleApi(getGoogleApiTestServer())
                .createSpreadsheetRequest()
                .hasSheetTitle("TestData")
                .andReturnRandomSpreadsheet();

        Spreadsheet testSheet = getSpreadsheet();

        assertThatGoogleApi(getGoogleApiTestServer())
                .getSpreadsheetRequest(testSheet.getSpreadsheetId())
                .andReturnSpreadsheet(testSheet);

        // using String message body for single parameter "spreadsheetId"
        final Spreadsheet result = requestBody("direct://GET", testSheet.getSpreadsheetId());

        assertNotNull("get result is null", result);
        assertEquals(testSheet.getSpreadsheetId(), result.getSpreadsheetId());

        LOG.debug("get: " + result);
    }

    @Test
    public void testBatchUpdate() throws Exception {
        assertThatGoogleApi(getGoogleApiTestServer())
                .createSpreadsheetRequest()
                .hasSheetTitle("TestData")
                .andReturnRandomSpreadsheet();

        Spreadsheet testSheet = getSpreadsheet();
        String updateTitle = "updated-" + testSheet.getProperties().getTitle();

        assertThatGoogleApi(getGoogleApiTestServer())
                .batchUpdateSpreadsheetRequest(testSheet.getSpreadsheetId())
                .updateTitle(updateTitle)
                .andReturnUpdated();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", testSheet.getSpreadsheetId());
        // parameter type is com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
        headers.put(GoogleSheetsConstants.PROPERTY_PREFIX + "batchUpdateSpreadsheetRequest", new BatchUpdateSpreadsheetRequest()
                                                                            .setIncludeSpreadsheetInResponse(true)
                                                                            .setRequests(Collections.singletonList(new Request().setUpdateSpreadsheetProperties(new UpdateSpreadsheetPropertiesRequest()
                                                                                    .setProperties(new SpreadsheetProperties().setTitle(updateTitle))
                                                                                    .setFields("title")))));

        final BatchUpdateSpreadsheetResponse result = requestBodyAndHeaders("direct://BATCHUPDATE", null, headers);

        assertNotNull("batchUpdate result is null", result);
        assertEquals(updateTitle, result.getUpdatedSpreadsheet().getProperties().getTitle());

        LOG.debug("batchUpdate: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for batchUpdate
                from("direct://BATCHUPDATE")
                        .to("google-sheets://" + PATH_PREFIX + "/batchUpdate");

                // test route for create
                from("direct://CREATE")
                        .to("google-sheets://" + PATH_PREFIX + "/create?inBody=content");

                // test route for get
                from("direct://GET")
                        .to("google-sheets://" + PATH_PREFIX + "/get?inBody=spreadsheetId");

            }
        };
    }
}
