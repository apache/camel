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
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_CREATE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_DELETE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_MODIFY;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_UPDATE;

class HelsinkiServiceNowTableProcessor extends AbstractServiceNowProcessor {
    HelsinkiServiceNowTableProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, this::retrieveRecord);
        addDispatcher(ACTION_CREATE, this::createRecord);
        addDispatcher(ACTION_MODIFY, this::modifyRecord);
        addDispatcher(ACTION_DELETE, this::deleteRecord);
        addDispatcher(ACTION_UPDATE, this::updateRecord);
    }

    /*
     * GET
     * https://instance.service-now.com/api/now/table/{tableName}
     * https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void retrieveRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        Response response = ObjectHelper.isEmpty(sysId)
            ? client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(apiVersion)
                .path("table")
                .path(tableName)
                .query(ServiceNowParams.SYSPARM_QUERY, in)
                .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
                .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
                .query(ServiceNowParams.SYSPARM_SUPPRESS_PAGINATION_HEADER, in)
                .query(ServiceNowParams.SYSPARM_FIELDS, in)
                .query(ServiceNowParams.SYSPARM_LIMIT, in)
                .query(ServiceNowParams.SYSPARM_OFFSET, in)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET)
            : client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("now")
                .path(apiVersion)
                .path("table")
                .path(tableName)
                .path(sysId)
                .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
                .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
                .query(ServiceNowParams.SYSPARM_FIELDS, in)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET);

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    /*
     * POST
     * https://instance.service-now.com/api/now/table/{tableName}
     */
    private void createRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> requestModel = getRequestModel(in, tableName);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        validateBody(in, requestModel);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("table")
            .path(tableName)
            .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
            .query(ServiceNowParams.SYSPARM_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_INPUT_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, in)
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    /*
     * PUT
     * https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void modifyRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> requestModel = getRequestModel(in, tableName);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        validateBody(in, requestModel);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("table")
            .path(tableName)
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
            .query(ServiceNowParams.SYSPARM_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_INPUT_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, in)
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke(HttpMethod.PUT, in.getMandatoryBody());

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    /*
     * DELETE
     * https://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void deleteRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("table")
            .path(tableName)
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .query(responseModel)
            .invoke(HttpMethod.DELETE, null);

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    /*
     * PATCH
     * instance://instance.service-now.com/api/now/table/{tableName}/{sys_id}
     */
    private void updateRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> requestModel = getRequestModel(in, tableName);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        validateBody(in, requestModel);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("table")
            .path(tableName)
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .query(ServiceNowParams.SYSPARM_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, in)
            .query(ServiceNowParams.SYSPARM_FIELDS, in)
            .query(ServiceNowParams.SYSPARM_INPUT_DISPLAY_VALUE, in)
            .query(ServiceNowParams.SYSPARM_SUPPRESS_AUTO_SYS_FIELD, in)
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke("PATCH", in.getMandatoryBody());

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }
}
