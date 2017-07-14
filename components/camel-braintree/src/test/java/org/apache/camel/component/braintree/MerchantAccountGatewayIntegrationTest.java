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
import java.util.UUID;

import com.braintreegateway.MerchantAccount;
import com.braintreegateway.MerchantAccountRequest;
import com.braintreegateway.Result;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.BraintreeConstants;
import org.apache.camel.component.braintree.internal.MerchantAccountGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;

public class MerchantAccountGatewayIntegrationTest extends AbstractBraintreeTestSupport {
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(MerchantAccountGatewayApiMethod.class).getName();

    @Ignore
    @Test
    public void testCreate() throws Exception {
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
            Result.class
        );

        assertNotNull("create result", result);
        assertTrue(result.isSuccess());
    }

    @Ignore
    @Test
    public void testFind() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(BraintreeConstants.PROPERTY_PREFIX + "id", System.getenv("CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID"));
        final MerchantAccount result = requestBodyAndHeaders("direct://FIND", null, headers, MerchantAccount.class);

        assertNotNull("find result", result);
    }

    @Ignore
    @Test
    public void testUpdate() throws Exception {
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

        assertNotNull("update result", result);
        assertTrue(result.isSuccess());
    }

    // *************************************************************************
    // Routes
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
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
