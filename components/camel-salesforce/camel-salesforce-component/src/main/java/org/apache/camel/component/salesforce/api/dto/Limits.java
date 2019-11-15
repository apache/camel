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
package org.apache.camel.component.salesforce.api.dto;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.camel.component.salesforce.api.TypeReferences;
import org.apache.camel.component.salesforce.api.dto.Limits.LimitsDeserializer;

/**
 * Data given by the `Limits` resource on Salesforce.
 *
 * @see <a href=
 *      "https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_limits.htm">
 *      https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_limits.htm</a>
 */
@JsonDeserialize(using = LimitsDeserializer.class)
public final class Limits implements Serializable {

    public static final class LimitsDeserializer extends JsonDeserializer {

        @Override
        public Object deserialize(final JsonParser parser, final DeserializationContext context) throws IOException, JsonProcessingException {

            final Map<String, Usage> usages = parser.readValueAs(TypeReferences.USAGES_TYPE);

            return new Limits(usages);
        }

    }

    public enum Operation {
        ConcurrentAsyncGetReportInstances, ConcurrentSyncReportRuns, DailyApiRequests, DailyAsyncApexExecutions, 
        DailyBulkApiRequests, DailyDurableGenericStreamingApiEvents, DailyDurableStreamingApiEvents, 
        DailyGenericStreamingApiEvents, DailyStreamingApiEvents, DailyWorkflowEmails, DataStorageMB, 
        DurableStreamingApiConcurrentClients, FileStorageMB, HourlyAsyncReportRuns, HourlyDashboardRefreshes, 
        HourlyDashboardResults, HourlyDashboardStatuses, HourlyODataCallout, HourlySyncReportRuns, 
        HourlyTimeBasedWorkflow, MassEmail, PermissionSets, SingleEmail, StreamingApiConcurrentClients
    }

    /**
     * Encapsulates usage limits for single operation.
     */
    public static final class Usage implements Serializable {

        private static final long serialVersionUID = 1L;

        private static final int UNKNOWN_VAL = Integer.MIN_VALUE;

        public static final Usage UNKNOWN = new Usage(UNKNOWN_VAL, UNKNOWN_VAL);

        private final int max;

        private final int remaining;

        private final Map<String, Usage> perApplication = new HashMap<>();

        @JsonCreator
        Usage(@JsonProperty("Max")
        final int max, @JsonProperty("Remaining")
        final int remaining) {
            this.max = max;
            this.remaining = remaining;
        }

        /** Returns {@link Usage} for application */
        public Optional<Usage> forApplication(final String application) {
            return Optional.ofNullable(perApplication.get(application));
        }

        /** Further per application usage. */
        public Set<String> getApplications() {
            return perApplication.keySet();
        }

        /** Maximum allowed by the limit */
        public int getMax() {
            return max;
        }

        /** Returns usages per application */
        public Map<String, Usage> getPerApplicationUsage() {
            return Collections.unmodifiableMap(perApplication);
        }

        /** Remaining invocations allowed */
        public int getRemaining() {
            return remaining;
        }

        public boolean isUnknown() {
            return max == UNKNOWN_VAL && remaining == UNKNOWN_VAL;
        }

        @Override
        public String toString() {
            if (max == UNKNOWN_VAL && remaining == UNKNOWN_VAL) {
                return "Undefined";
            }

            return "Max: " + max + ", Remaining: " + remaining + ", per application: " + perApplication;
        }

        @JsonAnySetter
        void addApplicationUsage(final String application, final Usage usage) {
            perApplication.put(application, usage);
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Usage UNDEFINED = new Usage(Usage.UNKNOWN_VAL, Usage.UNKNOWN_VAL);

    private final Map<String, Usage> usages;

    public Limits(final Map<?, Usage> usages) {
        if (usages == null) {
            this.usages = new HashMap<>();
        } else {
            this.usages = usages.entrySet().stream().collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Entry::getValue));
        }
    }

    public Usage forOperation(final Operation operation) {
        return usages.getOrDefault(operation, UNDEFINED);
    }

    public Usage forOperation(final String name) {
        return usages.getOrDefault(name, UNDEFINED);
    }

    /** Concurrent REST API requests for results of asynchronous report runs */
    public Usage getConcurrentAsyncGetReportInstances() {
        return forOperation(Operation.ConcurrentAsyncGetReportInstances.name());
    }

    /** Concurrent synchronous report runs via REST API */
    public Usage getConcurrentSyncReportRuns() {
        return forOperation(Operation.ConcurrentSyncReportRuns.name());
    }

    /** Daily API calls */
    public Usage getDailyApiRequests() {
        return forOperation(Operation.DailyApiRequests.name());
    }

    /** Daily Batch Apex and future method executions */
    public Usage getDailyAsyncApexExecutions() {
        return forOperation(Operation.DailyAsyncApexExecutions.name());
    }

    /** Daily Bulk API calls */
    public Usage getDailyBulkApiRequests() {
        return forOperation(Operation.DailyBulkApiRequests.name());
    }

    /**
     * Daily durable generic streaming events (if generic streaming is enabled
     * for your organization)
     */
    public Usage getDailyDurableGenericStreamingApiEvents() {
        return forOperation(Operation.DailyDurableGenericStreamingApiEvents.name());
    }

    /**
     * Daily durable streaming events (if generic streaming is enabled for your
     * organization)
     */
    public Usage getDailyDurableStreamingApiEvents() {
        return forOperation(Operation.DailyDurableStreamingApiEvents.name());
    }

    /**
     * Daily generic streaming events (if generic streaming is enabled for your
     * organization)
     */
    public Usage getDailyGenericStreamingApiEvents() {
        return forOperation(Operation.DailyGenericStreamingApiEvents.name());
    }

    /** Daily Streaming API events */
    public Usage getDailyStreamingApiEvents() {
        return forOperation(Operation.DailyStreamingApiEvents.name());
    }

    /** Daily workflow emails */
    public Usage getDailyWorkflowEmails() {
        return forOperation(Operation.DailyWorkflowEmails.name());
    }

    /** Data storage (MB) */
    public Usage getDataStorageMB() {
        return forOperation(Operation.DataStorageMB.name());
    }

    /** Streaming API concurrent clients */
    public Usage getDurableStreamingApiConcurrentClients() {
        return forOperation(Operation.DurableStreamingApiConcurrentClients.name());
    }

    /** File storage (MB) */
    public Usage getFileStorageMB() {
        return forOperation(Operation.FileStorageMB.name());
    }

    /** Hourly asynchronous report runs via REST API */
    public Usage getHourlyAsyncReportRuns() {
        return forOperation(Operation.HourlyAsyncReportRuns.name());
    }

    /** Hourly dashboard refreshes via REST API */
    public Usage getHourlyDashboardRefreshes() {
        return forOperation(Operation.HourlyDashboardRefreshes.name());
    }

    /** Hourly REST API requests for dashboard results */
    public Usage getHourlyDashboardResults() {
        return forOperation(Operation.HourlyDashboardResults.name());
    }

    /** Hourly dashboard status requests via REST API */
    public Usage getHourlyDashboardStatuses() {
        return forOperation(Operation.HourlyDashboardStatuses.name());
    }

    /** Hourly OData callouts */
    public Usage getHourlyODataCallout() {
        return forOperation(Operation.HourlyODataCallout.name());
    }

    /** Hourly synchronous report runs via REST API */
    public Usage getHourlySyncReportRuns() {
        return forOperation(Operation.HourlySyncReportRuns.name());
    }

    /** Hourly workflow time triggers */
    public Usage getHourlyTimeBasedWorkflow() {
        return forOperation(Operation.HourlyTimeBasedWorkflow.name());
    }

    /**
     * Daily number of mass emails that are sent to external email addresses by
     * using Apex or Force.com APIs
     */
    public Usage getMassEmail() {
        return forOperation(Operation.MassEmail.name());
    }

    /**
     * Usage of permission sets.
     */
    public Usage getPermissionSets() {
        return forOperation(Operation.PermissionSets.name());
    }

    /**
     * Daily number of single emails that are sent to external email addresses
     * by using Apex or Force.com APIs
     */
    public Usage getSingleEmail() {
        return forOperation(Operation.SingleEmail.name());
    }

    /** Durable streaming API concurrent clients */
    public Usage getStreamingApiConcurrentClients() {
        return forOperation(Operation.StreamingApiConcurrentClients.name());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Limits: " + usages.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining(", "));
    }
}
