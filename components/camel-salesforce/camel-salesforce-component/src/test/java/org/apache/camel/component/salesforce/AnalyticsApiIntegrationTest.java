/**
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
package org.apache.camel.component.salesforce;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.SalesforceReportResultsToListConverter;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.AsyncReportResults;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.QueryRecordsReport;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.Report;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportDescription;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportInstance;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportMetadata;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.ReportStatusEnum;
import org.apache.camel.component.salesforce.api.dto.analytics.reports.SyncReportResults;
import org.apache.camel.dataformat.csv.CsvDataFormat;
import org.apache.commons.csv.CSVFormat;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for Salesforce analytics API endpoints. 
 */
@RunWith(Theories.class)
public class AnalyticsApiIntegrationTest extends AbstractSalesforceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsApiIntegrationTest.class);
    private static final int RETRY_DELAY = 5000;
    private static final int REPORT_RESULT_RETRIES = 5;
    private static final String[] REPORT_OPTIONS = new String[]{
        SalesforceReportResultsToListConverter.INCLUDE_HEADERS,
        SalesforceReportResultsToListConverter.INCLUDE_DETAILS,
        SalesforceReportResultsToListConverter.INCLUDE_SUMMARY
    };
    private static final int NUM_OPTIONS = REPORT_OPTIONS.length;
    private static final int[] POWERS = new int[] {4, 2, 1};

    private static final String TEST_REPORT_NAME = "Test_Report";
    private boolean bodyMetadata;

    /**
     * Get test report developer names as data points.
     * @return test report developer names in test-salesforce-login.properties
     * @throws Exception
     */
    @DataPoints
    public static String[] getTestReportDeveloperNames() throws Exception {
        return new String[] {TEST_REPORT_NAME};
    }

    @Test
    public void testGetRecentReports() throws Exception {

        final List recentReports = template().requestBody("direct:getRecentReports", null, List.class);

        assertNotNull("getRecentReports", recentReports);
        assertFalse("getRecentReports empty", recentReports.isEmpty());
        LOG.debug("getRecentReports: {}", recentReports);
    }

    @Theory
    public void testReport(String reportName) throws Exception {

        log.info("Testing report {}...", reportName);

        // get Report Id
        final QueryRecordsReport reports = template().requestBody("direct:queryReport",
            "SELECT Id FROM Report WHERE DeveloperName='" + reportName + "'", QueryRecordsReport.class);

        assertNotNull("query", reports);
        final List<Report> reportsRecords = reports.getRecords();
        assertFalse("Report not found", reportsRecords.isEmpty());
        final String testReportId = reportsRecords.get(0).getId();
        assertNotNull(testReportId);

        // 1. getReportDescription
        final ReportDescription reportDescription = template().requestBody("direct:getReportDescription", testReportId,
            ReportDescription.class);

        assertNotNull("getReportDescriptions", reportDescription);
        LOG.debug("getReportDescriptions: {}", reportDescription);
        final ReportMetadata testReportMetadata = reportDescription.getReportMetadata();

        // 2. executeSyncReport
        // execute with no metadata
        SyncReportResults reportResults = template().requestBodyAndHeader("direct:executeSyncReport",
            testReportId, SalesforceEndpointConfig.INCLUDE_DETAILS, Boolean.TRUE, SyncReportResults.class);

        assertNotNull("executeSyncReport", reportResults);
        LOG.debug("executeSyncReport: {}", reportResults);

        // execute with metadata
        final Map<String, Object> headers = new HashMap<>();
        headers.put(SalesforceEndpointConfig.INCLUDE_DETAILS, Boolean.FALSE);
        Object body;
        if (!bodyMetadata) {
            headers.put(SalesforceEndpointConfig.REPORT_METADATA, testReportMetadata);
            body = testReportId;
        } else {
            body = testReportMetadata;
        }
        reportResults = template().requestBodyAndHeaders("direct:executeSyncReport",
            body, headers, SyncReportResults.class);

        assertNotNull("executeSyncReport with metadata", reportResults);
        LOG.debug("executeSyncReport with metadata: {}", reportResults);

        // 3. executeAsyncReport
        // execute with no metadata
        ReportInstance reportInstance = template().requestBodyAndHeader("direct:executeAsyncReport",
            testReportId, SalesforceEndpointConfig.INCLUDE_DETAILS, true, ReportInstance.class);

        assertNotNull("executeAsyncReport", reportInstance);
        LOG.debug("executeAsyncReport: {}", reportInstance);

        // execute with metadata
        headers.clear();
        headers.put(SalesforceEndpointConfig.INCLUDE_DETAILS, "true");
        if (!bodyMetadata) {
            headers.put(SalesforceEndpointConfig.REPORT_METADATA, testReportMetadata);
            body = testReportId;
            bodyMetadata = true;
        } else {
            body = testReportMetadata;
            bodyMetadata = false;
        }
        reportInstance = template().requestBodyAndHeaders("direct:executeAsyncReport",
            body, headers, ReportInstance.class);

        assertNotNull("executeAsyncReport with metadata", reportInstance);
        LOG.debug("executeAsyncReport with metadata: {}", reportInstance);
        final String testReportInstanceId = reportInstance.getId();

        // 4. getReportInstances
        final List reportInstances = template().requestBody("direct:getReportInstances", testReportId, List.class);

        assertNotNull("getReportInstances", reportInstances);
        assertFalse("getReportInstances empty", reportInstances.isEmpty());
        LOG.debug("getReportInstances: {}", reportInstances);

        // 5. getReportResults
        // wait for the report to complete
        boolean done = false;
        int tries = 0;
        AsyncReportResults asyncReportResults = null;
        while (!done) {
            asyncReportResults = template().requestBodyAndHeader("direct:getReportResults",
                testReportId, SalesforceEndpointConfig.INSTANCE_ID, testReportInstanceId, AsyncReportResults.class);
            done = asyncReportResults != null
                && (asyncReportResults.getAttributes().getStatus() == ReportStatusEnum.Success
                    || asyncReportResults.getAttributes().getStatus() == ReportStatusEnum.Error);
            if (!done) {
                // avoid flooding calls
                Thread.sleep(RETRY_DELAY);
                if (++tries > REPORT_RESULT_RETRIES) {
                    final long retrySeconds = TimeUnit.SECONDS.convert(tries * RETRY_DELAY, TimeUnit.MILLISECONDS);
                    fail("Async report result not available in " + retrySeconds + " seconds");
                }
            }
        }

        assertNotNull("getReportResults", asyncReportResults);
        assertEquals("getReportResults status", ReportStatusEnum.Success,
            asyncReportResults.getAttributes().getStatus());
        LOG.debug("getReportResults: {}", asyncReportResults);

        // 6. SalesforceReportResultsConverter tests
        // defaults
        String convertResults = template.requestBody("direct:convertResults", asyncReportResults, String.class);
        assertNotNull("default convertResults", convertResults);
        LOG.debug("Default options", convertResults);
        LOG.debug("{}", convertResults);

        // permutations of include details, include headers, include summary
        final boolean[] values = new boolean[NUM_OPTIONS];
        final int nIterations = (int) Math.pow(2, NUM_OPTIONS);

        for (int i = 0; i < nIterations; i++) {

            // toggle options
            for (int j = 0; j < NUM_OPTIONS; j++) {
                if (i % POWERS[j] == 0) {
                    values[j] = !values[j];
                }
            }

            log.debug("Options {} = {}", REPORT_OPTIONS, values);
            headers.clear();
            for (int j = 0; j < REPORT_OPTIONS.length; j++) {
                headers.put(REPORT_OPTIONS[j], values[j]);
            }
            convertResults = template.requestBodyAndHeaders("direct:convertResults", asyncReportResults,
                headers, String.class);

            assertNotNull("convertResults", convertResults);
            LOG.debug("{}", convertResults);
        }
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                // get Report SObject by DeveloperName
                from("direct:queryReport")
                    .to("salesforce:query?sObjectClass=" + QueryRecordsReport.class.getName());
                
                from("direct:getRecentReports")
                    .to("salesforce:getRecentReports");

                from("direct:getReportDescription")
                    .to("salesforce:getReportDescription");
                
                from("direct:executeSyncReport")
                    .to("salesforce:executeSyncReport");
                
                from("direct:executeAsyncReport")
                    .to("salesforce:executeAsyncReport?includeDetails=true");
                
                from("direct:getReportInstances")
                    .to("salesforce:getReportInstances");
                
                from("direct:getReportResults")
                    .to("salesforce:getReportResults");

                CsvDataFormat csv = new CsvDataFormat(CSVFormat.EXCEL);

                // type converter test
                from("direct:convertResults")
                    .convertBodyTo(List.class)
                    .marshal(csv);
            }
        };
    }
}
