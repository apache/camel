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

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_CATEGORIES;

class HelsinkiServiceNowServiceCatalogProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowServiceCatalogProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_CATEGORIES, this::retrieveCatalogsCategories);
        addDispatcher(ACTION_RETRIEVE, this::retrieveCatalogs);
    }

    /*
     * This method retrieves a list of catalogs to which the user has access or
     * a single one if sys_id is defined.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/catalogs
     * - /sn_sc/servicecatalog/catalogs/{sys_id}
     */
    private void retrieveCatalogs(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = ObjectHelper.isEmpty(sysId)
            ? client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("sn_sc")
                .path(apiVersion)
                .path("servicecatalog")
                .path("catalogs")
                .query(ServiceNowParams.SYSPARM_LIMIT, in)
                .query(ServiceNowParams.SYSPARM_QUERY, in)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET)
            : client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("sn_sc")
                .path(apiVersion)
                .path("servicecatalog")
                .path("catalogs")
                .path(sysId)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves a list of categories for a catalog.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/catalogs/{sys_id}/categories
     */
    private void retrieveCatalogsCategories(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("catalogs")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("categories")
            .query(ServiceNowParams.SYSPARM_TOP_LEVEL_ONLY, in)
            .query(ServiceNowParams.SYSPARM_LIMIT, in)
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(ServiceNowParams.SYSPARM_OFFSET, in)
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }
}
