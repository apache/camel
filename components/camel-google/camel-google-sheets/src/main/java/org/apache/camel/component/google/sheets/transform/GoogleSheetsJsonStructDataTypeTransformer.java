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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.component.google.sheets.internal.GoogleSheetsConstants;
import org.apache.camel.component.google.sheets.stream.GoogleSheetsStreamConstants;
import org.apache.camel.component.jackson.transform.Json;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Data type supports generic JsonNode representation of Google Sheets row and column values. Transforms generic
 * JsonNode struct to/from a Google Sheets ValueRange object. Supports both inbound and outbound transformation
 * depending on the given message body content. When Google Sheets ValueRange object is given as message body (e.g. as a
 * result of a get values operation) the transformer will transform into generic Json struct. When generic Json struct
 * is given as a message body transformer will transform into a proper ValueRange object that is ready to be used in an
 * update/append values operation. The Implementation also supports splitResults setting where a set of values is split
 * into its individual items.
 */
@DataTypeTransformer(name = "google-sheets:application-x-struct",
                     description = "Transforms to/from JSon data and Google Sheets ValueRange object")
public class GoogleSheetsJsonStructDataTypeTransformer extends Transformer {

    private static final String ROW_PREFIX = "#";

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        final Optional<ValueRange> valueRange = getValueRangeBody(message);

        String range = message.getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", "A:A").toString();
        String majorDimension = message
                .getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension", RangeCoordinate.DIMENSION_ROWS).toString();
        String spreadsheetId = message.getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", "").toString();
        String[] columnNames
                = message.getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "columnNames", "A").toString().split(",");

        boolean splitResults = Boolean
                .parseBoolean(message.getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "splitResults", "false").toString());

        if (valueRange.isPresent()) {
            message.setBody(
                    transformFromValueRangeModel(message, valueRange.get(), spreadsheetId, range, majorDimension, columnNames));
        } else if (splitResults) {
            message.setBody(transformFromSplitValuesModel(message, spreadsheetId, range, majorDimension, columnNames));
        } else {
            String valueInputOption
                    = message.getHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption", "USER_ENTERED").toString();
            message.setBody(
                    transformToValueRangeModel(message, spreadsheetId, range, majorDimension, valueInputOption, columnNames));
        }
    }

    /**
     * Constructs proper ValueRange object from given generic Json struct.
     */
    private ValueRange transformToValueRangeModel(
            Message message, String spreadsheetId, String range, String majorDimension, String valueInputOption,
            String[] columnNames) {
        try {
            List<String> jsonBeans = bodyAsJsonBeans(message);

            ValueRange valueRange = new ValueRange();
            List<List<Object>> values = new ArrayList<>();

            if (ObjectHelper.isNotEmpty(jsonBeans)) {
                final ArrayList<String> properties = createCoordinateNameSpec(range, majorDimension, columnNames);

                for (String json : jsonBeans) {
                    Map<String, Object> dataShape = Json.mapper().reader().forType(Map.class).readValue(json);

                    if (dataShape.containsKey("spreadsheetId")) {
                        spreadsheetId = Optional.ofNullable(dataShape.remove("spreadsheetId"))
                                .map(Object::toString)
                                .orElse(spreadsheetId);
                    }

                    List<Object> rangeValues = new ArrayList<>();
                    properties
                            .stream()
                            .filter(specEntry -> !Objects.equals("spreadsheetId", specEntry))
                            .forEach(specEntry -> rangeValues.add(dataShape.getOrDefault(specEntry, null)));

                    values.add(rangeValues);
                }
            }

            valueRange.setMajorDimension(majorDimension);
            valueRange.setValues(values);

            message.setHeader(GoogleSheetsStreamConstants.SPREADSHEET_ID, spreadsheetId);
            message.setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "spreadsheetId", spreadsheetId);
            message.setHeader(GoogleSheetsStreamConstants.RANGE, range);
            message.setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "range", range);
            message.setHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION, majorDimension);
            message.setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "majorDimension", majorDimension);
            message.setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "valueInputOption", valueInputOption);
            message.setHeader(GoogleSheetsConstants.PROPERTY_PREFIX + "values", valueRange);

            return valueRange;
        } catch (InvalidPayloadException | JsonProcessingException e) {
            throw new CamelExecutionException(
                    "Failed to apply Google Sheets Json struct data type on exchange",
                    message.getExchange(), e);
        }
    }

    /**
     * Construct generic Json struct from given ValueRange object. Json struct represents the row and column values
     * only.
     */
    private List<String> transformFromValueRangeModel(
            Message message, ValueRange valueRange, String spreadsheetId, String range, String majorDimension,
            String[] columnNames) {
        final List<String> jsonBeans = new ArrayList<>();

        try {
            if (valueRange != null) {
                if (ObjectHelper.isNotEmpty(valueRange.getRange())) {
                    range = valueRange.getRange();
                }
                RangeCoordinate rangeCoordinate = RangeCoordinate.fromRange(range);

                if (ObjectHelper.isNotEmpty(valueRange.getMajorDimension())) {
                    majorDimension = valueRange.getMajorDimension();
                }

                if (ObjectHelper.equal(RangeCoordinate.DIMENSION_ROWS, majorDimension)) {
                    for (List<Object> values : valueRange.getValues()) {
                        final Map<String, Object> model = new HashMap<>();
                        model.put("spreadsheetId", spreadsheetId);
                        int columnIndex = rangeCoordinate.getColumnStartIndex();
                        for (Object value : values) {
                            model.put(CellCoordinate.getColumnName(columnIndex, rangeCoordinate.getColumnStartIndex(),
                                    columnNames), value);
                            columnIndex++;
                        }
                        jsonBeans.add(Json.mapper().writer().writeValueAsString(model));
                    }
                } else if (ObjectHelper.equal(RangeCoordinate.DIMENSION_COLUMNS, majorDimension)) {
                    for (List<Object> values : valueRange.getValues()) {
                        final Map<String, Object> model = new HashMap<>();
                        model.put("spreadsheetId", spreadsheetId);
                        int rowIndex = rangeCoordinate.getRowStartIndex() + 1;
                        for (Object value : values) {
                            model.put(ROW_PREFIX + rowIndex, value);
                            rowIndex++;
                        }
                        jsonBeans.add(Json.mapper().writer().writeValueAsString(model));
                    }
                }
            }

        } catch (IOException e) {
            throw new CamelExecutionException(
                    "Failed to apply Google Sheets Json struct data type on exchange",
                    message.getExchange(), e);
        }

        return jsonBeans;
    }

    /**
     * Construct generic Json struct from given split values model. Json struct represents the row and column values
     * only. In split mode one single row/column is handled as an individual result.
     */
    private String transformFromSplitValuesModel(
            Message message, String spreadsheetId, String range, String majorDimension, String[] columnNames) {
        try {
            final List<?> values = bodyAsJsonBeans(message);

            final Map<String, Object> model = new HashMap<>();
            model.put("spreadsheetId", spreadsheetId);

            if (values != null) {
                if (ObjectHelper.isNotEmpty(message.getHeader(GoogleSheetsStreamConstants.RANGE))) {
                    range = message.getHeader(GoogleSheetsStreamConstants.RANGE).toString();
                }
                RangeCoordinate rangeCoordinate = RangeCoordinate.fromRange(range);

                if (ObjectHelper.isNotEmpty(message.getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION))) {
                    majorDimension = message.getHeader(GoogleSheetsStreamConstants.MAJOR_DIMENSION).toString();
                }

                if (ObjectHelper.equal(RangeCoordinate.DIMENSION_ROWS, majorDimension)) {
                    int columnIndex = rangeCoordinate.getColumnStartIndex();
                    for (Object value : values) {
                        model.put(CellCoordinate.getColumnName(columnIndex, rangeCoordinate.getColumnStartIndex(), columnNames),
                                value);
                        columnIndex++;
                    }
                } else if (ObjectHelper.equal(RangeCoordinate.DIMENSION_COLUMNS, majorDimension)) {
                    int rowIndex = rangeCoordinate.getRowStartIndex() + 1;
                    for (Object value : values) {
                        model.put(ROW_PREFIX + rowIndex, value);
                        rowIndex++;
                    }
                }
            }

            return Json.mapper().writer().writeValueAsString(model);
        } catch (InvalidPayloadException | JsonProcessingException e) {
            throw new CamelExecutionException(
                    "Failed to apply Google Sheets Json struct data type on exchange",
                    message.getExchange(), e);
        }
    }

    /**
     * Try to convert message body to a ValueRange object if possible. Returns empty optional when message body
     * conversion is not applicable.
     */
    private static Optional<ValueRange> getValueRangeBody(Message message) {
        if (message.getBody() instanceof ValueRange) {
            return Optional.of(message.getBody(ValueRange.class));
        }

        String jsonBody = MessageHelper.extractBodyAsString(message);
        if (jsonBody != null) {
            try {
                ValueRange valueRange = Json.mapper().reader().readValue(jsonBody, ValueRange.class);
                return valueRange.getValues() != null ? Optional.of(valueRange) : Optional.empty();
            } catch (IOException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Converts message body to list of Json objects. Supports different message body types such as List, String,
     * InputStream.
     */
    private static List<String> bodyAsJsonBeans(Message message) throws JsonProcessingException, InvalidPayloadException {
        if (message.getBody() == null) {
            return Collections.emptyList();
        }

        if (message.getBody() instanceof List) {
            return message.getBody(List.class);
        }

        String body = message.getMandatoryBody(String.class);
        if (Json.isJsonArray(body)) {
            return Json.arrayToJsonBeans(Json.mapper().reader().readTree(body));
        } else if (Json.isJson(body)) {
            return Collections.singletonList(body);
        }

        return Collections.emptyList();
    }

    /**
     * Construct row and column coordinate names for given range. Supports mapping of custom column names to proper
     * row/column coordinates.
     */
    public static ArrayList<String> createCoordinateNameSpec(String range, String majorDimension, String... columnNames) {
        ArrayList<String> names = new ArrayList<>();

        RangeCoordinate coordinate = RangeCoordinate.fromRange(range);
        if (ObjectHelper.equal(RangeCoordinate.DIMENSION_ROWS, majorDimension)) {
            createSchemaFromRowDimension(names, coordinate, columnNames);
        } else if (ObjectHelper.equal(RangeCoordinate.DIMENSION_COLUMNS, majorDimension)) {
            createSchemaFromColumnDimension(names, coordinate);
        }

        return names;
    }

    /**
     * Create dynamic json schema from row dimension. If split only a single object "ROW" holding 1-n column values is
     * created. Otherwise, each row results in a separate object with 1-n column values as property.
     */
    private static void createSchemaFromRowDimension(
            ArrayList<String> properties, RangeCoordinate coordinate, String... columnNames) {
        for (int i = coordinate.getColumnStartIndex(); i < coordinate.getColumnEndIndex(); i++) {
            properties.add(CellCoordinate.getColumnName(i, coordinate.getColumnStartIndex(), columnNames));
        }
    }

    /**
     * Create dynamic json schema from column dimension. If split only a single object "COLUMN" holding 1-n row values
     * is created. Otherwise, each column results in a separate object with 1-n row values as property.
     */
    private static void createSchemaFromColumnDimension(ArrayList<String> properties, RangeCoordinate coordinate) {
        for (int i = coordinate.getRowStartIndex() + 1; i <= coordinate.getRowEndIndex(); i++) {
            properties.add("#" + i);
        }
    }
}
