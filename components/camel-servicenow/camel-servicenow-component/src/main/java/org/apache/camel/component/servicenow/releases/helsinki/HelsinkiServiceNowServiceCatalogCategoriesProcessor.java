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

class HelsinkiServiceNowServiceCatalogCategoriesProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowServiceCatalogCategoriesProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, this::retrieveCategory);
    }

    /*
     * This method retrieves all the information about a requested category.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/categories/{sys_id}
     */
    private void retrieveCategory(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("categories")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }
}
