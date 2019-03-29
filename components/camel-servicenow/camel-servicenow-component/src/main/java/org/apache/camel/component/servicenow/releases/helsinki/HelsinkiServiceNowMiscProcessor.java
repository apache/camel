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

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_CREATE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_IDENTIFY_RECONCILE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_USER_ROLE_INHERITANCE;

class HelsinkiServiceNowMiscProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowMiscProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_USER_ROLE_INHERITANCE, this::retrieveUserRoleInheritance);
        addDispatcher(ACTION_CREATE, ACTION_SUBJECT_IDENTIFY_RECONCILE, this::uploadIdentifyReconcile);
    }

    /*
     * This method retrieves the roles the user has an determine what was inherited.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /api/global/user_role_inheritance
     */
    private void retrieveUserRoleInheritance(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("global")
            .path("user_role_inheritance")
            .query(ServiceNowParams.PARAM_USER_SYS_ID, in)
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves the roles the user has an determine what was inherited.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /api/now/identifyreconcile
     */
    private void uploadIdentifyReconcile(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("now")
            .path(apiVersion)
            .path("identifyreconcile")
            .query(ServiceNowParams.SYSPARM_DATA_SOURCE, in)
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }
}
