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

import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_DELETE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_RETRIEVE;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_CHECKOUT;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_SUBJECT_DELIVERY_ADDRESS;
import static org.apache.camel.component.servicenow.ServiceNowConstants.ACTION_UPDATE;

class HelsinkiServiceNowServiceCatalogCartsProcessor extends AbstractServiceNowProcessor {

    HelsinkiServiceNowServiceCatalogCartsProcessor(ServiceNowEndpoint endpoint) throws Exception {
        super(endpoint);

        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_DELIVERY_ADDRESS, this::retrieveDeliveryAddress);
        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_CHECKOUT, this::retrieveCheckoutCart);
        addDispatcher(ACTION_RETRIEVE, ACTION_SUBJECT_CHECKOUT, this::retrieveCarts);
        addDispatcher(ACTION_UPDATE, ACTION_SUBJECT_CHECKOUT, this::checkoutCart);
        addDispatcher(ACTION_UPDATE, this::updateCart);
        addDispatcher(ACTION_DELETE, this::deleteCart);
    }

    /*
     * This method retrieves the default list of cart contents, cart details,
     * and price shown on the two-step checkout page.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart
     */
    private void retrieveCarts(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves the shipping address of the requested user.
     *
     * Method:
     * - GET
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart/delivery_address/{user_id}
     */
    private void retrieveDeliveryAddress(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .path("delivery_address")
            .path(getMandatoryRequestParamFromHeader(ServiceNowParams.PARAM_USER_ID, in))
            .query(responseModel)
            .invoke(HttpMethod.GET);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method edits and updates any item in the cart.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart/{cart_item_id}
     */
    private void updateCart(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .path(getMandatoryRequestParamFromHeader(ServiceNowParams.PARAM_CART_ITEM_ID, in))
            .query(responseModel)
            .invoke(HttpMethod.POST, in.getMandatoryBody());

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method deletes the cart and contents of the cart for a given user
     * role and sys_id.
     *
     * Method:
     * - DELETE
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart/{sys_id}/empty
     */
    private void deleteCart(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .path(getMandatoryRequestParamFromHeader(ServiceNowParams.PARAM_SYS_ID, in))
            .path("empty")
            .query(responseModel)
            .invoke(HttpMethod.DELETE);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method retrieves the checkout cart details based on the two-step
     * checkout process enabled or disabled. If the user enables two-step checkout,
     * the method returns cart order status and all the information required for
     * two-step checkout. If the user disables two-step checkout, the method
     * checks out the cart and returns the request number and request order ID.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart/checkout
     */
    private void retrieveCheckoutCart(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .path("checkout")
            .query(responseModel)
            .invoke(HttpMethod.POST);

        setBodyAndHeaders(in, responseModel, response);
    }

    /*
     * This method checks out the user cart, whether two-step parameter is
     * enabled or disabled.
     *
     * Method:
     * - POST
     *
     * URL Format:
     * - /sn_sc/servicecatalog/cart/submit_order
     */
    private void checkoutCart(Exchange exchange) throws Exception {
        final Message in = exchange.getIn();
        final Class<?> responseModel = getResponseModel(in);
        final String apiVersion = getApiVersion(in);

        Response response = client.reset()
            .types(MediaType.APPLICATION_JSON_TYPE)
            .path("sn_sc")
            .path(apiVersion)
            .path("servicecatalog")
            .path("cart")
            .path("submit_order")
            .query(responseModel)
            .invoke(HttpMethod.POST);

        setBodyAndHeaders(in, responseModel, response);
    }
}
