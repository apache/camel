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
package org.apache.camel.component.salesforce;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.Limits;
import org.apache.camel.component.salesforce.api.dto.Limits.Usage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LimitsManualIT extends AbstractSalesforceTestBase {

    private static final Object NOT_USED = null;

    @Test
    public void shouldFetchLimitsForOrganization() {
        final Limits limits = template.requestBody("direct:test-limits", NOT_USED, Limits.class);

        assertNotNull(limits, "Should fetch limits from Salesforce REST API");

        assertLimitIsFetched("ConcurrentAsyncGetReportInstances", limits.getConcurrentAsyncGetReportInstances());

        assertLimitIsFetched("ConcurrentSyncReportRuns", limits.getConcurrentSyncReportRuns());

        assertLimitIsFetched("DailyApiRequests", limits.getDailyApiRequests());

        assertLimitIsFetched("DailyAsyncApexExecutions", limits.getDailyAsyncApexExecutions());

        assertLimitIsFetched("DailyBulkApiRequests", limits.getDailyBulkApiRequests());

        assertLimitIsFetched("DailyDurableGenericStreamingApiEvents", limits.getDailyDurableGenericStreamingApiEvents());

        assertLimitIsFetched("DailyDurableStreamingApiEvents", limits.getDailyDurableStreamingApiEvents());

        assertLimitIsFetched("DailyGenericStreamingApiEvents", limits.getDailyGenericStreamingApiEvents());

        assertLimitIsFetched("DailyStreamingApiEvents", limits.getDailyStreamingApiEvents());

        assertLimitIsFetched("DailyWorkflowEmails", limits.getDailyWorkflowEmails());

        assertLimitIsFetched("DataStorageMB", limits.getDataStorageMB());

        assertLimitIsFetched("DurableStreamingApiConcurrentClients", limits.getDurableStreamingApiConcurrentClients());

        assertLimitIsFetched("FileStorageMB", limits.getFileStorageMB());

        assertLimitIsFetched("HourlyAsyncReportRuns", limits.getHourlyAsyncReportRuns());

        assertLimitIsFetched("HourlyDashboardRefreshes", limits.getHourlyDashboardRefreshes());

        assertLimitIsFetched("HourlyDashboardResults", limits.getHourlyDashboardResults());

        assertLimitIsFetched("HourlyDashboardStatuses", limits.getHourlyDashboardStatuses());

        assertLimitIsFetched("HourlyODataCallout", limits.getHourlyODataCallout());

        assertLimitIsFetched("HourlySyncReportRuns", limits.getHourlySyncReportRuns());

        assertLimitIsFetched("HourlyTimeBasedWorkflow", limits.getHourlyTimeBasedWorkflow());

        assertLimitIsFetched("MassEmail", limits.getMassEmail());

        assertLimitIsFetched("SingleEmail", limits.getSingleEmail());

        assertLimitIsFetched("StreamingApiConcurrentClients", limits.getStreamingApiConcurrentClients());
    }

    private static void assertLimitIsFetched(String property, Usage usage) {
        assertNotNull(usage, "Usage for `" + property + "` should be defined");
        assertNotEquals(0, usage.getMax(), "Max usage for `" + property + "` should be defined");
        assertNotEquals(0, usage.getRemaining(), "Remaining usage for `" + property + "` should be defined");
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:test-limits").to("salesforce:limits");
            }
        };
    }
}
