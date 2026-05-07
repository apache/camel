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

import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.braintreegateway.TransactionLevelFeeReport;
import com.braintreegateway.TransactionLevelFeeReportRequest;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.braintree.internal.BraintreeApiCollection;
import org.apache.camel.component.braintree.internal.ReportGatewayApiMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link com.braintreegateway.ReportGateway} APIs.
 */
@EnabledIfSystemProperty(named = "braintreeAuthenticationType", matches = ".*")
public class ReportGatewayIT extends AbstractBraintreeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ReportGatewayIT.class);
    private static final String PATH_PREFIX
            = BraintreeApiCollection.getCollection().getApiName(ReportGatewayApiMethod.class).getName();

    @EnabledIfEnvironmentVariables({
            @EnabledIfEnvironmentVariable(named = "CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID", matches = ".*"),
            @EnabledIfEnvironmentVariable(named = "CAMEL_BRAINTREE_REPORT_DATE", matches = ".*")
    })
    @Test
    public void testTransactionLevelFees() throws Exception {
        String merchantAccountId = System.getenv("CAMEL_BRAINTREE_MERCHANT_ACCOUNT_ID");
        String reportDateString = System.getenv("CAMEL_BRAINTREE_REPORT_DATE");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar reportDate = Calendar.getInstance();
        reportDate.setTime(sdf.parse(reportDateString));

        TransactionLevelFeeReportRequest request = new TransactionLevelFeeReportRequest()
                .date(reportDate)
                .merchantAccountId(merchantAccountId);

        final com.braintreegateway.Result<TransactionLevelFeeReport> result = requestBody(
                "direct://TRANSACTIONLEVELFEES",
                request);

        LOG.info("Result message: {}", result.getMessage());
        assertNotNull(result, "transactionLevelFees result");
        assertTrue(result.isSuccess(), "transactionLevelFees success");
        TransactionLevelFeeReport report = result.getTarget();
        assertNotNull(report);

        LOG.debug("transactionLevelFees: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for transactionLevelFees
                from("direct://TRANSACTIONLEVELFEES")
                        .to("braintree://" + PATH_PREFIX + "/transactionLevelFees?inBody=request");

            }
        };
    }
}
