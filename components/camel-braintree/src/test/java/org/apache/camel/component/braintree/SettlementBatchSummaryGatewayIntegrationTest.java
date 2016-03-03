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
import org.apache.camel.component.braintree.internal.SettlementBatchSummaryGatewayApiMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SettlementBatchSummaryGatewayIntegrationTest extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SettlementBatchSummaryGatewayIntegrationTest.class);
    private static final String PATH_PREFIX = BraintreeApiCollection.getCollection().getApiName(SettlementBatchSummaryGatewayApiMethod.class).getName();

    // TODO provide parameter values for generate
    @Ignore
    @Test
    public void testGenerate() throws Exception {
        // using java.util.Calendar message body for single parameter "settlementDate"
        final com.braintreegateway.Result result = requestBody("direct://GENERATE", null);

        assertNotNull("generate result", result);
        LOG.debug("generate: " + result);
    }

    // TODO provide parameter values for generate
    @Ignore
    @Test
    public void testGenerateWithCustomFields() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is java.util.Calendar
        headers.put("CamelBraintree.settlementDate", null);
        // parameter type is String
        headers.put("CamelBraintree.groupByCustomField", null);

        final com.braintreegateway.Result result = requestBodyAndHeaders("direct://GENERATE_1", null, headers);

        assertNotNull("generate result", result);
        LOG.debug("generate: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for generate
                from("direct://GENERATE")
                    .to("braintree://" + PATH_PREFIX + "/generate?inBody=settlementDate");
                // test route for generate
                from("direct://GENERATE_1")
                    .to("braintree://" + PATH_PREFIX + "/generate");
            }
        };
    }
}
