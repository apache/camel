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
import org.apache.camel.util.ObjectHelper;

class FujiServiceNowImportSetProcessor extends FujiServiceNowProcessor {

    FujiServiceNowImportSetProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, Class<?> requestModel, Class<?> responseModel, String action, String apiVersion, String tableName, String sysId) throws Exception {
        Response response;
        if (ObjectHelper.equal(ServiceNowConstants.ACTION_RETRIEVE, action, true)) {
            response = retrieveRecord(exchange.getIn(), requestModel, responseModel, apiVersion, tableName, sysId);
        } else if (ObjectHelper.equal(ServiceNowConstants.ACTION_CREATE, action, true)) {
            response = createRecord(exchange.getIn(), requestModel, responseModel, apiVersion, tableName);
        } else {
            throw new IllegalArgumentException("Unknown action " + action);
        }

        setBodyAndHeaders(exchange.getIn(), responseModel, response);
    }

    /*
     * GET
     * https://instance.service-now.com/api/now/import/{tableName}/{sys_id}
     */
    private Response retrieveRecord(Message in, Class<?> requestModel, Class<?> responseModel, String apiVersion, String tableName, String sysId) throws Exception {
        return client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("import")
            .path(tableName)
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .query(responseModel)
            .invoke(HttpMethod.GET);
    }

    /*
     * POST
     * https://instance.service-now.com/api/now/import/{tableName}
     */
    private Response createRecord(Message in, Class<?> requestModel, Class<?> responseModel, String apiVersion, String tableName) throws Exception {
        if (in.getHeader(ServiceNowConstants.RETRIEVE_TARGET_RECORD, config::getRetrieveTargetRecordOnImport, Boolean.class)) {
            throw new UnsupportedOperationException("RetrieveTargetRecordOnImport is supported from Helsinky");
        }

        validateBody(in, requestModel);

        return client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("import")
            .path(tableName)
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());
    }
}
