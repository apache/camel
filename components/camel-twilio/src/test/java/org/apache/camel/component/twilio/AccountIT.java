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
package org.apache.camel.component.twilio;

import java.util.HashMap;
import java.util.Map;

import com.twilio.base.ResourceSet;
import com.twilio.rest.api.v2010.Account;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twilio.internal.AccountApiMethod;
import org.apache.camel.component.twilio.internal.TwilioApiCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link com.twilio.rest.api.v2010.Account} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.twilio.AbstractTwilioTestSupport#hasCredentials",
           disabledReason = "Twilio credentials were not provided")
public class AccountIT extends AbstractTwilioTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AccountIT.class);
    private static final String PATH_PREFIX = TwilioApiCollection.getCollection().getApiName(AccountApiMethod.class).getName();

    @Test
    public void testFetcher() {
        final Account result = requestBody("direct://FETCHER", null);

        assertNotNull(result, "fetcher result not null");
        assertNotNull(result.getSid(), "fetcher result sid not null");
        LOG.debug("fetcher: {}", result);
    }

    @Test
    public void testFetcherWithPathSid() {
        final Account result = requestBodyAndHeaders("direct://FETCHER", null,
                headers("CamelTwilioPathSid", ((TwilioComponent) context().getComponent("twilio")).getAccountSid()));

        assertNotNull(result, "fetcher result not null");
        assertNotNull(result.getSid(), "fetcher result sid not null");
        LOG.debug("fetcher: {}", result);
    }

    @Test
    public void testReader() {
        final ResourceSet<Account> result = requestBody("direct://READER", null);

        assertNotNull(result, "reader result not null");
        result.forEach(account -> {
            assertNotNull(account, "reader result account not null");
            LOG.debug("reader: {}", account);
        });
    }

    @Test
    public void testReaderWithStatusActive() {
        final ResourceSet<Account> result = requestBodyAndHeaders("direct://READER", null,
                headers("CamelTwilioStatus", "active"));

        assertNotNull(result, "reader result not null");
        result.forEach(account -> {
            assertEquals(Account.Status.ACTIVE, account.getStatus(), "reader result account active");
            LOG.debug("reader: {}", account);
        });
    }

    private static Map<String, Object> headers(String name, Object value) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(name, value);
        return headers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for fetcher
                from("direct://FETCHER")
                        .to("twilio://" + PATH_PREFIX + "/fetch");

                // test route for reader
                from("direct://READER")
                        .to("twilio://" + PATH_PREFIX + "/read");

            }
        };
    }
}
