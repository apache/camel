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
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_CREATE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_CART;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_CHECKOUT_GUIDE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_PRODUCER;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_SUBMIT_GUIDE;

class HelsinkiServiceNowServiceCatalogItemsProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowServiceCatalogItemsProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_SUBMIT_GUIDE, this::submitItemGuide);
        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_CHECKOUT_GUIDE, this::checkoutItemGuide);
        addDispatcher(ACTION_RETRIEVE, this::retrieveItems);
        addDispatcher(ACTION_CREATE, ACTION_SUBJECT_CART, this::addItemToCart);
        addDispatcher(ACTION_CREATE, ACTION_SUBJECT_PRODUCER, this::submitItemProducer);
    }

    /*
     * This method retrieves a list of catalogs to which the user has access or
     * a single one if sys_id is defined.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/items
     * - /sn_sc/servicecatalog/items/{sys_id}
     */
    private void retrieveItems(Exchange exchange) throws Exception {
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
                .path("items")
                .query(ServiceNowParams.SYSPARM_CATEGORY, in)
                .query(ServiceNowParams.SYSPARM_TYPE, in)
                .query(ServiceNowParams.SYSPARM_LIMIT, in)
                .query(ServiceNowParams.SYSPARM_TEXT, in)
                .query(ServiceNowParams.SYSPARM_OFFSET, in)
                .query(ServiceNowParams.SYSPARM_CATALOG, in)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET)
            : client.reset()
                .types(MediaType.APPLICATION_JSON_TYPE)
                .path("sn_sc")
                .path(apiVersion)
                .path("items")
                .path("items")
                .path(sysId)
                .query(ServiceNowParams.SYSPARM_VIEW, in)
                .query(responseModel)
                .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves a list of items based on the needs described for an
     * order guide.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/items/{sys_id}/submit_guide
     */
    private void submitItemGuide(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response =  client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("items")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("submit_guide")
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves an array of contents requested for checkout.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/items/{sys_id}/checkout_guide
     */
    private void checkoutItemGuide(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("items")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("submit_guide")
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method adds an item to the cart of the current user.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/items/{sys_id}/add_to_cart
     */
    private void addItemToCart(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("items")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("add_to_cart")
            .query(responseModel)
            .invoke(HttpMethod.POST);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method creates a record and returns the Table API relative path and
     * redirect url to access the created record.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/items/{sys_id}/submit_producer
     */
    private void submitItemProducer(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String sysId = getSysID(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("items")
            .path(ObjectHelper.notNull(sysId, "sysId"))
            .path("submit_producer")
            .query(ServiceNowParams.SYSPARM_VIEW, in)
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }
}
