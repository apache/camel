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

import com.braintreegateway.CreditCardVerification;
import com.braintreegateway.ResourceCollection;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.CreditCardVerificationGatewayApiMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class CreditCardVerificationGatewayIT extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CreditCardVerificationGatewayIT.class);
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(CreditCardVerificationGatewayApiMethod.class).getName();

    // TODO provide parameter values for find
    @Disabled
    @Test
    public void testFind() {
        // using String message body for single parameter "id"
        final CreditCardVerification result = requestBody("direct://FIND", null, CreditCardVerification.class);

        assertNotNull(result, "find result");
        LOG.debug("find: {}", result);
    }

    // TODO provide parameter values for search
    @Disabled
    @Test
    public void testSearch() {
        // using com.braintreegateway.CreditCardVerificationSearchRequest message body for single parameter "query"
        final ResourceCollection<CreditCardVerification> result
                = requestBody("direct://SEARCH", null, ResourceCollection.class);

        assertNotNull(result, "search result");
        LOG.debug("search: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for find
                from("direct://FIND")
                        .to("braintree://" + PATH_PREFIX + "/find?inBody=id");
                // test route for search
                from("direct://SEARCH")
                        .to("braintree://" + PATH_PREFIX + "/search?inBody=query");
            }
        };
    }
}
