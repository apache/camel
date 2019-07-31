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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;

import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;

/**
 * Report metadata DTO.
 */
public class ReportMetadata extends AbstractDTOBase {

    private String name;
    private String id;
    private String currency;
    private ReportFormatEnum reportFormat;
    private String developerName;
    private ReportType reportType;
    private String[] aggregates;
    private GroupingInfo[] groupingsDown;
    private GroupingInfo[] groupingsAcross;
    private String reportBooleanFilter;
    private ReportFilter[] reportFilters;
    private String[] detailColumns;
    private String[] historicalSnapshotDates;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public ReportFormatEnum getReportFormat() {
        return reportFormat;
    }

    public void setReportFormat(ReportFormatEnum reportFormat) {
        this.reportFormat = reportFormat;
    }

    public String getDeveloperName() {
        return developerName;
    }

    public void setDeveloperName(String developerName) {
        this.developerName = developerName;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public String[] getAggregates() {
        return aggregates;
    }

    public void setAggregates(String[] aggregates) {
        this.aggregates = aggregates;
    }

    public GroupingInfo[] getGroupingsDown() {
        return groupingsDown;
    }

    public void setGroupingsDown(GroupingInfo[] groupingsDown) {
        this.groupingsDown = groupingsDown;
    }

    public GroupingInfo[] getGroupingsAcross() {
        return groupingsAcross;
    }

    public void setGroupingsAcross(GroupingInfo[] groupingsAcross) {
        this.groupingsAcross = groupingsAcross;
    }

    public String getReportBooleanFilter() {
        return reportBooleanFilter;
    }

    public void setReportBooleanFilter(String reportBooleanFilter) {
        this.reportBooleanFilter = reportBooleanFilter;
    }

    public ReportFilter[] getReportFilters() {
        return reportFilters;
    }

    public void setReportFilters(ReportFilter[] reportFilters) {
        this.reportFilters = reportFilters;
    }

    public String[] getDetailColumns() {
        return detailColumns;
    }

    public void setDetailColumns(String[] detailColumns) {
        this.detailColumns = detailColumns;
    }

    public String[] getHistoricalSnapshotDates() {
        return historicalSnapshotDates;
    }

    public void setHistoricalSnapshotDates(String[] historicalSnapshotDates) {
        this.historicalSnapshotDates = historicalSnapshotDates;
    }
}
