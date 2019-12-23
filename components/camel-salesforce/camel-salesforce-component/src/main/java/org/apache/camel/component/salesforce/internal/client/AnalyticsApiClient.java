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
package org.apache.camel.component.salesforce.internal.client;

import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AbstractReportResultsBase;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.RecentReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportDescription;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;

/**
 * Client interface for Analytics API.
 */
public interface AnalyticsApiClient {

    // Report operations

    public interface RecentReportsResponseCallback {
        void onResponse(List<RecentReport> reportDescription, Map<String, String> headers, SalesforceException ex);
    }

    public interface ReportDescriptionResponseCallback {
        void onResponse(ReportDescription reportDescription, Map<String, String> headers, SalesforceException ex);
    }

    public interface ReportResultsResponseCallback {
        void onResponse(AbstractReportResultsBase reportResults, Map<String, String> headers, SalesforceException ex);
    }

    public interface ReportInstanceResponseCallback {
        void onResponse(ReportInstance reportInstance, Map<String, String> headers, SalesforceException ex);
    }

    public interface ReportInstanceListResponseCallback {
        void onResponse(List<ReportInstance> reportInstances, Map<String, String> headers, SalesforceException ex);
    }

    void getRecentReports(Map<String, List<String>> headers, RecentReportsResponseCallback callback);

    void getReportDescription(String reportId, Map<String, List<String>> headers, ReportDescriptionResponseCallback callback);

    void executeSyncReport(String reportId, Boolean includeDetails, ReportMetadata reportFilter, Map<String, List<String>> headers, ReportResultsResponseCallback callback);

    void executeAsyncReport(String reportId, Boolean includeDetails, ReportMetadata reportFilter, Map<String, List<String>> headers, ReportInstanceResponseCallback callback);

    void getReportInstances(String reportId, Map<String, List<String>> headers, ReportInstanceListResponseCallback callback);

    void getReportResults(String reportId, String instanceId, Map<String, List<String>> headers, ReportResultsResponseCallback callback);
}
