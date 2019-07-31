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
package org.apache.camel.component.google.sheets.stream;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;

import static org.apache.camel.component.google.sheets.server.GoogleSheetsApiTestServerAssert.assertThatGoogleApi;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.MAJOR_DIMENSION;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.RANGE;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.RANGE_INDEX;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.SPREADSHEET_ID;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.VALUE_INDEX;

public class SheetsStreamConsumerIntegrationTest extends AbstractGoogleSheetsStreamTestSupport {

    private String range = TEST_SHEET + "!A1:B2";

    @Test
    public void testConsumeValueRange() throws Exception {
        String spreadsheetId = UUID.randomUUID().toString();

        assertThatGoogleApi(getGoogleApiTestServer())
                .createSpreadsheetRequest()
                .hasSheetTitle("TestData")
                .andReturnSpreadsheet(spreadsheetId);

        Spreadsheet testSheet = getSpreadsheet();

        List<List<Object>> data = Arrays.asList(
                Arrays.asList("a1", "b1"),
                Arrays.asList("a2", "b2")
        );

        assertThatGoogleApi(getGoogleApiTestServer())
                .updateValuesRequest(spreadsheetId, range, data)
                .andReturnUpdateResponse();

        applyTestData(testSheet);

        assertThatGoogleApi(getGoogleApiTestServer())
                .batchGetValuesRequest(testSheet.getSpreadsheetId(), range)
                .andReturnValues(data);

        context().addRoutes(createGoogleStreamRouteBuilder(testSheet.getSpreadsheetId(), false));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
        Assert.assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
        Assert.assertEquals(range, exchange.getIn().getHeaders().get(RANGE));
        Assert.assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
        Assert.assertEquals("ROWS", exchange.getIn().getHeaders().get(MAJOR_DIMENSION));

        ValueRange values = (ValueRange) exchange.getIn().getBody();
        Assert.assertEquals(2L, values.getValues().size());
        Assert.assertEquals("a1", values.getValues().get(0).get(0));
        Assert.assertEquals("b1", values.getValues().get(0).get(1));
        Assert.assertEquals("a2", values.getValues().get(1).get(0));
        Assert.assertEquals("b2", values.getValues().get(1).get(1));
    }

    @Test
    public void testConsumeValueRangeSplitResults() throws Exception {
        String spreadsheetId = UUID.randomUUID().toString();

        assertThatGoogleApi(getGoogleApiTestServer())
                .createSpreadsheetRequest()
                .hasSheetTitle("TestData")
                .andReturnSpreadsheet(spreadsheetId);

        Spreadsheet testSheet = getSpreadsheet();

        List<List<Object>> data = Arrays.asList(
                Arrays.asList("a1", "b1"),
                Arrays.asList("a2", "b2")
        );

        assertThatGoogleApi(getGoogleApiTestServer())
                .updateValuesRequest(spreadsheetId, range, data)
                .andReturnUpdateResponse();

        applyTestData(testSheet);

        assertThatGoogleApi(getGoogleApiTestServer())
                .batchGetValuesRequest(testSheet.getSpreadsheetId(), range)
                .andReturnValues(data);

        context().addRoutes(createGoogleStreamRouteBuilder(testSheet.getSpreadsheetId(), true));
        context().getRouteController().startRoute("google-stream-test");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);
        assertMockEndpointsSatisfied();

        Exchange exchange = mock.getReceivedExchanges().get(0);
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(VALUE_INDEX));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
        Assert.assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
        Assert.assertEquals(range, exchange.getIn().getHeaders().get(RANGE));
        Assert.assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
        Assert.assertEquals(1, exchange.getIn().getHeaders().get(VALUE_INDEX));
        Assert.assertEquals("ROWS", exchange.getIn().getHeaders().get(MAJOR_DIMENSION));

        List<?> values = (List) exchange.getIn().getBody();
        Assert.assertEquals(2L, values.size());
        Assert.assertEquals("a1", values.get(0));
        Assert.assertEquals("b1", values.get(1));

        exchange = mock.getReceivedExchanges().get(1);
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(VALUE_INDEX));
        Assert.assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
        Assert.assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
        Assert.assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
        Assert.assertEquals(2, exchange.getIn().getHeaders().get(VALUE_INDEX));

        values = (List) exchange.getIn().getBody();
        Assert.assertEquals(2L, values.size());
        Assert.assertEquals("a2", values.get(0));
        Assert.assertEquals("b2", values.get(1));
    }

    private RouteBuilder createGoogleStreamRouteBuilder(String spreadsheetId, boolean splitResults) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(String.format("google-sheets-stream://data?spreadsheetId=%s&range=%s&delay=20000&maxResults=5&splitResults=%s", spreadsheetId, range, splitResults))
                        .routeId("google-stream-test")
                        .to("mock:result");
            }
        };
    }
}
