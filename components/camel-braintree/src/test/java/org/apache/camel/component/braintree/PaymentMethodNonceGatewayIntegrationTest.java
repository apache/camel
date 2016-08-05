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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.PaymentMethodNonceGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentMethodNonceGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentMethodNonceGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(PaymentMethodNonceGatewayApiMethod.class).getName();

    // TODO provide parameter values for create
    @Ignore
    @Test
    public void testCreate() throws Exception {
        // using String message body for single parameter "paymentMethodToken"
        final com.braintreegateway.Result result = requestBody("direct://CREATE", null);

        assertNotNull("create result", result);
        LOG.debug("create: " + result);
    }

    // TODO provide parameter values for find
    @Ignore
    @Test
    public void testFind() throws Exception {
        // using String message body for single parameter "paymentMethodNonce"
        final com.braintreegateway.PaymentMethodNonce result = requestBody("direct://FIND", null);

        assertNotNull("find result", result);
        LOG.debug("find: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for create
                from("direct://CREATE")
                    .to("braintree://" + PATH_PREFIX + "/create?inBody=paymentMethodToken");
                // test route for find
                from("direct://FIND")
                    .to("braintree://" + PATH_PREFIX + "/find?inBody=paymentMethodNonce");
            }
        };
    }
}
