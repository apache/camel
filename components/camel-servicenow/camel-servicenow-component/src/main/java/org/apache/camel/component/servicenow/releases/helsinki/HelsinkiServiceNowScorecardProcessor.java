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
package org.apache.camel.component.servicenow.releases.helsinki;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.AbstractServiceNowProcessor;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowParams;

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_PERFORMANCE_ANALYTICS;

class HelsinkiServiceNowScorecardProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowScorecardProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_PERFORMANCE_ANALYTICS, this::retrievePerformanceAnalytics);
    }

    /*
     * This method retrieves Performance Analytics scorecard details.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /api/now/pa/scorecards
     */
    private void retrievePerformanceAnalytics(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("pa")
            .path("scorecards")
            .query(ServiceNowParams.SYSPARM_UUID, in)
            .query(ServiceNowParams.SYSPARM_BREAKDOWN, in)
            .query(ServiceNowParams.SYSPARM_INCLUDE_SCORES, in)
            .query(ServiceNowParams.SYSPARM_INCLUDE_AGGREGATES, in)
            .query(ServiceNowParams.SYSPARM_INCLUDE_AVAILABLE_BREAKDOWNS, in)
            .query(ServiceNowParams.SYSPARM_INCLUDE_AVAILABLE_AGGREGATES, in)
            .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
            .query(ServiceNowParams.SYSPARM_FAVORITES, in)
            .query(ServiceNowParams.SYSPARM_KEY, in)
            .query(ServiceNowParams.SYSPARM_TARGET, in)
            .query(ServiceNowParams.SYSPARM_DISPLAY, in)
            .query(ServiceNowParams.SYSPARM_CONTAINS, in)
            .query(ServiceNowParams.SYSPARM_TAGS, in)
            .query(ServiceNowParams.SYSPARM_PER_PAGE, in)
            .query(ServiceNowParams.SYSPARM_PAGE, in)
            .query(ServiceNowParams.SYSPARM_SORT_BY, in)
            .query(ServiceNowParams.SYSPARM_SORT_DIR, in)
            .query(ServiceNowParams.SYSPARM_ELEMENTS_FILTER, in)
            .query(ServiceNowParams.SYSPARM_BREAKDOWN_RELATION, in)
            .query(ServiceNowParams.SYSPARM_INCLUDE_SCORE_NOTES, in)
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }
}
