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
import java.util.UUID;

import com.braintreegateway.MerchantAccount;
import com.braintreegateway.MerchantAccountRequest;
import com.braintreegateway.Result;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.MerchantAccountGatewayApiMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class MerchantAccountGatewayIT extends AbstractBraintreeTestSupport {
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(MerchantAccountGatewayApiMethod.class).getName();

    @Disabled
    @Test
    public void testCreate() {
        final String merchantId = UUID.randomUUID().toString();
        final Result<MerchantAccount> result = requestBody("direct://CREATE",
                new MerchantAccountRequest()
                        .masterMerchantAccountId(System.getenv("CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID"))
                        .individual()
                        .firstName("merchant")
                        .lastName(merchantId)
                        .address()
                        .streetAddress("my street")
                        .done()
                        .done(),
                Result.class);

        assertNotNull(result, "create result");
        assertTrue(result.isSuccess());
    }

    @Disabled
    @Test
    public void testFind() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "id", System.getenv("CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID"));
        final MerchantAccount result = requestBodyAndHeaders("direct://FIND", null, headers, MerchantAccount.class);

        assertNotNull(result, "find result");
    }

    @Disabled
    @Test
    public void testUpdate() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "id", System.getenv("CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID"));

        final Result<MerchantAccount> result = requestBodyAndHeaders("direct://UPDATE",
                new MerchantAccountRequest()
                        .individual()
                        .address()
                        .streetAddress("my new street address")
                        .done()
                        .done(),
                headers,
                Result.class);

        assertNotNull(result, "update result");
        assertTrue(result.isSuccess());
    }

    // *************************************************************************
    // Routes
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for create
                from("direct://CREATE")
                        .to("braintree://" + PATH_PREFIX + "/create?inBody=request");
                // test route for find
                from("direct://FIND")
                        .to("braintree://" + PATH_PREFIX + "/find");
                // test route for update
                from("direct://UPDATE")
                        .to("braintree://" + PATH_PREFIX + "/update?inBody=request");
            }
        };
    }
}
