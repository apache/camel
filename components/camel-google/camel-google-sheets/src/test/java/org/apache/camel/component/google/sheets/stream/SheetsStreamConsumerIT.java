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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.sheets.GoogleSheetsClientFactory;
import org.apache.camel.component.google.sheets.MockGoogleSheetsClientFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.MAJOR_DIMENSION;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.RANGE;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.RANGE_INDEX;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.SPREADSHEET_ID;
import static org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants.VALUE_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SheetsStreamConsumerIT {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setDefaultPropertyInclusion(
                    JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
            .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING).disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
    private static final List<List<Object>> TEST_DATA = Arrays.asList(
            Arrays.asList("a1", "b1"),
            Arrays.asList("a2", "b2"));

    @Nested
    class ConsumeValueRangeIT extends AbstractGoogleSheetsStreamTestSupport {
        Spreadsheet testSheet = getSpreadsheet();

        @Test
        public void test() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(1);
            MockEndpoint.assertIsSatisfied(context);

            Exchange exchange = mock.getReceivedExchanges().get(0);
            assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
            assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
            assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
            assertEquals(TEST_RANGE, exchange.getIn().getHeaders().get(RANGE));
            assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
            assertEquals("ROWS", exchange.getIn().getHeaders().get(MAJOR_DIMENSION));

            ValueRange result = (ValueRange) exchange.getIn().getBody();

            assertEquals(2L, result.getValues().size());
            assertEquals("a1", result.getValues().get(0).get(0));
            assertEquals("b1", result.getValues().get(0).get(1));
            assertEquals("a2", result.getValues().get(1).get(0));
            assertEquals("b2", result.getValues().get(1).get(1));
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent(
                                    "{\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\"," + "\"valueRanges\": [" + "{"
                                        + "\"range\": \"" + TEST_RANGE + "\"," + "\"majorDimension\": \"ROWS\","
                                        + "\"values\":" + MAPPER.writer().writeValueAsString(TEST_DATA) + "}" + "]}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from(String.format("google-sheets-stream:%s?range=%s&delay=20000&maxResults=5&splitResults=%s",
                            testSheet.getSpreadsheetId(), TEST_RANGE, false))
                            .to("mock:result");
                }
            };
        }
    }

    @Nested
    class ConsumeValueRangeSplitResultsIT extends AbstractGoogleSheetsStreamTestSupport {
        Spreadsheet testSheet = getSpreadsheet();

        @Test
        public void test() throws Exception {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.expectedMinimumMessageCount(2);
            MockEndpoint.assertIsSatisfied(context);

            Exchange exchange = mock.getReceivedExchanges().get(0);
            assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
            assertTrue(exchange.getIn().getHeaders().containsKey(VALUE_INDEX));
            assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
            assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
            assertEquals(TEST_RANGE, exchange.getIn().getHeaders().get(RANGE));
            assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
            assertEquals(1, exchange.getIn().getHeaders().get(VALUE_INDEX));
            assertEquals("ROWS", exchange.getIn().getHeaders().get(MAJOR_DIMENSION));

            List<?> values = (List) exchange.getIn().getBody();
            assertEquals(2L, values.size());
            assertEquals("a1", values.get(0));
            assertEquals("b1", values.get(1));

            exchange = mock.getReceivedExchanges().get(1);
            assertTrue(exchange.getIn().getHeaders().containsKey(SPREADSHEET_ID));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE));
            assertTrue(exchange.getIn().getHeaders().containsKey(RANGE_INDEX));
            assertTrue(exchange.getIn().getHeaders().containsKey(VALUE_INDEX));
            assertTrue(exchange.getIn().getHeaders().containsKey(MAJOR_DIMENSION));
            assertEquals(testSheet.getSpreadsheetId(), exchange.getIn().getHeaders().get(SPREADSHEET_ID));
            assertEquals(1, exchange.getIn().getHeaders().get(RANGE_INDEX));
            assertEquals(2, exchange.getIn().getHeaders().get(VALUE_INDEX));

            values = (List) exchange.getIn().getBody();
            assertEquals(2L, values.size());
            assertEquals("a2", values.get(0));
            assertEquals("b2", values.get(1));
        }

        @Override
        protected GoogleSheetsClientFactory getClientFactory() throws Exception {
            return new MockGoogleSheetsClientFactory(
                    new MockLowLevelHttpResponse()
                            .setContent(
                                    "{\"spreadsheetId\": \"" + testSheet.getSpreadsheetId() + "\"," + "\"valueRanges\": [" + "{"
                                        + "\"range\": \"" + TEST_RANGE + "\"," + "\"majorDimension\": \"ROWS\","
                                        + "\"values\":" + MAPPER.writer().writeValueAsString(TEST_DATA) + "}" + "]}"));
        }

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from(String.format("google-sheets-stream:%s?range=%s&delay=20000&maxResults=5&splitResults=%s",
                            testSheet.getSpreadsheetId(), TEST_RANGE, true))
                            .to("mock:result");
                }
            };
        }
    }
}
