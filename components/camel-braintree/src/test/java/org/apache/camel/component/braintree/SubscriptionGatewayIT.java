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
package org.apache.camel.component.braintree;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.SubscriptionGatewayApiMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class SubscriptionGatewayIT extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionGatewayIT.class);
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(SubscriptionGatewayApiMethod.class).getName();

    // TODO provide parameter values for cancel
    @Disabled
    @Test
    public void testCancel() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Result result = requestBody("direct://CANCEL", null);

        assertNotNull(result, "cancel result");
        LOG.debug("cancel: {}", result);
    }

    // TODO provide parameter values for create
    @Disabled
    @Test
    public void testCreate() {
        // using com.braintreegateway.SubscriptionRequest message body for single parameter "request"
        final com.braintreegateway.Result result = requestBody("direct://CREATE", null);

        assertNotNull(result, "create result");
        LOG.debug("create: {}", result);
    }

    // TODO provide parameter values for delete
    @Disabled
    @Test
    public void testDelete() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBraintree.customerId", null);
        // parameter type is String
        headers.put("CamelBraintree.id", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://DELETE", null, headers);

        assertNotNull(result, "delete result");
        LOG.debug("delete: {}", result);
    }

    // TODO provide parameter values for find
    @Disabled
    @Test
    public void testFind() {
        // using String message body for single parameter "id"
        final com.braintreegateway.Subscription result = requestBody("direct://FIND", null);

        assertNotNull(result, "find result");
        LOG.debug("find: {}", result);
    }

    // TODO provide parameter values for retryCharge
    @Disabled
    @Test
    public void testRetryCharge() {
        // using String message body for single parameter "subscriptionId"
        final com.braintreegateway.Result result = requestBody("direct://RETRYCHARGE", null);

        assertNotNull(result, "retryCharge result");
        LOG.debug("retryCharge: {}", result);
    }

    // TODO provide parameter values for retryCharge
    @Disabled
    @Test
    public void testRetryChargeWithAmount() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBraintree.subscriptionId", null);
        // parameter type is java.math.BigDecimal
        headers.put("CamelBraintree.amount", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://RETRYCHARGE_1", null, headers);

        assertNotNull(result, "retryCharge result");
        LOG.debug("retryCharge: {}", result);
    }

    // TODO provide parameter values for search
    @Disabled
    @Test
    public void testSearch() {
        // using com.braintreegateway.SubscriptionSearchRequest message body for single parameter "searchRequest"
        final com.braintreegateway.ResourceCollection result = requestBody("direct://SEARCH", null);

        assertNotNull(result, "search result");
        LOG.debug("search: {}", result);
    }

    // TODO provide parameter values for update
    @Disabled
    @Test
    public void testUpdate() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBraintree.id", null);
        // parameter type is com.braintreegateway.SubscriptionRequest
        headers.put("CamelBraintree.request", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://UPDATE", null, headers);

        assertNotNull(result, "update result");
        LOG.debug("update: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
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
