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
package org.apache.camel.component.google.sheets.transform;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.Exchange;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class GoogleSheetsJsonStructDataTypeTransformerTest {

    private final GoogleSheetsJsonStructDataTypeTransformer transformer = new GoogleSheetsJsonStructDataTypeTransformer();
    private DefaultCamelContext camelContext;

    private String spreadsheetId;

    @BeforeEach
    void setup() {
        this.camelContext = new DefaultCamelContext();
        this.spreadsheetId = UUID.randomUUID().toString();
    }

    public static Stream<Arguments> transformFromSplitValuesData() {
        return Stream.of(
                Arguments.of("A1", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "A", Collections.singletonList("a1"),
                        "{\"spreadsheetId\":\"%s\", \"A\":\"a1\"}"),
                Arguments.of("A1:A5", "Sheet1", RangeCoordinate.DIMENSION_COLUMNS, "A",
                        Arrays.asList("a1", "a2", "a3", "a4", "a5"),
                        "{\"spreadsheetId\":\"%s\", \"#1\":\"a1\",\"#2\":\"a2\",\"#3\":\"a3\",\"#4\":\"a4\",\"#5\":\"a5\"}"),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "A", Arrays.asList("a1", "b1"),
                        "{\"spreadsheetId\":\"%s\", \"A\":\"a1\",\"B\":\"b1\"}"),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "Foo,Bar", Arrays.asList("a1", "b1"),
                        "{\"spreadsheetId\":\"%s\", \"Foo\":\"a1\",\"Bar\":\"b1\"}"),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_COLUMNS, "A", Arrays.asList("a1", "a2"),
                        "{\"spreadsheetId\":\"%s\", \"#1\":\"a1\",\"#2\":\"a2\"}"));
    }

    public static Stream<Arguments> transformFromValueRangeData() {
        return Stream.of(
                Arguments.of("A1:A5", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "A",
                        Arrays.asList(Collections.singletonList("a1"),
                                Collections.singletonList("a2"),
                                Collections.singletonList("a3"),
                                Collections.singletonList("a4"),
                                Collections.singletonList("a5")),
                        Arrays.asList("{\"spreadsheetId\":\"%s\", \"A\":\"a1\"}",
                                "{\"spreadsheetId\":\"%s\", \"A\":\"a2\"}",
                                "{\"spreadsheetId\":\"%s\", \"A\":\"a3\"}",
                                "{\"spreadsheetId\":\"%s\", \"A\":\"a4\"}",
                                "{\"spreadsheetId\":\"%s\", \"A\":\"a5\"}")),
                Arguments.of("A1:A5", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "Foo",
                        Arrays.asList(Collections.singletonList("a1"),
                                Collections.singletonList("a2"),
                                Collections.singletonList("a3"),
                                Collections.singletonList("a4"),
                                Collections.singletonList("a5")),
                        Arrays.asList("{\"spreadsheetId\":\"%s\", \"Foo\":\"a1\"}",
                                "{\"spreadsheetId\":\"%s\", \"Foo\":\"a2\"}",
                                "{\"spreadsheetId\":\"%s\", \"Foo\":\"a3\"}",
                                "{\"spreadsheetId\":\"%s\", \"Foo\":\"a4\"}",
                                "{\"spreadsheetId\":\"%s\", \"Foo\":\"a5\"}")),
                Arguments.of("A1:A5", "Sheet1", RangeCoordinate.DIMENSION_COLUMNS, "A",
                        Collections.singletonList(Arrays.asList("a1", "a2", "a3", "a4", "a5")),
                        Collections.singletonList(
                                "{\"spreadsheetId\":\"%s\", \"#1\":\"a1\",\"#2\":\"a2\",\"#3\":\"a3\",\"#4\":\"a4\",\"#5\":\"a5\"}")),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "A",
                        Arrays.asList(Arrays.asList("a1", "b1"), Arrays.asList("a2", "b2")),
                        Arrays.asList("{\"spreadsheetId\":\"%s\", \"A\":\"a1\",\"B\":\"b1\"}",
                                "{\"spreadsheetId\":\"%s\", \"A\":\"a2\",\"B\":\"b2\"}")),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_ROWS, "Foo,Bar",
                        Arrays.asList(Arrays.asList("a1", "b1"), Arrays.asList("a2", "b2")),
                        Arrays.asList("{\"spreadsheetId\":\"%s\", \"Foo\":\"a1\",\"Bar\":\"b1\"}",
                                "{\"spreadsheetId\":\"%s\", \"Foo\":\"a2\",\"Bar\":\"b2\"}")),
                Arguments.of("A1:B2", "Sheet1", RangeCoordinate.DIMENSION_COLUMNS, "A",
                        Arrays.asList(Arrays.asList("a1", "a2"), Arrays.asList("b1", "b2")),
                        Arrays.asList("{\"spreadsheetId\":\"%s\", \"#1\":\"a1\",\"#2\":\"a2\"}",
                                "{\"spreadsheetId\":\"%s\", \"#1\":\"b1\",\"#2\":\"b2\"}")));
    }

    @ParameterizedTest
    @MethodSource("transformFromSplitValuesData")
    public void testTransformFromSplitValues(
            String range, String sheetName, String majorDimension, String columnNames,
            List<List<Object>> values, String expectedValueModel)
            throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);

        inbound.getMessage().setBody(values);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "splitResults", true);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", spreadsheetId);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", sheetName + "!" + range);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension", majorDimension);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "columnNames", columnNames);
        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        String model = inbound.getMessage().getBody(String.class);
        JSONAssert.assertEquals(String.format(expectedValueModel, spreadsheetId), model, JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @MethodSource("transformFromValueRangeData")
    public void testTransformFromValueRange(
            String range, String sheetName, String majorDimension, String columnNames,
            List<List<Object>> values, List<String> expectedValueModel)
            throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);

        ValueRange valueRange = new ValueRange();
        valueRange.setRange(sheetName + "!" + range);
        valueRange.setMajorDimension(majorDimension);
        valueRange.setValues(values);

        inbound.getMessage().setBody(valueRange);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", spreadsheetId);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "columnNames", columnNames);
        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        @SuppressWarnings("unchecked")
        List<String> model = inbound.getMessage().getBody(List.class);
        Assertions.assertEquals(expectedValueModel.size(), model.size());
        Iterator<String> modelIterator = model.iterator();
        for (String expected : expectedValueModel) {
            JSONAssert.assertEquals(String.format(expected, spreadsheetId), modelIterator.next(), JSONCompareMode.STRICT);
        }
    }

    @Test
    public void testTransformToEmptyValueRange() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", spreadsheetId);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1");
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption", "RAW");

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(spreadsheetId, inbound.getMessage().getHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID));
        Assertions.assertEquals("A1", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("RAW",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(0L, valueRange.getValues().size());
    }

    @Test
    public void testTransformToValueRangeRowDimension() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B1");

        String model = "{" +
                       "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                       "\"A\": \"a1\"," +
                       "\"B\": \"b1\"" +
                       "}";
        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(spreadsheetId, inbound.getMessage().getHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID));
        Assertions.assertEquals("A1:B1", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(1L, valueRange.getValues().size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("b1", valueRange.getValues().get(0).get(1));
    }

    @Test
    public void testTransformToValueRangeColumnNames() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B1");
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "columnNames", "Foo,Bar");

        String model = "{" +
                       "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                       "\"Foo\": \"a1\"," +
                       "\"Bar\": \"b1\"" +
                       "}";
        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(spreadsheetId, inbound.getMessage().getHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID));
        Assertions.assertEquals("A1:B1", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(1L, valueRange.getValues().size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("b1", valueRange.getValues().get(0).get(1));
    }

    @Test
    public void testTransformToValueRangeColumnDimension() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:A2");
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension",
                RangeCoordinate.DIMENSION_COLUMNS);

        String model = "{" +
                       "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                       "\"#1\": \"a1\"," +
                       "\"#2\": \"a2\"" +
                       "}";
        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals(spreadsheetId, inbound.getMessage().getHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID));
        Assertions.assertEquals("A1:A2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_COLUMNS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(1L, valueRange.getValues().size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("a2", valueRange.getValues().get(0).get(1));
    }

    @Test
    public void testTransformToValueRangeMultipleRows() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B2");

        List<String> model = Arrays.asList("{" +
                                           "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                           "\"A\": \"a1\"," +
                                           "\"B\": \"b1\"" +
                                           "}",
                "{" +
                                                "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                                "\"A\": \"a2\"," +
                                                "\"B\": \"b2\"" +
                                                "}");
        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:B2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(2L, valueRange.getValues().size());
        Assertions.assertEquals(2L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("b1", valueRange.getValues().get(0).get(1));
        Assertions.assertEquals(2L, valueRange.getValues().get(1).size());
        Assertions.assertEquals("a2", valueRange.getValues().get(1).get(0));
        Assertions.assertEquals("b2", valueRange.getValues().get(1).get(1));
    }

    @Test
    public void testTransformToValueRangeMultipleColumns() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B2");
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension",
                RangeCoordinate.DIMENSION_COLUMNS);

        List<String> model = Arrays.asList("{" +
                                           "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                           "\"#1\": \"a1\"," +
                                           "\"#2\": \"a2\"" +
                                           "}",
                "{" +
                                                "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                                "\"#1\": \"b1\"," +
                                                "\"#2\": \"b2\"" +
                                                "}");

        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:B2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_COLUMNS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(2L, valueRange.getValues().size());
        Assertions.assertEquals(2L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("a2", valueRange.getValues().get(0).get(1));
        Assertions.assertEquals(2L, valueRange.getValues().get(1).size());
        Assertions.assertEquals("b1", valueRange.getValues().get(1).get(0));
        Assertions.assertEquals("b2", valueRange.getValues().get(1).get(1));
    }

    @Test
    public void testTransformToValueRangeAutoFillColumnValues() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:C2");

        List<String> model = Arrays.asList("{" +
                                           "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                           "\"A\": \"a1\"," +
                                           "\"C\": \"c1\"" +
                                           "}",
                "{" +
                                                "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                                "\"A\": \"a2\"," +
                                                "\"B\": \"b2\"" +
                                                "}");

        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:C2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(2L, valueRange.getValues().size());
        Assertions.assertEquals(3L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertNull(valueRange.getValues().get(0).get(1));
        Assertions.assertEquals("c1", valueRange.getValues().get(0).get(2));
        Assertions.assertEquals(3L, valueRange.getValues().get(1).size());
        Assertions.assertEquals("a2", valueRange.getValues().get(1).get(0));
        Assertions.assertEquals("b2", valueRange.getValues().get(1).get(1));
        Assertions.assertNull(valueRange.getValues().get(1).get(2));
    }

    @Test
    public void testTransformToValueRangeAutoFillRowValues() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:C3");
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension",
                RangeCoordinate.DIMENSION_COLUMNS);

        List<String> model = Arrays.asList("{" +
                                           "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                           "\"#1\": \"a1\"," +
                                           "\"#3\": \"c1\"" +
                                           "}",
                "{" +
                                                "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                                                "\"#1\": \"a2\"," +
                                                "\"#2\": \"b2\"" +
                                                "}");

        inbound.getMessage().setBody(model);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:C3", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_COLUMNS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(2L, valueRange.getValues().size());
        Assertions.assertEquals(3L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertNull(valueRange.getValues().get(0).get(1));
        Assertions.assertEquals("c1", valueRange.getValues().get(0).get(2));
        Assertions.assertEquals(3L, valueRange.getValues().get(1).size());
        Assertions.assertEquals("a2", valueRange.getValues().get(1).get(0));
        Assertions.assertEquals("b2", valueRange.getValues().get(1).get(1));
        Assertions.assertNull(valueRange.getValues().get(1).get(2));
    }

    @Test
    public void testTransformToValueRangeWithJsonArray() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B2");

        String body = "[{" +
                      "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                      "\"A\": \"a1\"," +
                      "\"B\": \"b1\"" +
                      "}," +
                      "{" +
                      "\"spreadsheetId\": \"" + spreadsheetId + "\"," +
                      "\"A\": \"a2\"," +
                      "\"B\": \"b2\"" +
                      "}]";
        inbound.getMessage().setBody(body);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:B2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(2L, valueRange.getValues().size());
        Assertions.assertEquals(2L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("b1", valueRange.getValues().get(0).get(1));
        Assertions.assertEquals(2L, valueRange.getValues().get(1).size());
        Assertions.assertEquals("a2", valueRange.getValues().get(1).get(0));
        Assertions.assertEquals("b2", valueRange.getValues().get(1).get(1));
    }

    @Test
    public void testTransformToValueRangeWithJsonObject() throws Exception {
        Exchange inbound = new DefaultExchange(camelContext);
        inbound.getMessage().setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A1:B2");

        String body = "{\"spreadsheetId\": \"" + spreadsheetId + "\", \"A\": \"a1\", \"B\": \"b1\" }";
        inbound.getMessage().setBody(body);

        transformer.transform(inbound.getMessage(), DataType.ANY, DataType.ANY);

        Assertions.assertEquals("A1:B2", inbound.getMessage().getHeader(GoogleSheetsStreamConstants.RANGE));
        Assertions.assertEquals(RangeCoordinate.DIMENSION_ROWS,
                inbound.getMessage().getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION));
        Assertions.assertEquals("USER_ENTERED",
                inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption"));

        ValueRange valueRange = (ValueRange) inbound.getMessage().getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values");
        Assertions.assertEquals(1L, valueRange.getValues().size());
        Assertions.assertEquals(2L, valueRange.getValues().get(0).size());
        Assertions.assertEquals("a1", valueRange.getValues().get(0).get(0));
        Assertions.assertEquals("b1", valueRange.getValues().get(0).get(1));
    }
}
