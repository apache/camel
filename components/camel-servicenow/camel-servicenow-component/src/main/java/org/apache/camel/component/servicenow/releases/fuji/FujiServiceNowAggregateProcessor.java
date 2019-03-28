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
package org.apache.camel.component.servicenow.releases.fuji;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.ServiceNowConstants;
import org.apache.camel.component.servicenow.ServiceNowEndpoint;
import org.apache.camel.component.servicenow.ServiceNowParams;
import org.apache.camel.util.ObjectHelper;

class FujiServiceNowAggregateProcessor extends FujiServiceNowProcessor {

    FujiServiceNowAggregateProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, Class<?> requestModel, Class<?> responseModel, String action, String apiVersion, String tableName, String sysId) throws Exception {
        Response response;
        if (ObjectHelper.equal(ServiceNowConstants.ACTION_RETRIEVE, action, true)) {
            response = retrieveStats(exchange.getIn(), requestModel, responseModel, tableName);
        } else {
            throw new IllegalArgumentException("Unknown action " + action);
        }

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    private Response retrieveStats(Message in, Class<?> requestModel, Class<?> responseModel, String tableName) throws Exception {
        final String apiVersion = getApiVersion(in);

        return client.reset()
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
    }
}
