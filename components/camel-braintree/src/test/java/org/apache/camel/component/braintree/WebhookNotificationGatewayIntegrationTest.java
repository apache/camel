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

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.WebhookNotification;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.WebhookNotificationGatewayApiMethod;
import org.junit.Test;

public class WebhookNotificationGatewayIntegrationTest extends AbstractBraintreeTestSupport {
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(WebhookNotificationGatewayApiMethod.class).getName();

    @Test
    public void testParse() throws Exception {
        final BraintreeGateway gateway = getGateway();

        Map<String, String> notification = gateway.webhookTesting().sampleNotification(
            WebhookNotification.Kind.SUBSCRIPTION_WENT_PAST_DUE,
            "my_id"
        );

        final Map<String, Object> headers = new HashMap<>();
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "signature", notification.get("bt_signature"));
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "payload", notification.get("bt_payload"));

        final WebhookNotification result = requestBodyAndHeaders("direct://PARSE", null, headers);

        assertNotNull("parse result", result);
        assertEquals("my_id", result.getSubscription().getId());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for parse
                from("direct://PARSE")
                    .to("braintree://" + PATH_PREFIX + "/parse");
                // test route for verify
                from("direct://VERIFY")
                    .to("braintree://" + PATH_PREFIX + "/verify?inBody=challenge");
            }
        };
    }
}
