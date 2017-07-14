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
package org.apache.camel.component.braintree;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.SubscriptionGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(SubscriptionGatewayApiMethod.class).getName();

    // TODO provide parameter values for cancel
    @Ignore
    @Test
    public void testCancel() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://CANCEL", null);

        assertNotNull("cancel result", result);
        LOG.debug("cancel: " + result);
    }

    // TODO provide parameter values for create
    @Ignore
    @Test
    public void testCreate() throws Exception {
        // using com.braintreegateway.SubscriptionRequest message body for single parameter "request"
        final com.braintreegateway.Result result = requestBody("direct://CREATE", null);

        assertNotNull("create result", result);
        LOG.debug("create: " + result);
    }

    // TODO provide parameter values for delete
    @Ignore
    @Test
    public void testDelete() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBraintree.customerId", null);
        // parameter type is String
        headers.put("CamelBraintree.id", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://DELETE", null, headers);

        assertNotNull("delete result", result);
        LOG.debug("delete: " + result);
    }

    // TODO provide parameter values for find
    @Ignore
    @Test
    public void testFind() throws Exception {
        // using String message body for single parameter "id"
        final com.braintreegateway.Subscription result = requestBody("direct://FIND", null);

        assertNotNull("find result", result);
        LOG.debug("find: " + result);
    }

    // TODO provide parameter values for retryCharge
    @Ignore
    @Test
    public void testRetryCharge() throws Exception {
        // using String message body for single parameter "subscriptionId"
        final com.braintreegateway.Result result = requestBody("direct://RETRYCHARGE", null);

        assertNotNull("retryCharge result", result);
        LOG.debug("retryCharge: " + result);
    }

    // TODO provide parameter values for retryCharge
    @Ignore
    @Test
    public void testRetryChargeWithAmount() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBraintree.subscriptionId", null);
        // parameter type is java.math.BigDecimal
        headers.put("CamelBraintree.amount", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://RETRYCHARGE_1", null, headers);

        assertNotNull("retryCharge result", result);
        LOG.debug("retryCharge: " + result);
    }

    // TODO provide parameter values for search
    @Ignore
    @Test
    public void testSearch() throws Exception {
        // using com.braintreegateway.SubscriptionSearchRequest message body for single parameter "searchRequest"
        final com.braintreegateway.ResourceCollection result = requestBody("direct://SEARCH", null);

        assertNotNull("search result", result);
        LOG.debug("search: " + result);
    }

    // TODO provide parameter values for update
    @Ignore
    @Test
    public void testUpdate() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelBraintree.id", null);
        // parameter type is com.braintreegateway.SubscriptionRequest
        headers.put("CamelBraintree.request", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://UPDATE", null, headers);

        assertNotNull("update result", result);
        LOG.debug("update: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for cancel
                from("direct://CANCEL")
                    .to("braintree://" + PATH_PREFIX + "/cancel?inBody=id");
                // test route for create
                from("direct://CREATE")
                    .to("braintree://" + PATH_PREFIX + "/create?inBody=request");
                // test route for delete
                from("direct://DELETE")
                    .to("braintree://" + PATH_PREFIX + "/delete");
                // test route for find
                from("direct://FIND")
                    .to("braintree://" + PATH_PREFIX + "/find?inBody=id");
                // test route for retryCharge
                from("direct://RETRYCHARGE")
                    .to("braintree://" + PATH_PREFIX + "/retryCharge?inBody=subscriptionId");
                // test route for retryCharge
                from("direct://RETRYCHARGE_1")
                    .to("braintree://" + PATH_PREFIX + "/retryCharge");
                // test route for search
                from("direct://SEARCH")
                    .to("braintree://" + PATH_PREFIX + "/search?inBody=searchRequest");
                // test route for update
                from("direct://UPDATE")
                    .to("braintree://" + PATH_PREFIX + "/update");
            }
        };
    }
}
