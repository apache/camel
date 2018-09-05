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

class HelsinkiServiceNowAggregateProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowAggregateProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, this::retrieveStats);
    }

    /*
     * This method retrieves records for the specified table and performs aggregate
     * functions on the returned values.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /api/now/api/stats/{tableName}
     */
    private void retrieveStats(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> responseModel = getResponseModel(in, tableName);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("stats")
            .path(tableName)
            .query(ServiceNowParams.SYSPARM_QUERY, in)
            .query(ServiceNowParams.SYSPARM_AVG_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_COUNT, in)
            .query(ServiceNowParams.SYSPARM_MIN_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_QUERY, in)
            .query(ServiceNowParams.SYSPARM_MAX_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_SUM_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_GROUP_BY, in)
            .query(ServiceNowParams.SYSPARM_ORDER_BY, in)
            .query(ServiceNowParams.SYSPARM_HAVING, in)
            .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }
}
