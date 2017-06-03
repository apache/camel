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
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_CREATE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;

class HelsinkiServiceNowImportSetProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowImportSetProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, this::retrieveRecord);
        addDispatcher(ACTION_CREATE, this::createRecord);
    }

    /*
     * GET
     * https://instance.service-now.com/api/now/import/{tableName}/{sys_id}
     */
    private void retrieveRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> responseModel = getResponseModel(in, tableName);
        final String sysId = getSysID(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("import")
            .path(ObjectHelper.notNull(tableName, "tableName"))
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * POST
     * https://instance.service-now.com/api/now/import/{tableName}
     */
    private void createRecord(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final String tableName = getTableName(in);
        final String apiVersion = getApiVersion(in);
        final Class<?> requestModel = getRequestModel(in, tableName);
        final Class<?> responseModel = getResponseModel(in, tableName);

        validateBody(in, requestModel);
        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("import")
            .path(tableName)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }
}
