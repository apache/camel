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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AbstractReportResultsBase;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AggregateColumnInfo;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AsyncReportResults;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.DetailColumnInfo;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.GroupingColumnInfo;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.GroupingInfo;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.GroupingValue;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportExtendedMetadata;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportFactWithDetails;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportRow;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportStatusEnum;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.SummaryValue;

/**
 * Salesforce report results to
 * <code>List&lt;List&lt;String&gt;&gt;</code>converter.
 */
@Converter(generateLoader = true)
public final class SalesforceReportResultsToListConverter {

    public static final String INCLUDE_DETAILS = "CamelSalesforceIncludeDetails";
    public static final String INCLUDE_HEADERS = "CamelSalesforceIncludeHeaders";
    public static final String INCLUDE_SUMMARY = "CamelSalesforceIncludeSummary";

    private static final String ROW_COUNT = "RowCount";
    private static final String EMPTY_VALUE = "";
    private static final List<String> EMPTY_STRING_LIST = Collections.emptyList();

    private SalesforceReportResultsToListConverter() {
    }

    @Converter
    public static List<List<String>> convertToList(final AbstractReportResultsBase reportResults, final Exchange exchange) {

        List<List<String>> results = null;
        if (reportResults instanceof AsyncReportResults) {
            AsyncReportResults asyncReportResults = (AsyncReportResults)reportResults;
            final ReportStatusEnum status = asyncReportResults.getAttributes().getStatus();
            // only successfully completed async report results have data rows
            if (status != ReportStatusEnum.Success) {
                throw new IllegalArgumentException("Invalid asynchronous report results status " + status);
            }
        }

        switch (reportResults.getReportMetadata().getReportFormat()) {
            case TABULAR:
                results = convertTabularResults(reportResults, exchange);
                break;
            case SUMMARY:
                results = convertSummaryResults(reportResults, exchange);
                break;
            case MATRIX:
                results = convertMatrixResults(reportResults, exchange);
                break;
            default:
                // ignore
        }

        return results;
    }

    private static List<List<String>> convertTabularResults(final AbstractReportResultsBase reportResults, final Exchange exchange) {

        final ArrayList<List<String>> result = new ArrayList<>();

        final ReportMetadata reportMetadata = reportResults.getReportMetadata();
        final String[] detailColumns = reportMetadata.getDetailColumns();

        final ReportExtendedMetadata reportExtendedMetadata = reportResults.getReportExtendedMetadata();
        final ReportFactWithDetails factWithDetails = reportResults.getFactMap().get("T!T");

        // include detail rows?
        final String[] aggregates = reportMetadata.getAggregates();
        if (reportResults.getHasDetailRows() && getOption(exchange, INCLUDE_DETAILS, Boolean.TRUE)) {

            final int rowLength = detailColumns.length;

            // include detail headers?
            if (getOption(exchange, INCLUDE_HEADERS, Boolean.TRUE)) {

                final List<String> headers = new ArrayList<>(rowLength);
                result.add(headers);

                addColumnHeaders(headers, reportExtendedMetadata.getDetailColumnInfo(), detailColumns);
            }

            final ReportRow[] reportRows = factWithDetails.getRows();
            result.ensureCapacity(result.size() + reportRows.length);
            for (ReportRow reportRow : reportRows) {

                final List<String> row = new ArrayList<>(rowLength);
                result.add(row);

                addRowValues(row, reportRow.getDataCells());
            }

            // include summary values?
            if (aggregates.length > 0 && getOption(exchange, INCLUDE_SUMMARY, Boolean.TRUE)) {

                addSummaryRows(result, detailColumns, null, aggregates, factWithDetails.getAggregates());
            }

        } else if (aggregates.length > 0) {

            final int rowLength = aggregates.length;

            // include summary headers?
            if (getOption(exchange, INCLUDE_HEADERS, Boolean.TRUE)) {

                final List<String> headers = new ArrayList<>(rowLength);
                result.add(headers);

                addColumnHeaders(headers, reportExtendedMetadata.getAggregateColumnInfo(), aggregates);
            }

            // add summary values
            final List<String> row = new ArrayList<>(rowLength);
            result.add(row);
            addRowValues(row, factWithDetails.getAggregates());
        }

        return result;
    }

    private static List<List<String>> convertSummaryResults(final AbstractReportResultsBase reportResults, Exchange exchange) {

        final ArrayList<List<String>> result = new ArrayList<>();

        final ReportMetadata reportMetadata = reportResults.getReportMetadata();
        final ReportExtendedMetadata reportExtendedMetadata = reportResults.getReportExtendedMetadata();
        final String[] aggregates = reportMetadata.getAggregates();

        final boolean includeDetails = reportResults.getHasDetailRows() && getOption(exchange, INCLUDE_DETAILS, Boolean.TRUE);
        final boolean includeSummary = aggregates.length > 0 && getOption(exchange, INCLUDE_SUMMARY, Boolean.TRUE);

        // column list, including grouping columns and details if required
        final ArrayList<DetailColumnInfo> columnInfos = new ArrayList<>();
        final String[] columnNames = getResultColumns(columnInfos, reportMetadata, reportExtendedMetadata, includeDetails, includeSummary);

        // include detail headers?
        if (getOption(exchange, INCLUDE_HEADERS, Boolean.TRUE)) {
            addColumnHeaders(result, columnInfos);
        }

        // process down groups
        for (GroupingValue groupingValue : reportResults.getGroupingsDown().getGroupings()) {
            addSummaryGroupValues(result, reportResults, columnNames, groupingValue, EMPTY_STRING_LIST, includeDetails, includeSummary);
        }

        // add grand total
        if (includeSummary) {

            final ReportFactWithDetails grandTotal = reportResults.getFactMap().get("T!T");

            addSummaryValues(result, includeDetails, columnNames, EMPTY_STRING_LIST, aggregates, grandTotal.getAggregates());
        }

        return result;
    }

    private static List<List<String>> convertMatrixResults(final AbstractReportResultsBase reportResults, Exchange exchange) {
        final ArrayList<List<String>> result = new ArrayList<>();

        final ReportMetadata reportMetadata = reportResults.getReportMetadata();
        final ReportExtendedMetadata reportExtendedMetadata = reportResults.getReportExtendedMetadata();
        final String[] aggregates = reportMetadata.getAggregates();

        final boolean includeDetails = reportResults.getHasDetailRows() && getOption(exchange, INCLUDE_DETAILS, Boolean.TRUE);
        final boolean includeSummary = aggregates.length > 0 && getOption(exchange, INCLUDE_SUMMARY, Boolean.TRUE);

        // column list, including grouping columns and details if required
        final ArrayList<DetailColumnInfo> columnInfos = new ArrayList<>();
        final String[] columnNames = getResultColumns(columnInfos, reportMetadata, reportExtendedMetadata, includeDetails, includeSummary);

        // include detail headers?
        if (getOption(exchange, INCLUDE_HEADERS, Boolean.TRUE)) {
            addColumnHeaders(result, columnInfos);
        }

        // process down groups
        final GroupingValue[] groupingsDown = reportResults.getGroupingsDown().getGroupings();
        for (GroupingValue groupingValue : groupingsDown) {
            addMatrixGroupValues(result, reportResults, columnNames, groupingValue, EMPTY_STRING_LIST, includeDetails, includeSummary, EMPTY_VALUE, true);
        }

        // add grand total
        if (includeSummary) {

            final Map<String, ReportFactWithDetails> factMap = reportResults.getFactMap();

            // first add summary for across groups
            final List<String> downGroupsPrefix = new ArrayList<>(Collections.nCopies(groupingsDown.length, EMPTY_VALUE));

            for (GroupingValue acrossGrouping : reportResults.getGroupingsAcross().getGroupings()) {
                addAcrossGroupSummaryValues(result, reportMetadata, includeDetails, columnNames, factMap, downGroupsPrefix, acrossGrouping);
            }

            final ReportFactWithDetails grandTotal = factMap.get("T!T");
            addSummaryValues(result, includeDetails, columnNames, EMPTY_STRING_LIST, reportResults.getReportMetadata().getAggregates(), grandTotal.getAggregates());
        }

        return result;
    }

    private static void addAcrossGroupSummaryValues(ArrayList<List<String>> result, ReportMetadata reportMetadata, boolean includeDetails, String[] columnNames,
                                                    Map<String, ReportFactWithDetails> factMap, List<String> downGroupsPrefix, GroupingValue acrossGrouping) {

        final List<String> newDownGroupsPrefix = new ArrayList<>(downGroupsPrefix);
        newDownGroupsPrefix.add(acrossGrouping.getLabel());

        addSummaryValues(result, includeDetails, columnNames, newDownGroupsPrefix, reportMetadata.getAggregates(), factMap.get("T!" + acrossGrouping.getKey()).getAggregates());

        // process across subgroups
        for (GroupingValue subGroup : acrossGrouping.getGroupings()) {
            addAcrossGroupSummaryValues(result, reportMetadata, includeDetails, columnNames, factMap, newDownGroupsPrefix, subGroup);
        }
    }

    private static void addMatrixGroupValues(ArrayList<List<String>> result, AbstractReportResultsBase reportResults, String[] columnNames, GroupingValue groupingValue,
                                             List<String> rowPrefix, boolean includeDetails, boolean includeSummary, String keyPrefix, boolean downGroup) {

        final String groupKey = groupingValue.getKey();
        final String newKeyPrefix = keyPrefix + groupKey;

        // group values prefix
        final List<String> newPrefix = new ArrayList<>(rowPrefix);
        newPrefix.add(groupingValue.getLabel());

        final GroupingValue[] groupings = groupingValue.getGroupings();
        // has subgroups?
        if (groupings.length > 0) {

            for (GroupingValue subGroup : groupings) {
                addMatrixGroupValues(result, reportResults, columnNames, subGroup, newPrefix, includeDetails, includeSummary, newKeyPrefix + "_", downGroup);
            }

            // process across groupings?
        } else if (downGroup) {

            for (GroupingValue acrossGroup : reportResults.getGroupingsAcross().getGroupings()) {
                addMatrixGroupValues(result, reportResults, columnNames, acrossGroup, newPrefix, includeDetails, includeSummary, newKeyPrefix + "!", false);
            }

            // add lowest level across group detail rows?
        } else if (includeDetails) {

            addDetailRows(result, newPrefix, reportResults.getFactMap().get(newKeyPrefix));

            // add group columns only at lowest across level?
        } else if (!includeSummary) {

            result.add(newPrefix);

        }

        // add summary values for down group or lowest level across group
        if (includeSummary) {

            final String summaryKey = getGroupTotalKey(keyPrefix, downGroup, groupKey);

            addSummaryValues(result, includeDetails, columnNames, newPrefix, reportResults.getReportMetadata().getAggregates(),
                    reportResults.getFactMap().get(summaryKey).getAggregates());
        }
    }

    private static String getGroupTotalKey(String keyPrefix, boolean downGroup, String key) {
        if (downGroup) {
            // keyPrefix has rows only
            return keyPrefix + key + "!T";
        } else {
            // keyPrefix is of the form r(_r)*!(c_)*
            return keyPrefix + key;
        }
    }

    private static void addSummaryGroupValues(ArrayList<List<String>> result, AbstractReportResultsBase reportResults, String[] columnNames, GroupingValue groupingValue,
                                              List<String> rowPrefix, boolean includeDetails, boolean includeSummary) {

        // get fact map at this level
        final ReportFactWithDetails factWithDetails = reportResults.getFactMap().get(groupingValue.getKey() + "!T");

        final List<String> newPrefix = new ArrayList<>(rowPrefix);
        newPrefix.add(groupingValue.getLabel());

        // more groups?
        final GroupingValue[] groupings = groupingValue.getGroupings();
        if (groupings.length > 0) {

            for (GroupingValue subGroup : groupings) {
                addSummaryGroupValues(result, reportResults, columnNames, subGroup, newPrefix, includeDetails, includeSummary);
            }

            // add lowest level group detail rows?
        } else if (includeDetails) {

            addDetailRows(result, newPrefix, factWithDetails);

            // add group columns only at lowest level?
        } else if (!includeSummary) {
            result.add(newPrefix);
        }

        if (includeSummary) {
            final SummaryValue[] summaryValues = factWithDetails.getAggregates();
            final String[] aggregates = reportResults.getReportMetadata().getAggregates();

            addSummaryValues(result, includeDetails, columnNames, newPrefix, aggregates, summaryValues);
        }
    }

    private static void addDetailRows(ArrayList<List<String>> result, List<String> newPrefix, ReportFactWithDetails factWithDetails) {
        final ReportRow[] rows = factWithDetails.getRows();
        result.ensureCapacity(result.size() + rows.length);
        for (ReportRow row : rows) {
            final ArrayList<String> rowValues = new ArrayList<>(newPrefix);
            addRowValues(rowValues, row.getDataCells());
            result.add(rowValues);
        }
    }

    private static void addSummaryValues(ArrayList<List<String>> result, boolean includeDetails, String[] columnNames, List<String> newPrefix, String[] aggregates,
                                         SummaryValue[] summaryValues) {
        // no summary values to add
        if (summaryValues.length == 0) {
            return;
        }

        if (includeDetails) {
            // add summary rows for this group
            addSummaryRows(result, columnNames, newPrefix, aggregates, summaryValues);
        } else {
            // add summary values as columns for this group
            final ArrayList<String> summaryRow = new ArrayList<>(newPrefix);
            // add remaining group values
            final int nGroups = columnNames.length - summaryValues.length;
            for (int i = summaryRow.size(); i < nGroups; i++) {
                summaryRow.add(EMPTY_VALUE);
            }
            addRowValues(summaryRow, summaryValues);
            result.add(summaryRow);
        }
    }

    private static void addSummaryRows(List<List<String>> result, String[] detailColumns, List<String> rowPrefix, String[] aggregateColumns, SummaryValue[] summaryValues) {

        final ArrayList<List<String>> rows = new ArrayList<>(summaryValues.length + 1);
        String rowCount = null;
        for (int i = 0; i < aggregateColumns.length; i++) {

            final String aggregate = aggregateColumns[i];

            final String valueLabel = summaryValues[i].getLabel();
            if (ROW_COUNT.equals(aggregate)) {
                rowCount = valueLabel;
            } else {

                final List<String> summaryRow = rowPrefix == null ? new ArrayList<>() : new ArrayList<>(rowPrefix);
                rows.add(summaryRow);

                // skip rowPrefix columns if not null
                for (int j = rowPrefix == null ? 0 : rowPrefix.size(); j < detailColumns.length; j++) {

                    final String columnName = detailColumns[j];
                    if (aggregate.endsWith("!" + columnName)) {
                        final StringBuilder valueBuilder = new StringBuilder();
                        if (aggregate.startsWith("a!")) {
                            valueBuilder.append("avg ");
                        } else if (aggregate.startsWith("mx!")) {
                            valueBuilder.append("max ");
                        } else if (aggregate.startsWith("m!")) {
                            valueBuilder.append("min ");
                        }
                        valueBuilder.append(valueLabel);
                        summaryRow.add(valueBuilder.toString());
                    } else {
                        summaryRow.add(EMPTY_VALUE);
                    }
                }
            }
        }

        // add a Grand Totals separator row
        final List<String> grandTotal = new ArrayList<>();
        result.add(grandTotal);

        if (rowCount != null) {
            grandTotal.add("Grand Totals (" + rowCount + " records)");
        } else {
            grandTotal.add("Grand Totals");
        }

        // add summary values rows
        result.addAll(rows);
    }

    private static String[] getResultColumns(List<DetailColumnInfo> result, ReportMetadata reportMetadata, ReportExtendedMetadata reportExtendedMetadata, boolean includeDetails,
                                             boolean includeSummary) {

        final List<String> columnNames = new ArrayList<>();

        // add grouping columns before detail columns
        final Map<String, GroupingColumnInfo> groupingColumnInfos = reportExtendedMetadata.getGroupingColumnInfo();
        for (GroupingInfo downGroup : reportMetadata.getGroupingsDown()) {
            final String name = downGroup.getName();
            columnNames.add(name);
            result.add(groupingColumnInfos.get(name));
        }
        for (GroupingInfo acrossGroup : reportMetadata.getGroupingsAcross()) {
            final String name = acrossGroup.getName();
            columnNames.add(name);
            result.add(groupingColumnInfos.get(name));
        }

        // include details?
        if (!includeDetails) {
            // include summary columns?
            if (includeSummary) {
                final Map<String, AggregateColumnInfo> aggregateColumnInfos = reportExtendedMetadata.getAggregateColumnInfo();
                for (String aggregateColumnName : reportMetadata.getAggregates()) {
                    columnNames.add(aggregateColumnName);
                    result.add(aggregateColumnInfos.get(aggregateColumnName));
                }
            }
        } else {
            // add detail columns
            final Map<String, DetailColumnInfo> detailColumnInfo = reportExtendedMetadata.getDetailColumnInfo();
            for (String columnName : reportMetadata.getDetailColumns()) {
                columnNames.add(columnName);
                result.add(detailColumnInfo.get(columnName));
            }
        }

        return columnNames.toArray(new String[columnNames.size()]);
    }

    private static void addColumnHeaders(List<String> headers, Map<String, ? extends DetailColumnInfo> columnInfos, String[] columns) {
        for (String columnName : columns) {
            headers.add(columnInfos.get(columnName).getLabel());
        }
    }

    private static void addColumnHeaders(List<List<String>> result, ArrayList<DetailColumnInfo> columnInfos) {
        final ArrayList<String> headers = new ArrayList<>(columnInfos.size());
        for (DetailColumnInfo info : columnInfos) {
            headers.add(info.getLabel());
        }
        result.add(headers);
    }

    private static void addRowValues(List<String> row, SummaryValue[] values) {
        for (SummaryValue summaryValue : values) {
            row.add(summaryValue.getLabel());
        }
    }

    private static boolean getOption(Exchange exchange, String name, Boolean defaultValue) {
        return exchange.getIn().getHeader(name, defaultValue, Boolean.class);
    }
}
